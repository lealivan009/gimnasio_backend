package com.authbase.api;

import com.authbase.api.dto.GymDtos.CreateWorkoutLogRequest;
import com.authbase.api.dto.GymDtos.WorkoutLogResponse;
import com.authbase.api.dto.MessageResponse;
import com.authbase.security.CurrentUser;
import com.authbase.service.WorkoutService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.List;

@Path("/api/registros-entrenamiento")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ALUMNO", "PROFESOR", "ADMIN"})
@Tag(name = "Registros de entrenamiento")
public class WorkoutResource {

    private final WorkoutService workoutService;
    private final CurrentUser currentUser;

    public WorkoutResource(WorkoutService workoutService, CurrentUser currentUser) {
        this.workoutService = workoutService;
        this.currentUser = currentUser;
    }

    @GET
    public List<WorkoutLogResponse> list(
            @QueryParam("studentId") String studentId,
            @QueryParam("exerciseId") String exerciseId,
            @QueryParam("from") LocalDate from,
            @QueryParam("to") LocalDate to
    ) {
        return workoutService.list(currentUser.require(), studentId, exerciseId, from, to);
    }

    @POST
    public WorkoutLogResponse create(@Valid CreateWorkoutLogRequest request) {
        return workoutService.create(currentUser.require(), request);
    }

    @DELETE
    @Path("/{id}")
    public MessageResponse delete(@PathParam("id") String id) {
        workoutService.delete(currentUser.require(), id);
        return new MessageResponse("Registro eliminado");
    }
}
