package com.authbase.api;

import com.authbase.api.dto.GymDtos.BookClassRequest;
import com.authbase.api.dto.GymDtos.BookingResponse;
import com.authbase.api.dto.GymDtos.ClassScheduleResponse;
import com.authbase.api.dto.GymDtos.InstructorResponse;
import com.authbase.api.dto.GymDtos.UpdateBookingStatusRequest;
import com.authbase.api.dto.MessageResponse;
import com.authbase.api.dto.GymDtos.UpsertClassScheduleRequest;
import com.authbase.security.CurrentUser;
import com.authbase.service.ClassService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.List;

@Path("/api/clases")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ALUMNO", "PROFESOR", "ADMIN"})
@Tag(name = "Clases")
public class ClassResource {

    private final ClassService classService;
    private final CurrentUser currentUser;

    public ClassResource(ClassService classService, CurrentUser currentUser) {
        this.classService = classService;
        this.currentUser = currentUser;
    }

    @GET
    @Path("/instructores")
    @RolesAllowed({"PROFESOR", "ADMIN"})
    public List<InstructorResponse> instructors() {
        return classService.instructors();
    }

    @GET
    public List<ClassScheduleResponse> list(
            @QueryParam("date") LocalDate date,
            @QueryParam("active") Boolean active
    ) {
        return classService.list(date, active);
    }

    @POST
    @RolesAllowed({"PROFESOR", "ADMIN"})
    public ClassScheduleResponse create(@Valid UpsertClassScheduleRequest request) {
        return classService.create(request);
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({"PROFESOR", "ADMIN"})
    public ClassScheduleResponse update(@PathParam("id") String id, @Valid UpsertClassScheduleRequest request) {
        return classService.update(id, request);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({"PROFESOR", "ADMIN"})
    public MessageResponse deactivate(@PathParam("id") String id) {
        classService.deactivate(id);
        return new MessageResponse("Turno desactivado");
    }

    @GET
    @Path("/reservas")
    public List<BookingResponse> bookings(
            @QueryParam("date") LocalDate date,
            @QueryParam("studentId") String studentId
    ) {
        return classService.listBookings(currentUser.require(), date, studentId);
    }

    @POST
    @Path("/{id}/reservas")
    public BookingResponse book(@PathParam("id") String id, @Valid BookClassRequest request) {
        return classService.book(currentUser.require(), id, request);
    }

    @POST
    @Path("/reservas/{id}/cancelar")
    public BookingResponse cancel(@PathParam("id") String id) {
        return classService.cancel(currentUser.require(), id);
    }

    @PATCH
    @Path("/reservas/{id}")
    @RolesAllowed({"PROFESOR", "ADMIN"})
    public BookingResponse updateStatus(@PathParam("id") String id, @Valid UpdateBookingStatusRequest request) {
        return classService.updateStatus(id, request);
    }
}
