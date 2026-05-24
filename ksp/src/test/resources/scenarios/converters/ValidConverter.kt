package scenarios.converters

import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter

@ReadingConverter
class ValidConverter : Converter<String, String> {
    override fun convert(source: String): String = source
}
