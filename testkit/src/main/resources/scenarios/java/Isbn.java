package scenarios.java;

import com.qualityminds.lazyval.LazyValue;

@LazyValue
public final class Isbn {
    private final String value;

    private Isbn(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Isbn parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ISBN cannot be blank");
        }
        return new Isbn(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Isbn isbn = (Isbn) obj;
        return value.equals(isbn.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}