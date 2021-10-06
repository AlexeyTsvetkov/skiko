package internal.utils

import org.gradle.workers.WorkParameters
import java.io.File

internal interface RunExternalProcessWorkParameters : WorkParameters {
    var workId: String
    var executable: String
    var args: List<String>
    var workingDir: File
}