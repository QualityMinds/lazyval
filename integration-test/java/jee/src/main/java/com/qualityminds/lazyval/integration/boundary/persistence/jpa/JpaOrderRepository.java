package com.qualityminds.lazyval.integration.boundary.persistence.jpa;

import com.qualityminds.lazyval.integration.domain.Order;
import com.qualityminds.lazyval.integration.domain.OrderRepository;
import com.qualityminds.lazyval.integration.shared.Isbn;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Named("jpa")
public class JpaOrderRepository implements OrderRepository {

    @PersistenceContext
    EntityManager em;
    @Inject
    JpaMapper mapper;


    @Override
    public void save(Order order) {
        em.persist(mapper.toDB(order));
    }

    @Override
    public List<Order> findAll() {
        List<JpaOrder> jpaOrders = em.createQuery("SELECT o FROM JpaOrder o", JpaOrder.class)
                .getResultStream().sorted(Comparator.comparing(jpaOrder -> jpaOrder.isbn.value())).toList();
        return mapper.toDomain(jpaOrders);
    }

    @Override
    public Order getById(UUID id) {
        JpaOrder jpaOrder = em.find(JpaOrder.class, id);
        return mapper.toDomain(jpaOrder);
    }

    @Override
    public List<Order> findByIsbn(Isbn isbn) {
        List<JpaOrder> jpaOrders = em.createQuery("SELECT o FROM JpaOrder o WHERE o.isbn = :isbn", JpaOrder.class)
                .setParameter("isbn", isbn)
                .getResultStream().sorted(Comparator.comparing(jpaOrder -> jpaOrder.isbn.value())).toList();
        return mapper.toDomain(jpaOrders);
    }
}
