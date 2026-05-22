package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;

public record Demo(Isbn isbn, Quantity quantity, EMail email) {
}
