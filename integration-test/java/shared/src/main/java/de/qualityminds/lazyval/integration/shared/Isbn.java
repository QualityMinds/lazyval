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
        for (int i = 0; i < 9; i++) {
            if (!Character.isDigit(isbn.charAt(i))) {
                throw new IllegalArgumentException("Invalid ISBN-10 format: contains non-digit characters");
            }
        }

        char lastChar = isbn.charAt(9);
        if (!Character.isDigit(lastChar) && lastChar != 'X' && lastChar != 'x') {
            throw new IllegalArgumentException("Invalid ISBN-10 format: last character must be digit or X");
        }

        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += Character.getNumericValue(isbn.charAt(i)) * (10 - i);
        }

        int checkDigit = (lastChar == 'X' || lastChar == 'x') ? 10 : Character.getNumericValue(lastChar);
        sum += checkDigit;

        if (sum % 11 != 0) {
            throw new IllegalArgumentException("Invalid ISBN-10: checksum validation failed");
        }
    }

    private static void validateIsbn13(String isbn) {
        for (char c : isbn.toCharArray()) {
            if (!Character.isDigit(c)) {
                throw new IllegalArgumentException("Invalid ISBN-13 format: contains non-digit characters");
            }
        }

        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = Character.getNumericValue(isbn.charAt(i));
            sum += (i % 2 == 0) ? digit : digit * 3;
        }

        int checkDigit = Character.getNumericValue(isbn.charAt(12));
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
