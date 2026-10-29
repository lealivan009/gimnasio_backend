package com.authbase.api;

import com.authbase.api.dto.CreateUserRequest;
import com.authbase.api.dto.MessageResponse;
import com.authbase.api.dto.UpdateUserRequest;
import com.authbase.api.dto.UserPageResponse;
import com.authbase.api.dto.UserResponse;
import com.authbase.domain.Role;
import com.authbase.service.UserService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
@Tag(name = "Users", description = "Gestión de usuarios (solo ADMIN)")
public class UserResource {

    private final UserService userService;
    private final JsonWebToken jwt;

    public UserResource(UserService userService, JsonWebToken jwt) {
        this.userService = userService;
        this.jwt = jwt;
    }

    @GET
    @Operation(summary = "Listar usuarios (búsqueda y paginación)")
    public UserPageResponse list(
            @QueryParam("q") String q,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("enabled") Boolean enabled,
            @QueryParam("role") Role role
    ) {
        return userService.list(q, page, size, enabled, role);
    }

    @GET
    @Path("/pendientes")
    @RolesAllowed({"ADMIN", "PROFESOR"})
    @Operation(summary = "Cuentas que todavía no fueron aprobadas")
    public List<UserResponse> pending() {
        return userService.pending();
    }

    @POST
    @Path("/{id}/aprobar")
    @RolesAllowed({"ADMIN", "PROFESOR"})
    @Operation(summary = "Aprobar una cuenta")
    public UserResponse approve(@PathParam("id") String id) {
        return userService.approve(id);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Detalle de usuario")
    public UserResponse get(@PathParam("id") String id) {
        return userService.get(id);
    }

    @POST
    @Operation(summary = "Crear usuario")
    public UserResponse create(@Valid CreateUserRequest request) {
        return userService.create(request);
    }

    @PATCH
    @Path("/{id}")
    @Operation(summary = "Actualizar usuario")
    public UserResponse update(@PathParam("id") String id, @Valid UpdateUserRequest request) {
        return userService.update(id, jwt.getSubject(), request);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Eliminar usuario")
    public MessageResponse delete(@PathParam("id") String id) {
        userService.delete(id, jwt.getSubject());
        return new MessageResponse("Usuario eliminado");
    }
}
