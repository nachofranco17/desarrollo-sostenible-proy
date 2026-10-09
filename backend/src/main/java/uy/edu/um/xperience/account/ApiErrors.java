package uy.edu.um.xperience.account;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.xperience.security.Denegaciones;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    private final Denegaciones denials;
    public ApiErrors(Denegaciones denials) { this.denials = denials; }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidInput(Exception error, HttpServletRequest request) {
        markUnrecognizedField(error);
        denials.registrar(request);
        return ResponseEntity.badRequest().body(Map.of("message", "Datos inválidos. Revisá los valores y enviá únicamente los campos permitidos."));
    }

    private static void markUnrecognizedField(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnrecognizedPropertyException unrecognized) {
                Denegaciones.marcarCampoNoAutorizado(unrecognized.getPropertyName());
                return;
            }
        }
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException error, HttpServletRequest request) {
        if (error.getStatusCode().is4xxClientError()) denials.registrar(request);
        String message = error.getReason() == null ? "No se pudo completar la solicitud." : error.getReason();
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", message));
    }
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> badCredentials(BadCredentialsException error, HttpServletRequest request) {
        denials.registrar(request);
        return ResponseEntity.status(401).body(Map.of("message", "Credenciales incorrectas."));
    }
}