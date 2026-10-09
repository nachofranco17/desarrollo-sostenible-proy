package uy.edu.um.xperience.enrollment;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Inscripción de un Talento a un curso o proyecto (RF4). */
public record Enrollment(
    UUID id,
    UUID talentoId,
    UUID cursoId,
    OffsetDateTime fecha,
    int porcentajeAvance
) {}
