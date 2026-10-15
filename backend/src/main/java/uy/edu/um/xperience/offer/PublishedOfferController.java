package uy.edu.um.xperience.offer;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.xperience.security.RequiereAccion;

/** Consulta de ofertas publicadas para los roles de la matriz; no es el catálogo público RF3. */
@RestController
@RequestMapping("/api/offers")
public class PublishedOfferController {
    private final OfferService offers;

    public PublishedOfferController(OfferService offers) { this.offers = offers; }

    @RequiereAccion("oferta.ver")
    @GetMapping
    public List<OfferView.Summary> list(HttpServletRequest request) {
        OfferQuery.requireEmpty(request.getParameterMap());
        return offers.listPublished();
    }

    @RequiereAccion("oferta.ver")
    @GetMapping("/{id}")
    public OfferView.Detail detail(@PathVariable UUID id, HttpServletRequest request) {
        OfferQuery.requireEmpty(request.getParameterMap());
        return offers.publishedDetail(id);
    }
}
