import internal.utils.writeLines
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

abstract class AbstractNativeToolTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Inject
    abstract val fileOperations: FileOperations

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    protected open fun configureOptions(mode: ToolMode): MutableList<String> =
        arrayListOf()

    @get:Input
    abstract val buildTargetOS: Property<OS>

    @get:Input
    abstract val buildTargetArch: Property<Arch>

    @get:Input
    abstract val buildVariant: Property<SkiaBuildType>

    @get:Internal
    protected abstract val outDirNameForTool: String

    @get:OutputDirectory
    val outDir: DirectoryProperty =
        project.objects.directoryProperty().apply {
            set(
                project.layout.buildDirectory.map {
                    val suffix = "${buildVariant.get().id}-${buildTargetOS.get().id}-${buildTargetArch.get().id}"
                    it.dir("out/$outDirNameForTool/$suffix")
                }
            )
        }

    @get:LocalState
    internal val taskStateDir: DirectoryProperty =
        project.objects.directoryProperty().apply {
            set(project.layout.buildDirectory.dir("tmp/$name"))
        }

    private val optionsTxtFile: File
        get() = taskStateDir.get().asFile.resolve("options.txt")

    @TaskAction
    fun run(inputChanges: InputChanges) {
        beforeRun()

        val mode = determineToolMode(inputChanges)
        when (mode) {
            is ToolMode.NonIncremental -> cleanStaleOutput(mode)
            is ToolMode.Incremental -> cleanStaleOutput(mode)
        }

        val options = configureOptions(mode)
        optionsTxtFile.writeLines(options)
        logger.warn("Written options to $optionsTxtFile")
        execute(mode, options)

        afterRun()
    }

    internal open fun determineToolMode(
        inputChanges: InputChanges
    ): ToolMode {
        return ToolMode.NonIncremental("$this is not incremental")
    }

    protected abstract fun execute(
        mode: ToolMode,
        options: List<String>
    )

    protected open fun cleanStaleOutput(mode: ToolMode.Incremental) {
        error("Incremental execution mode is not implemented by ${this.javaClass.canonicalName}")
    }

    protected open fun cleanStaleOutput(mode: ToolMode.NonIncremental) {
        cleanDirs(outDir, taskStateDir)
    }

    private fun cleanDirs(vararg dirs: Any) {
        for (dir in dirs) {
            fileOperations.delete(dir)
            fileOperations.mkdir(dir)
        }
    }

    protected open fun beforeRun() {}
    protected open fun afterRun() {}
}

