package internal.utils

import org.gradle.api.Task
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File
import java.io.Writer

internal val Provider<out FileSystemLocation>.absolutePath: String
        get() = get().asFile.absolutePath

internal fun Provider<out FileSystemLocation>.resolveToAbsolutePath(path: String): String =
    get().asFile.absoluteFile.resolve(path).absolutePath

internal fun Provider<out FileSystemLocation>.resolveToAbsolutePath(path: Provider<String>): String =
    get().asFile.absoluteFile.resolve(path.get()).absolutePath

internal inline fun <reified T : Any> ObjectFactory.nullableProperty(): Property<T?> =
    property(T::class.java)

internal inline fun <reified T : Any> ObjectFactory.notNullProperty(): Property<T> =
    property(T::class.java)

internal inline fun <reified T : Any> ObjectFactory.notNullProperty(defaultValue: T): Property<T> =
    property(T::class.java).value(defaultValue)

internal inline fun <reified T> Task.provider(noinline fn: () -> T): Provider<T> =
    project.provider(fn)

internal fun File.writeLines(lines: Collection<String>) {
    if (exists()) {
        delete()
    } else {
        parentFile?.mkdirs()
    }

    bufferedWriter().use { writer ->
        lines.forEach { writer.writeLine(it) }
    }
}

internal fun Writer.writeLine(line: String) {
    write(line)
    write("\n")
}
