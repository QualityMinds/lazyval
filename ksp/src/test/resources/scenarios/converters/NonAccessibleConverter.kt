package scenarios.converters

import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter

/**
 * Top-level `private` (file-scoped) class. Cannot be referenced from any other file,
 * regardless of module or package. Used to verify that the visibility check rejects
 * unconditionally inaccessible classes.
 */
@ReadingConverter
private class NonAccessibleConverter : Converter<String, String> {
    override fun convert(source: String): String = source
}
