package scenarios.java;

import com.qualityminds.lazyval.LazyValue;

@LazyValue
public final class SocialSecurityNumber {
    private final String value;

    private SocialSecurityNumber(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SocialSecurityNumber parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SSN cannot be blank");
        }
        return new SocialSecurityNumber(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SocialSecurityNumber ssn = (SocialSecurityNumber) obj;
        return value.equals(ssn.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}