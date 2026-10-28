package com.authbase.api;

import com.authbase.api.dto.GymDtos.GymConfigResponse;
import com.authbase.api.dto.GymDtos.OccupancyResponse;
import com.authbase.api.dto.GymDtos.PublicGymResponse;
import com.authbase.api.dto.GymDtos.UpdateGymConfigRequest;
import com.authbase.service.GymConfigService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/gimnasio")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ALUMNO", "PROFESOR", "ADMIN"})
@Tag(name = "Gimnasio")
public class GymConfigResource {

    private final GymConfigService gymConfigService;

    public GymConfigResource(GymConfigService gymConfigService) {
        this.gymConfigService = gymConfigService;
    }

    @GET
    @Path("/publico")
    @PermitAll
    public PublicGymResponse publicInfo() {
        return gymConfigService.publicInfo();
    }

    @GET
    @Path("/config")
    public GymConfigResponse get() {
        return gymConfigService.get();
    }

    @PUT
    @Path("/config")
    @RolesAllowed({"PROFESOR", "ADMIN"})
    public GymConfigResponse update(@Valid UpdateGymConfigRequest request) {
        return gymConfigService.update(request);
    }

    @GET
    @Path("/ocupacion")
    public OccupancyResponse occupancy() {
        return gymConfigService.occupancy();
    }
}
