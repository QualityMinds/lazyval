package scenarios.converters;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * Holds a private nested {@link Converter} that cannot be reached from any package.
 * Used to verify the visibility check rejects unconditionally inaccessible classes.
 */
public final class NonAccessibleConverter {

    private NonAccessibleConverter() {
    }

    @ReadingConverter
    private static final class Inner implements Converter<String, String> {

        public Inner() {
        }

        @Override
        public String convert(String source) {
            return source;
        }
    }
}
