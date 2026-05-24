package scenarios.converters

import org.springframework.core.convert.converter.Converter

class UnannotatedConverter : Converter<String, String> {
    override fun convert(source: String): String = source
}
