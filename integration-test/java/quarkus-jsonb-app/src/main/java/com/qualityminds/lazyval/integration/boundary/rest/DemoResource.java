package com.qualityminds.lazyval.integration.boundary.rest;

import com.qualityminds.lazyval.integration.shared.EMail;
import com.qualityminds.lazyval.integration.shared.Isbn;
import com.qualityminds.lazyval.integration.shared.Quantity;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/demo")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DemoResource {

    @GET
    public Demo get() {
        return new Demo(
                Isbn.parse("3-86680-192-0"),
                new Quantity(2),
                new EMail("a@b.de"));
    }

    @POST
    public Demo echo(Demo demo) {
        return demo;
    }
}
