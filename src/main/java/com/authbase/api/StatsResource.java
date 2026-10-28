package com.authbase.api;

import com.authbase.api.dto.GymDtos.CalendarDayResponse;
import com.authbase.api.dto.GymDtos.ExerciseProgressPoint;
import com.authbase.api.dto.GymDtos.StatsSummaryResponse;
import com.authbase.api.dto.GymDtos.StudentFollowResponse;
import com.authbase.security.CurrentUser;
import com.authbase.service.StatsService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Estadísticas")
public class StatsResource {

    private final StatsService statsService;
    private final CurrentUser currentUser;

    public StatsResource(StatsService statsService, CurrentUser currentUser) {
        this.statsService = statsService;
        this.currentUser = currentUser;
    }

    @GET
    @Path("/estadisticas/resumen")
    @RolesAllowed({"USER", "ALUMNO", "PROFESOR", "ADMIN"})
    public StatsSummaryResponse summary(@QueryParam("studentId") String studentId) {
        return statsService.summary(currentUser.require(), studentId);
    }

    @GET
    @Path("/estadisticas/ejercicio/{exerciseId}")
    @RolesAllowed({"USER", "ALUMNO", "PROFESOR", "ADMIN"})
    public List<ExerciseProgressPoint> exercise(
            @PathParam("exerciseId") String exerciseId,
            @QueryParam("studentId") String studentId
    ) {
        return statsService.exerciseProgress(currentUser.require(), exerciseId, studentId);
    }

    @GET
    @Path("/estadisticas/calendario")
    @RolesAllowed({"USER", "ALUMNO", "PROFESOR", "ADMIN"})
    public List<CalendarDayResponse> calendar(
            @QueryParam("studentId") String studentId,
            @QueryParam("year") Integer year,
            @QueryParam("month") Integer month
    ) {
        return statsService.calendar(currentUser.require(), studentId, year, month);
    }

    @GET
    @Path("/alumnos")
    @RolesAllowed({"PROFESOR", "ADMIN"})
    public List<StudentFollowResponse> students() {
        currentUser.requireStaff();
        return statsService.followStudents();
    }
}
