import org.gradle.api.invocation.Gradle
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.*
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualStudioLocator
import org.gradle.nativeplatform.toolchain.internal.msvcpp.WindowsSdkLocator
import java.io.File

data class WindowsSdkPaths(
    val compiler: File,
    val linker: File,
    val includeDirs: Collection<File>,
    val libDirs: Collection<File>,
) {
    fun findWindowsSdkLibs(vararg libNames: String): List<File> {
        val libFiles = arrayListOf<File>()
        val unknownLibs = arrayListOf<String>()

        for (libName in libNames) {
            val libFile = libDirs.map { it.resolve(libName) }.firstOrNull { it.exists() }
            if (libFile != null) {
                libFiles.add(libFile)
            } else {
                unknownLibs.add(libName)
            }
        }

        if (unknownLibs.isNotEmpty()) {
            error(buildString {
                appendLine("Could not find requested Windows SDK libs: ${unknownLibs.joinToString(", ")}")
                appendLine("Searched in the following locations:")
                for (libDir in libDirs) {
                    appendLine("* '${libDir.absolutePath}'")
                }
            })
        }

        return libFiles
    }
}

fun findWindowsSdkPathsForCurrentOS(gradle: Gradle): WindowsSdkPaths {
    check(hostOs.isWindows) { "Unexpected host os: $hostOs, expected: ${OS.Windows}" }

    val vsbtPathEnvVar = "SKIKO_VSBT_PATH"
    val vsbtDir = System.getenv(vsbtPathEnvVar)?.let(::File) ?: error(
        "Environment variable '$vsbtPathEnvVar' is not set\n" +
                "Please set it to existing Visual Studio Build Tools installation"
    )
    check(vsbtDir.isDirectory) {
        "Environment variable '$vsbtPathEnvVar' points to non-existing directory: $vsbtDir\n" +
                "Please set it to existing Visual Studio Build Tools installation"
    }

    val vsLocator = gradle.serviceOf<VisualStudioLocator>()
    val vsComponent = vsLocator.locateComponent(vsbtDir)
    if (!vsComponent.isAvailable) error("Could not get VisualStudioInstall: $vsbtDir")

    val hostPlatform = host()
    val visualCpp = vsComponent.component.visualCpp.forPlatform(hostPlatform)
        ?: error("Visual Studio location component for host platform '$hostPlatform' is null")

    val compiler = visualCpp.compilerExecutable
    val linker = visualCpp.linkerExecutable
    val includeDirs = visualCpp.includeDirs.toMutableSet()
    val libDirs = visualCpp.libDirs.toMutableSet()

    val windowsSdkLocator = gradle.serviceOf<WindowsSdkLocator>()
    val windowsSdkComponents = windowsSdkLocator.locateAllComponents()
    val windowsSdkComponent = when (windowsSdkComponents.size) {
        0 -> error("Could not find Windows SDK location")
        1 -> windowsSdkComponents.single()
        else -> {
            val sdkVersionEnvVar = "SKIKO_WINDOWS_SDK_VERSION"
            val preferredVersion = System.getenv(sdkVersionEnvVar) ?:
            error(buildString {
                appendLine("Multiple Windows SDK versions are found:")
                windowsSdkComponents.forEach { appendLine("* '${it.version}'") }
                appendLine("Specify preferred version via '$sdkVersionEnvVar' environment variable")
            })
            windowsSdkComponents.find { it.toString() == preferredVersion } ?: error(
                buildString {
                    appendLine("Could not find preferred Windows SDK version '$preferredVersion' specified via ${sdkVersionEnvVar})")
                    appendLine("Available Windows SDK versions:")
                    windowsSdkComponents.forEach { appendLine("* '${it.version}'") }
                })
        }
    }
    val windowsSdk = windowsSdkComponent.forPlatform(hostPlatform)
        ?: error("Windows SDK component for host platform '$hostPlatform' is null")
    includeDirs.addAll(windowsSdk.includeDirs)
    val ucrtDir = windowsSdk.includeDirs
        .map { it.resolveSibling("ucrt") }.first { it.exists() }
    includeDirs.add(ucrtDir)
    libDirs.addAll(windowsSdk.libDirs)

    return WindowsSdkPaths(
        compiler = compiler,
        linker = linker,
        includeDirs = includeDirs,
        libDirs = libDirs
    )
}