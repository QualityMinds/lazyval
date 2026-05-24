package scenarios.converters;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

@WritingConverter
public class AnotherValidConverter implements Converter<Integer, String> {

    public AnotherValidConverter() {
    }

    @Override
    public String convert(Integer source) {
        return source.toString();
    }
}
