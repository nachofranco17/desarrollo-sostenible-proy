package uy.edu.um.xperience.catalog;

import java.math.BigDecimal;

/**
 * Criterios de búsqueda del catálogo (RF3). Null = filtro inactivo.
 * La combinación es AND: un ítem debe cumplir todos los activos.
 */
public record CatalogCriteria(
        String nombre,
        String tecnologia,
        String nivel,
        Integer duracionMin,
        Integer duracionMax,
        BigDecimal costoMin,
        BigDecimal costoMax) {
    public static CatalogCriteria empty() {
        return new CatalogCriteria(null, null, null, null, null, null, null);
    }
}
