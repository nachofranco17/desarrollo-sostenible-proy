package uy.edu.um.xperience.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Respuestas públicas del catálogo (RF3). Solo contenido PUBLICADO; sin lecciones ni material. */
public final class CatalogView {
    private CatalogView() {}

    public record Item(UUID id, String tipo, String titulo, String tecnologia, String nivel,
                       int duracionHoras, BigDecimal costo, String empresaNombre) {}

    public record Detail(UUID id, String tipo, String titulo, String descripcion, String tecnologia,
                         String nivel, int duracionHoras, BigDecimal costo, String empresaNombre) {}

    /** Listado + tecnologías distintas de lo publicado (para el selector de filtro). */
    public record Page(List<Item> items, List<String> tecnologiasDisponibles) {}
}
