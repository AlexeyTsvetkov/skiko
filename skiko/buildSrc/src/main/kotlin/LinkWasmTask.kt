import internal.utils.*

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional

abstract class LinkWasmTask : LinkSharedLibraryTask() {
    @get:InputFile
    @get:Optional
    abstract val skikoJsPrefix: RegularFileProperty

    @get:Input
    abstract val jsOutputFileName: Property<String>

    override fun configureOptions(mode: ToolMode): MutableList<String> =
        super.configureOptions(mode).apply {
            add("-o")
            add(outDir.resolveToAbsolutePath(jsOutputFileName))

            add("--extern-post-js")
            add(skikoJsPrefix.get().asFile.absolutePath)
        }
}