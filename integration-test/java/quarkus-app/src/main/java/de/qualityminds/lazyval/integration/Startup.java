package de.qualityminds.lazyval.integration;

import de.qualityminds.lazyval.integration.shared.Isbn;
import de.qualityminds.lazyval.integration.shared.Quantity;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class Startup {

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        Order orderA = new Order(Isbn.parse("3-86680-192-0"), new Quantity(1), new EMail("a@b.de"));
        Order orderB = new Order(Isbn.parse("978-3-86680-192-9"), new Quantity(1), new EMail("x@y.de"));

        orderA.persist();
        orderB.persist();
    }
}
