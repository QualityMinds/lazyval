package scenarios.converters

import org.springframework.core.convert.converter.Converter

class NoNoArgConverter(private val prefix: String) : Converter<String, String> {
    override fun convert(source: String): String = prefix + source
}
