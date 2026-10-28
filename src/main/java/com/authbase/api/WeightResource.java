package com.authbase.api;

import com.authbase.api.dto.GymDtos.CreateWeightLogRequest;
import com.authbase.api.dto.GymDtos.WeightLogResponse;
import com.authbase.security.CurrentUser;
import com.authbase.service.WeightService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/peso")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ALUMNO", "PROFESOR", "ADMIN"})
@Tag(name = "Peso")
public class WeightResource {

    private final WeightService weightService;
    private final CurrentUser currentUser;

    public WeightResource(WeightService weightService, CurrentUser currentUser) {
        this.weightService = weightService;
        this.currentUser = currentUser;
    }

    @GET
    public List<WeightLogResponse> list(@QueryParam("studentId") String studentId) {
        return weightService.list(currentUser.require(), studentId);
    }

    @POST
    public WeightLogResponse create(@Valid CreateWeightLogRequest request) {
        return weightService.create(currentUser.require(), request);
    }
}
