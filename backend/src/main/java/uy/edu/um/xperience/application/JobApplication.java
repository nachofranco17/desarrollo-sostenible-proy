package uy.edu.um.xperience.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Instancia persistida; dueño, organización y CV siempre los resuelve el servidor. */
public record JobApplication(UUID id, UUID talentoId, UUID ofertaId, UUID empresaId,
                             UUID curriculumId, String estado, OffsetDateTime fecha) {}
