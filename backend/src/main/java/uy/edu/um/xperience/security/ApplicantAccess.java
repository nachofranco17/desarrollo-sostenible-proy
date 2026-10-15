package uy.edu.um.xperience.security;

import uy.edu.um.xperience.application.JobApplication;

/** Contexto de perfil/CV obtenido de una postulación; el evaluador comprueba la relación persistida. */
public record ApplicantAccess(JobApplication application) {
}
