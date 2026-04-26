package com.apac.atlas.lifecycle;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import java.net.URI;

@Path("/demo")
public class DemoController {

    @GET
    public Response demoHome() {
        return Response.temporaryRedirect(URI.create("/demo/index.html")).build();
    }
}
