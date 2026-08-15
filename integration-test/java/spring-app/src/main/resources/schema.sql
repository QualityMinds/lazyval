-- Spring Data JDBC does no DDL generation (unlike Hibernate, which creates the JPA `orders` table
-- from JpaOrder), so the JDBC table is created by Boot's DataSourceScriptDatabaseInitializer —
-- see spring.sql.init.mode in application.properties.
--
-- Every column is a plain scalar: the domain-primitive properties on JdbcOrder are written and read
-- through the converters lazyval generates into jdbcCustomConversions(). Column names follow Spring
-- Data's default naming strategy, so orderDate/couponCode become order_date/coupon_code.
CREATE TABLE IF NOT EXISTS JDBC_ORDERS
(
    id          UUID PRIMARY KEY,
    isbn        VARCHAR(32)  NOT NULL,
    quantity    INTEGER      NOT NULL,
    email       VARCHAR(255) NOT NULL,
    order_date  DATE         NOT NULL,
    coupon_code VARCHAR(64)
);
