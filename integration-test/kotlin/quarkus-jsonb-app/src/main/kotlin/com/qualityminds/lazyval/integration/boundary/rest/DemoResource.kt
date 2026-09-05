package com.qualityminds.lazyval.integration.boundary.rest

import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn
import com.qualityminds.lazyval.integration.shared.Quantity
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/demo")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class DemoResource {

    @GET
    fun get(): Demo = Demo(
        Isbn.parse("3-86680-192-0"),
        Quantity.of(2),
        EMail("a@b.de")
    )

    @POST
    fun echo(demo: Demo): Demo = demo
}
