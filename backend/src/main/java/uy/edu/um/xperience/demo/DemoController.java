package uy.edu.um.xperience.demo;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uy.edu.um.xperience.security.RequiereAccion;

/**
 * Probe for "Deny by default: ruta con permiso no asignado".
 * The action is in the documented catalog, but V4 loads no rows for it.
 * Distinct from a route missing {@code @RequiereAccion} (ruta nueva sin permiso).
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {
    @GetMapping("/permiso-asignable")
    @RequiereAccion("demo.permiso_asignable")
    public Map<String, String> permisoAsignable() {
        return Map.of(
            "message",
            "Acceso concedido: el rol tiene la acción demo.permiso_asignable en la tabla permiso.");
    }
}
