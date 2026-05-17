package com.qualityminds.lazyval.integration.boundary.persistence.jpa;

import com.qualityminds.lazyval.integration.shared.Isbn;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PanacheRepository implements io.quarkus.hibernate.orm.panache.PanacheRepository<JpaOrder> {

    JpaOrder findById(UUID id){
        return find("id", id).firstResult();
    }

    List<JpaOrder> findByIsbn(Isbn isbn){
        return find("isbn", isbn).list();
    }

}
