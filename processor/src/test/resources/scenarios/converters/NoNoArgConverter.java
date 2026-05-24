package scenarios.converters;

import org.springframework.core.convert.converter.Converter;

public class NoNoArgConverter implements Converter<String, String> {

    private final String prefix;

    public NoNoArgConverter(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String convert(String source) {
        return prefix + source;
    }
}
