package com.authbase.api;

import com.authbase.api.dto.GymDtos.ExerciseResponse;
import com.authbase.api.dto.GymDtos.UpsertExerciseRequest;
import com.authbase.api.dto.MessageResponse;
import com.authbase.domain.MuscleGroup;
import com.authbase.service.ExerciseService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/ejercicios")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ALUMNO", "PROFESOR", "ADMIN"})
@Tag(name = "Ejercicios")
public class ExerciseResource {

    private final ExerciseService exerciseService;

    public ExerciseResource(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GET
    public List<ExerciseResponse> list(
            @QueryParam("q") String q,
            @QueryParam("muscleGroup") MuscleGroup muscleGroup
    ) {
        return exerciseService.list(q, muscleGroup);
    }

    @GET
    @Path("/{id}")
    public ExerciseResponse get(@PathParam("id") String id) {
        return exerciseService.get(id);
    }

    @POST
    @RolesAllowed({"PROFESOR", "ADMIN"})
    public ExerciseResponse create(@Valid UpsertExerciseRequest request) {
        return exerciseService.create(request);
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"PROFESOR", "ADMIN"})
    public ExerciseResponse update(@PathParam("id") String id, @Valid UpsertExerciseRequest request) {
        return exerciseService.update(id, request);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({"PROFESOR", "ADMIN"})
    public MessageResponse delete(@PathParam("id") String id) {
        exerciseService.delete(id);
        return new MessageResponse("Ejercicio eliminado");
    }
}
