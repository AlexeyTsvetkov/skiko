package internal.utils

import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import javax.inject.Inject

internal abstract class RunExternalProcessWork: WorkAction<RunExternalProcessWorkParameters> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun execute() {
        val errFile = parameters.errLogFile
        check(!errFile.exists()) { "Error log file should not exist before execution: $errFile" }

        val execResult = errFile.outputStream().buffered().use { errStream ->
            execOperations.exec {
                executable = parameters.executable
                args = parameters.args
                workingDir = parameters.workingDir
                errorOutput = errStream
            }
        }

        if (execResult.exitValue == 0) {
            errFile.delete()
        }
    }
}