package uy.edu.um.xperience.account;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidInput(Exception error) {
        return ResponseEntity.badRequest().body(Map.of("message",
            "Datos inválidos. Revisá el correo, los nombres y la contraseña (12 a 128 caracteres). Enviá únicamente los campos del formulario."));
    }
}
