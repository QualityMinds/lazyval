package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.boundary.rest.model.PersistenceType;

/**
 * Thrown when a request names a {@link PersistenceType} that is valid per the API contract but
 * not wired in this deployment. Extends {@link UnsupportedOperationException} for readability,
 * but the 501 mapper binds to this subtype only — mapping the JDK type would silently turn
 * genuine bugs (mutating an immutable collection, say) into "not implemented" responses.
 */
public class UnsupportedPersistenceTypeException extends UnsupportedOperationException {

    private final PersistenceType persistenceType;

    public UnsupportedPersistenceTypeException(PersistenceType persistenceType) {
        super("Persistence type " + persistenceType + " is not implemented by this application");
        this.persistenceType = persistenceType;
    }

    public PersistenceType getPersistenceType() {
        return persistenceType;
    }
}
