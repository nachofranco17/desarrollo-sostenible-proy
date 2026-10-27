package uy.edu.um.xperience.application;

import java.time.LocalDate;

public record ApplicationCriteria(String q, String especializacion, LocalDate fechaDesde, LocalDate fechaHasta) {}
