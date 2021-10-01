import internal.utils.*

import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

abstract class LinkSharedLibraryTask : AbstractNativeToolTask() {
    @get:InputFiles
    lateinit var libFiles: FileCollection

    @get:InputFiles
    lateinit var objectFiles: FileCollection

    @get:Input
    abstract val libOutputFileName: Property<String>

    @get:Input
    abstract val flags: ListProperty<String>

    @get:Input
    abstract val linker: Property<String>

    override val outDirNameForTool: String
        get() = "link"

    override fun execute(mode: ToolMode, options: List<String>) {
        check(mode is ToolMode.NonIncremental) {
            "Linking is not incremental, but $mode is received"
        }

        execOperations.exec {
            executable = linker.get()
            args = options
            workingDir = outDir.get().asFile
        }
    }

    override fun configureOptions(mode: ToolMode): MutableList<String> =
        super.configureOptions(mode).apply {
            add("-o")
            add(outDir.resolveToAbsolutePath(libOutputFileName))

            addAll(objectFiles.files.map { it.absolutePath })
            addAll(libFiles.files.map { it.absolutePath })
            addAll(flags.get())
        }
}