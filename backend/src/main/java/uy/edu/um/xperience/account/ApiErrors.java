package uy.edu.um.xperience.account;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import uy.edu.um.xperience.security.Denegaciones;

@RestControllerAdvice
public class ApiErrors {
    private final Denegaciones denials;
    public ApiErrors(Denegaciones denials) { this.denials = denials; }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidInput(Exception error, HttpServletRequest request) {
        denials.registrar(request);
        return ResponseEntity.badRequest().body(Map.of("message", "Datos inválidos. Revisá los valores y enviá únicamente los campos permitidos."));
    }
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> badCredentials(BadCredentialsException error, HttpServletRequest request) {
        denials.registrar(request);
        return ResponseEntity.status(401).body(Map.of("message", "Credenciales incorrectas."));
    }
}