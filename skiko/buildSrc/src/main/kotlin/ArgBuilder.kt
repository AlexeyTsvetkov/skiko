import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import java.io.File

open class BaseArgBuilder {
    protected val args = arrayListOf<String>()

    fun arg(argName: String? = null, value: Any? = null) {
        addTransformedArgs(
            argName = transformName(argName),
            value = transformValue(value)
        )
    }

    protected open fun addTransformedArgs(argName: String?, value: String?) {
        argName?.let { args.add(it) }
        value?.let { args.add(it) }
    }

    protected open fun transformName(argName: String?): String? =
        argName

    private fun transformValue(value: Any?): String? =
        when (value) {
            is Provider<*> -> transformValue(value.get())
            is FileSystemLocation -> transformValue(value.asFile)
            is File -> escapePathIfNeeded(value)
            is Any -> value.toString()
            else -> null
        }

    protected open fun escapePathIfNeeded(file: File): String =
        file.absolutePath

    fun build(): Array<String> = args.toTypedArray()
}

abstract class BaseVisualStudioBuildToolsArgBuilder : BaseArgBuilder() {
    override fun escapePathIfNeeded(file: File): String =
        "\"${file.absolutePath.replace("/", "\\\\")}\""
}

class VisualCppCompilerArgBuilder : BaseVisualStudioBuildToolsArgBuilder() {
    override fun transformName(argName: String?): String? =
        when (argName) {
            "-o", "--output" -> "/Fo"
            "-c", "--compile" -> "/c"
            "-I", "--include-directory" -> "/I"
            else -> super.transformName(argName)
        }
}

class VisualCppLinkerArgBuilder : BaseVisualStudioBuildToolsArgBuilder() {
    private val outArg = "/OUT"
    private val libPathArg = "/LIBPATH"
    private val argsToJoinWithValues = listOf(outArg, libPathArg)

    override fun transformName(argName: String?): String? =
        when (argName) {
            "-o", "--output" -> outArg
            "-L", "--library-directory" -> libPathArg
            else -> super.transformName(argName)
        }

    override fun addTransformedArgs(argName: String?, value: String?) {
        if (argName in argsToJoinWithValues) {
            args.add("$argName:$value")
        } else {
            super.addTransformedArgs(argName, value)
        }
    }
}