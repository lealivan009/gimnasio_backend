package com.authbase.api;

import com.authbase.api.dto.GymDtos.ActiveRoutineResponse;
import com.authbase.api.dto.GymDtos.AssignRoutineRequest;
import com.authbase.api.dto.GymDtos.AssignmentResponse;
import com.authbase.api.dto.GymDtos.CloneRoutineRequest;
import com.authbase.api.dto.GymDtos.RoutineResponse;
import com.authbase.api.dto.GymDtos.UpdateAssignmentRequest;
import com.authbase.api.dto.GymDtos.UpsertRoutineRequest;
import com.authbase.api.dto.MessageResponse;
import com.authbase.security.CurrentUser;
import com.authbase.service.RoutineService;
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

@Path("/api/rutinas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ALUMNO", "PROFESOR", "ADMIN"})
@Tag(name = "Rutinas")
public class RoutineResource {

    private final RoutineService routineService;
    private final CurrentUser currentUser;

    public RoutineResource(RoutineService routineService, CurrentUser currentUser) {
        this.routineService = routineService;
        this.currentUser = currentUser;
    }

    @GET
    public List<RoutineResponse> list() {
        return routineService.list(currentUser.require());
    }

    @GET
    @Path("/activa")
    public ActiveRoutineResponse active() {
        return routineService.activeFor(currentUser.require());
    }

    @GET
    @Path("/asignaciones")
    public List<AssignmentResponse> assignments(
            @QueryParam("studentId") String studentId,
            @QueryParam("active") Boolean active
    ) {
        return routineService.listAssignments(currentUser.require(), studentId, active);
    }

    @GET
    @Path("/{id}")
    public RoutineResponse get(@PathParam("id") String id) {
        return routineService.get(currentUser.require(), id);
    }

    @POST
    public RoutineResponse create(@Valid UpsertRoutineRequest request) {
        return routineService.create(currentUser.require(), request);
    }

    @PUT
    @Path("/{id}")
    public RoutineResponse update(@PathParam("id") String id, @Valid UpsertRoutineRequest request) {
        return routineService.update(currentUser.require(), id, request);
    }

    @DELETE
    @Path("/{id}")
    public MessageResponse delete(@PathParam("id") String id) {
        routineService.delete(currentUser.require(), id);
        return new MessageResponse("Rutina eliminada");
    }

    @POST
    @Path("/{id}/clonar")
    public RoutineResponse clone(@PathParam("id") String id, CloneRoutineRequest request) {
        return routineService.clone(currentUser.require(), id, request);
    }

    @POST
    @Path("/{id}/asignar")
    public AssignmentResponse assign(@PathParam("id") String id, @Valid AssignRoutineRequest request) {
        return routineService.assign(currentUser.require(), id, request);
    }

    @PUT
    @Path("/asignaciones/{id}")
    public AssignmentResponse updateAssignment(@PathParam("id") String id, @Valid UpdateAssignmentRequest request) {
        return routineService.updateAssignment(currentUser.require(), id, request);
    }

    @DELETE
    @Path("/asignaciones/{id}")
    public MessageResponse deleteAssignment(@PathParam("id") String id) {
        routineService.deleteAssignment(currentUser.require(), id);
        return new MessageResponse("Asignación eliminada");
    }
}
