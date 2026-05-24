package scenarios.converters

import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter

@WritingConverter
class AnotherValidConverter : Converter<Int, String> {
    override fun convert(source: Int): String = source.toString()
}
