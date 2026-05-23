CREATE KEYSPACE IF NOT EXISTS quarkus
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

USE quarkus;

CREATE TABLE IF NOT EXISTS orders (
    id       UUID PRIMARY KEY,
    isbn     text,
    quantity int,
    email    text,
    orderdate date,
    couponcode text,
);
