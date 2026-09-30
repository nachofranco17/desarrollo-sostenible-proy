package com.xperience.backend.seguridad;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permiso")
public class Permiso {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	private Rol rol;

	private String accion;

	@Enumerated(EnumType.STRING)
	private Alcance alcance;

	protected Permiso() {
	}

	public UUID getId() {
		return id;
	}

	public Rol getRol() {
		return rol;
	}

	public String getAccion() {
		return accion;
	}

	public Alcance getAlcance() {
		return alcance;
	}

}
