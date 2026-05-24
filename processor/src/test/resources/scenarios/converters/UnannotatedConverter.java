package scenarios.converters;

import org.springframework.core.convert.converter.Converter;

public class UnannotatedConverter implements Converter<String, String> {

    public UnannotatedConverter() {
    }

    @Override
    public String convert(String source) {
        return source;
    }
}
