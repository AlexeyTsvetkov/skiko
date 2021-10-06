import internal.utils.*
import org.gradle.api.GradleException

import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutionException

import java.io.File
import java.util.*
import java.util.concurrent.Callable
import kotlin.collections.HashSet

abstract class CompileCppTask : AbstractNativeToolTask() {
    @get:Input
    abstract val flags: ListProperty<String>

    @get:Internal
    abstract val sourceRoots: ListProperty<Directory>

    @get:Input
    abstract val compiler: Property<String>

    override val outDirNameForTool: String
        get() = "compile"

    @get:InputFiles
    @get:Incremental
    val sourceFiles: FileCollection =
        project.files(Callable {
            fun File.isSourceFile(): Boolean =
                isFile && name.endsWith(".cc", ignoreCase = true)

            val sourceFiles = hashSetOf<File>()
            for (sourceRoot in sourceRoots.get()) {
                val canonicalSourceRoot = sourceRoot.asFile.canonicalFile
                canonicalSourceRoot.walk().filterTo(sourceFiles) { it.isSourceFile() }
            }
            sourceFiles
        })

    /**
     * Used only for up-to-date checks of headers' content
     *
     * @see [headersDirs]
     */
    @Suppress("UNUSED")
    @get:InputFiles
    @get:Incremental
    val headerFiles: FileCollection =
        project.files(Callable {
            fun File.isHeaderFile(): Boolean =
                isFile && name.endsWith(".h", ignoreCase = true)

            val headers = hashSetOf<File>()
            for (dir in headersDirs) {
                val canonicalDir = dir.canonicalFile
                dir.listFiles()?.forEach { file ->
                    if (file.isHeaderFile()) {
                        headers.add(canonicalDir.resolve(file.name))
                    }
                }
            }
            headers
        })

    @get:Internal
    internal val headersDirs = LinkedHashSet<File>()
    fun includeHeadersNonRecursive(dirs: Collection<File>) {
        headersDirs.addAll(dirs)
    }
    fun includeHeadersNonRecursive(dir: File) {
        headersDirs.add(dir)
    }

    private val sourceToOutputMapping = SourceToOutputMapping()
    private val sourceToOutputMappingFile: File
        get() = taskStateDir.get().asFile.resolve("source-to-output.txt")

    override fun beforeRun() {
        if (sourceToOutputMappingFile.exists()) {
            sourceToOutputMapping.load(sourceToOutputMappingFile)
        }
    }

    override fun afterRun() {
        sourceToOutputMapping.save(sourceToOutputMappingFile)
    }

    override fun execute(mode: ToolMode, options: List<String>) {
        val sourcesToCompile: Collection<File> = when (mode) {
            is ToolMode.Incremental -> mode.newOrModifiedFiles()
            is ToolMode.NonIncremental -> sourceFiles.files.also {
                logger.warn("Performing non-incremental compilation: ${mode.reason}")
            }
        }
        updateSourcesToOutputsMapping(sourcesToCompile)

        val outDir = outDir.get().asFile
        val compilerExecutablePath = findCompilerExecutable().absolutePath
        val workQueue = workerExecutor.noIsolation()
        val submittedWorks = HashSet<String>()

        for (source in sourcesToCompile) {
            val outputFile = sourceToOutputMapping[source]
                ?: error("Could not find output file for source file: $source")
            outputFile.parentFile.mkdirs()
            val workId = "Compiling '${source.absolutePath}'"
            submittedWorks.add(workId)

            workQueue.submit(RunExternalProcessWork::class.java) {
                this.workId = workId
                executable = compilerExecutablePath
                args = options + listOf(source.absolutePath, "-o", outputFile.absolutePath)
                workingDir = outDir
            }
        }

        try {
            workQueue.await()
        } catch (e: WorkerExecutionException) {
            for (work in submittedWorks) {
                val result = RunExternalProcessWork.workResults[work]
                if (result == null) {
                    logger.error("Error: no results for work '$work'")
                } else if (result.failure != null) {
                    logger.warn("\n$work:")
                    result.log.flushTo(logger)
                }
            }
            error("Some files were not compiled. Check the log for more details")
        } finally {
            for (work in submittedWorks) {
                RunExternalProcessWork.workResults.remove(work)
            }
        }
    }

    private fun findCompilerExecutable(): File {
        val compilerNameOrFile = compiler.get()
        val compilerFile = File(compilerNameOrFile)
        if (compilerFile.isFile) return compilerFile

        val paths = System.getenv("PATH").split(File.pathSeparator)
        for (path in paths) {
            val file = File(path).resolve(compilerNameOrFile)
            if (file.isFile) return file
        }

        error("Could not find compiler '$compilerNameOrFile' in PATH")
    }

    override fun cleanStaleOutput(mode: ToolMode.NonIncremental) {
        super.cleanStaleOutput(mode)
        // file is deleted by the base class
        check(!sourceToOutputMappingFile.exists())
        sourceToOutputMapping.clear()
    }

    override fun cleanStaleOutput(mode: ToolMode.Incremental) {
        for (sourceFile in mode.outdatedFiles()) {
            val outdatedOutputFile = sourceToOutputMapping.remove(sourceFile)
            check(outdatedOutputFile != null) {
                "Could not find output file for source file: $sourceFile"
            }
            check(outdatedOutputFile.exists()) {
                "Expected outdated output file does not exist: $outdatedOutputFile"
            }
            outdatedOutputFile.delete()
        }
    }

    private fun updateSourcesToOutputsMapping(sourcesToCompile: Collection<File>) {
        val mappingsForNewOrModifiedFiles = mapSourceFilesToOutputFiles(
            sourceRoots = sourceRoots.get().map { it.asFile },
            sourceFiles = sourcesToCompile,
            outDir = outDir.get().asFile,
            sourceFileExt = ".cc",
            outputFileExt = ".o"
        )
        sourceToOutputMapping.putAll(mappingsForNewOrModifiedFiles)
    }

    override fun determineToolMode(inputChanges: InputChanges): ToolMode {
        if (!sourceToOutputMappingFile.exists()) {
            return ToolMode.NonIncremental("first build or clean build")
        }

        if (!inputChanges.isIncremental) {
            return ToolMode.NonIncremental("inputs' changes are not incremental")
        }

        if (inputChanges.getFileChanges(headerFiles).any()) {
            return ToolMode.NonIncremental("header files are modified or removed")
        }

        val removedFiles = arrayListOf<File>()
        val newFiles = arrayListOf<File>()
        val modifiedFiles = arrayListOf<File>()

        val sourceFilesChanges = inputChanges.getFileChanges(sourceFiles)
        for (change in sourceFilesChanges) {
            when (change.changeType) {
                ChangeType.ADDED -> newFiles.add(change.file)
                ChangeType.MODIFIED -> modifiedFiles.add(change.file)
                ChangeType.REMOVED -> removedFiles.add(change.file)
            }
        }

        return ToolMode.Incremental(
            removedFiles = removedFiles,
            newFiles = newFiles,
            modifiedFiles = modifiedFiles
        )
    }

    override fun configureOptions(mode: ToolMode): MutableList<String> =
        super.configureOptions(mode).apply {
            add("-c")
            addAll(headersDirs.map { "-I${it.absolutePath}" })

            // todo: ensure that flags do not start with '-I' (all headers should be added via [headersDirs])
            addAll(flags.get())
        }
}

