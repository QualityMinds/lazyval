CREATE KEYSPACE IF NOT EXISTS spring
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

USE spring;

CREATE TABLE IF NOT EXISTS orders (
    id       UUID PRIMARY KEY,
    isbn     text,
    quantity int,
    email    text,
    orderdate date,
    couponcode text,
);
