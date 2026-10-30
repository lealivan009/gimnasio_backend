package com.authbase.api;

import com.authbase.api.dto.GymDtos.AttendanceResponse;
import com.authbase.security.CurrentUser;
import com.authbase.service.AttendanceService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.List;

@Path("/api/asistencia")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USER", "ALUMNO", "PROFESOR", "ADMIN"})
@Tag(name = "Asistencia")
public class AttendanceResource {

    private final AttendanceService attendanceService;
    private final CurrentUser currentUser;

    public AttendanceResource(AttendanceService attendanceService, CurrentUser currentUser) {
        this.attendanceService = attendanceService;
        this.currentUser = currentUser;
    }

    @GET
    public List<AttendanceResponse> list(
            @QueryParam("studentId") String studentId,
            @QueryParam("from") LocalDate from,
            @QueryParam("to") LocalDate to
    ) {
        return attendanceService.list(currentUser.require(), studentId, from, to);
    }

    @GET
    @Path("/hoy")
    public AttendanceResponse today() {
        return attendanceService.today(currentUser.require());
    }

    @GET
    @Path("/adentro")
    @RolesAllowed({"PROFESOR", "ADMIN"})
    public List<AttendanceResponse> insideNow() {
        return attendanceService.insideNow(currentUser.require());
    }

    @POST
    @Path("/ingreso")
    public AttendanceResponse checkIn(
            @QueryParam("studentId") String studentId,
            @QueryParam("fecha") LocalDate fecha
    ) {
        return attendanceService.checkIn(currentUser.require(), studentId, fecha);
    }

    @POST
    @Path("/egreso")
    public AttendanceResponse checkOut(@QueryParam("studentId") String studentId) {
        return attendanceService.checkOut(currentUser.require(), studentId);
    }
}
