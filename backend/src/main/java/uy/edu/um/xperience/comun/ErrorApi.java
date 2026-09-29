package uy.edu.um.xperience.comun;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorApi(String codigo, String mensaje, List<ErrorCampo> campos) {

    public ErrorApi(String codigo) {
        this(codigo, null, List.of());
    }

    public ErrorApi(String codigo, String mensaje) {
        this(codigo, mensaje, List.of());
    }

    public record ErrorCampo(String campo, String mensaje) {
    }
}
