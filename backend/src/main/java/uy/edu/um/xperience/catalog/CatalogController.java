package uy.edu.um.xperience.catalog;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.xperience.security.RequiereAccion;

/**
 * Catálogo público de cursos y proyectos (RF3). Visitante y roles con catalogo.ver.
 * Solo contenido PUBLICADO. Los query params desconocidos se ignoran.
 */
@RestController
@RequestMapping("/api/catalog/courses")
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @RequiereAccion("catalogo.ver")
    @GetMapping
    public CatalogView.Page list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String tecnologia,
            @RequestParam(required = false) String nivel,
            @RequestParam(required = false) String duracionMin,
            @RequestParam(required = false) String duracionMax,
            @RequestParam(required = false) String costoMin,
            @RequestParam(required = false) String costoMax) {
        // q es alias de nombre (buscador).
        String search = nombre != null ? nombre : q;
        return catalog.list(CatalogQuery.parse(search, tecnologia, nivel, duracionMin, duracionMax, costoMin, costoMax));
    }

    @RequiereAccion("catalogo.ver")
    @GetMapping("/{id}")
    public CatalogView.Detail detail(@PathVariable UUID id) {
        return catalog.detail(id);
    }
}
