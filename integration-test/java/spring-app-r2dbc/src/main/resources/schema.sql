-- Spring Data R2DBC does no DDL generation, so the schema is applied by Boot's
-- ApplicationR2dbcScriptDatabaseInitializer (spring.sql.init.mode in application.properties).
--
-- Every column is a plain scalar: the domain-primitive properties on R2dbcOrder are written and read
-- through the converters lazyval generates into LazyvalSpringDataConfiguration. Column names follow
-- Spring Data's default naming strategy, so orderDate/couponCode become order_date/coupon_code.
CREATE TABLE IF NOT EXISTS orders
(
    id          UUID PRIMARY KEY,
    isbn        VARCHAR(32)  NOT NULL,
    quantity    INTEGER      NOT NULL,
    email       VARCHAR(255) NOT NULL,
    order_date  DATE         NOT NULL,
    coupon_code VARCHAR(64)
);
