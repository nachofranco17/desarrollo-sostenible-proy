package uy.edu.um.xperience.enrollment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class EnrollmentView {
    private EnrollmentView() {}

    public record Item(
        UUID id,
        UUID cursoId,
        String tipo,
        String titulo,
        int porcentajeAvance,
        OffsetDateTime fecha
    ) {}

    public record Page(List<Item> items) {}
}
