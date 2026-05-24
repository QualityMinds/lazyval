package scenarios.converters;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class ValidConverter implements Converter<String, String> {

    public ValidConverter() {
    }

    @Override
    public String convert(String source) {
        return source;
    }
}
