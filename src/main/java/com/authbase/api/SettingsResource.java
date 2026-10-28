package com.authbase.api;

import com.authbase.api.dto.PublicSettingsResponse;
import com.authbase.api.dto.SettingsResponse;
import com.authbase.api.dto.UpdateSettingsRequest;
import com.authbase.service.SettingsService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/settings")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Settings", description = "Configuración general de la aplicación")
public class SettingsResource {

    private final SettingsService settingsService;

    public SettingsResource(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GET
    @Path("/public")
    @PermitAll
    @Operation(summary = "Configuración pública (registro, etc.)")
    public PublicSettingsResponse publicSettings() {
        return PublicSettingsResponse.from(settingsService.current());
    }

    @GET
    @RolesAllowed("ADMIN")
    @Operation(summary = "Obtener configuración general")
    public SettingsResponse get() {
        return settingsService.get();
    }

    @PATCH
    @RolesAllowed("ADMIN")
    @Operation(summary = "Actualizar configuración general")
    public SettingsResponse update(@Valid UpdateSettingsRequest request) {
        return settingsService.update(request);
    }
}
