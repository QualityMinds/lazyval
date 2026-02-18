package de.qualityminds.lazyval.integration.shared;

import java.util.Objects;
public final class Isbn {

    private final String value;

    private Isbn(String value){
        Objects.requireNonNull(value);
        this.value = value;
    }

    public String value(){
        return value;
    }

    // will be used by the annotation processor (factory methods have higher precedence)
    public static Isbn parse(String value) throws IllegalArgumentException {
        Objects.requireNonNull(value, "ISBN cannot be null");

        String cleanValue = value.replaceAll("[-\\s]", "");

        if (cleanValue.length() == 10) {
            validateIsbn10(cleanValue);
        } else if (cleanValue.length() == 13) {
            validateIsbn13(cleanValue);
        } else {
            throw new IllegalArgumentException("Invalid ISBN length. Must be 10 or 13 digits (excluding hyphens)");
        }

        return new Isbn(value);
    }

    private static void validateIsbn10(String isbn) {
        int sum = 0;

        for (int i = 0; i < 9; i++) {
            char c = isbn.charAt(i);
            int digit = Character.digit(c, 10);
            if (digit == -1) {
                throw new IllegalArgumentException("Invalid ISBN-10 format: contains non-digit characters");
            }
            sum += digit * (10 - i);
        }

        char lastChar = isbn.charAt(9);
        int checkDigit;
        if (lastChar == 'X' || lastChar == 'x') {
            checkDigit = 10;
        } else {
            checkDigit = Character.digit(lastChar, 10);
            if (checkDigit == -1) {
                throw new IllegalArgumentException("Invalid ISBN-10 format: last character must be digit or X");
            }
        }
        sum += checkDigit;

        if (sum % 11 != 0) {
            throw new IllegalArgumentException("Invalid ISBN-10: checksum validation failed");
        }
    }

    private static void validateIsbn13(String isbn) {
        int sum = 0;

        for (int i = 0; i < 12; i++) {
            char c = isbn.charAt(i);
            int digit = Character.digit(c, 10);
            if (digit == -1) {
                throw new IllegalArgumentException("Invalid ISBN-13 format: contains non-digit characters");
            }
            sum += (i % 2 == 0) ? digit : digit * 3;
        }

        int checkDigit = Character.digit(isbn.charAt(12), 10);
        if (checkDigit == -1) {
            throw new IllegalArgumentException("Invalid ISBN-13 format: contains non-digit characters");
        }

        int calculatedCheckDigit = (10 - (sum % 10)) % 10;

        if (checkDigit != calculatedCheckDigit) {
            throw new IllegalArgumentException("Invalid ISBN-13: checksum validation failed");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Isbn isbn = (Isbn) obj;
        return Objects.equals(value, isbn.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "ISBN{" + value + "}";
    }
}
