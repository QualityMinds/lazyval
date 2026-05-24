package scenarios.converters;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
class NonPublicConverter implements Converter<String, String> {

    public NonPublicConverter() {
    }

    @Override
    public String convert(String source) {
        return source;
    }
}
