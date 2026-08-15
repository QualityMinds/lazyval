package com.qualityminds.lazyval.integration.boundary.rest

import com.qualityminds.lazyval.integration.boundary.rest.model.PersistenceType

/**
 * Thrown when a request names a [PersistenceType] that is valid per the API contract but not wired
 * in this deployment. Extends [UnsupportedOperationException] for readability, but the 501 mapper
 * binds to this subtype only — mapping the stdlib type would silently turn genuine bugs into
 * "not implemented" responses.
 */
class UnsupportedPersistenceTypeException(val persistenceType: PersistenceType) :
    UnsupportedOperationException("Persistence type $persistenceType is not implemented by this application")
