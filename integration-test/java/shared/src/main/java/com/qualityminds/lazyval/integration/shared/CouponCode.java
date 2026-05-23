package com.qualityminds.lazyval.integration.shared;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class CouponCode {

    private final String value;

    private CouponCode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Nullable
    public static CouponCode ofNullable(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        return new CouponCode(value.trim().toUpperCase());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CouponCode other = (CouponCode) obj;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "CouponCode{" + value + "}";
    }
}
