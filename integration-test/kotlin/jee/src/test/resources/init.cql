CREATE KEYSPACE IF NOT EXISTS jee
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

USE jee;

CREATE TABLE IF NOT EXISTS orders (
    id       UUID PRIMARY KEY,
    isbn     text,
    quantity int,
    email    text,
    orderdate date
);
