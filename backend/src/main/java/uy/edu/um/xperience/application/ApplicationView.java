package uy.edu.um.xperience.application;

import java.time.OffsetDateTime;
import java.util.UUID;
import uy.edu.um.xperience.profile.ApplicantProfileView;

public final class ApplicationView {
    private ApplicationView() {}
    public record Own(UUID id, UUID ofertaId, String ofertaTitulo, String empresaNombre,
                      String estado, OffsetDateTime fecha) {}
    public record Applicant(UUID id, String estado, OffsetDateTime fecha, ApplicantProfileView perfil) {}
}
