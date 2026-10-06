package uy.edu.um.xperience.offer;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.xperience.security.RequiereAccion;

@RestController
@RequestMapping("/api/company/offers")
public class CompanyOfferController {
    private final OfferService offers;

    public CompanyOfferController(OfferService offers) { this.offers = offers; }

    @RequiereAccion("oferta.gestionar")
    @GetMapping
    public List<OfferView.Summary> list(HttpServletRequest request) {
        OfferQuery.requireEmpty(request.getParameterMap());
        return offers.listCompany();
    }

    @RequiereAccion("oferta.gestionar")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public OfferView.Detail create(@Valid @RequestBody OfferInput.Data input, HttpServletRequest request) {
        OfferQuery.requireEmpty(request.getParameterMap());
        return offers.create(input);
    }

    @RequiereAccion("oferta.gestionar")
    @GetMapping("/{id}")
    public OfferView.Detail detail(@PathVariable UUID id, HttpServletRequest request) {
        OfferQuery.requireEmpty(request.getParameterMap());
        return offers.companyDetail(id);
    }

    @RequiereAccion("oferta.gestionar")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OfferView.Detail update(@PathVariable UUID id, @Valid @RequestBody OfferInput.Data input,
                                   HttpServletRequest request) {
        OfferQuery.requireEmpty(request.getParameterMap());
        return offers.update(id, input);
    }

    @RequiereAccion("oferta.gestionar")
    @PostMapping("/{id}/publish")
    public OfferView.Detail publish(@PathVariable UUID id,
                                    @RequestBody(required = false) OfferInput.Publish input,
                                    HttpServletRequest request) {
        OfferQuery.requireEmpty(request.getParameterMap());
        return offers.publish(id);
    }
}
