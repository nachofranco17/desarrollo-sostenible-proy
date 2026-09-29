package uy.edu.um.xperience.comun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import uy.edu.um.xperience.seguridad.AccesoDenegadoException;
import uy.edu.um.xperience.seguridad.NoAutenticadoException;

import java.util.List;

/** Traducción de errores a respuestas JSON, sin trazas ni mensajes internos. */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(AccesoDenegadoException.class)
    ResponseEntity<ErrorApi> denegado() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorApi("PROHIBIDO", "Tu rol no permite realizar esta acción"));
    }

    @ExceptionHandler(NoAutenticadoException.class)
    ResponseEntity<ErrorApi> noAutenticado() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorApi("NO_AUTENTICADO"));
    }

    @ExceptionHandler({NoEncontradoException.class, NoResourceFoundException.class})
    ResponseEntity<ErrorApi> noEncontrado() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorApi("NO_ENCONTRADO"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorApi> invalido(MethodArgumentNotValidException e) {
        List<ErrorApi.ErrorCampo> campos = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorApi.ErrorCampo(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ErrorApi("VALIDACION", "Hay campos con errores", campos));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestPartException.class,
            SolicitudInvalidaException.class})
    ResponseEntity<ErrorApi> solicitudInvalida(Exception e) {
        String mensaje = e instanceof SolicitudInvalidaException s ? s.getMessage() : "La solicitud no es válida";
        return ResponseEntity.badRequest().body(new ErrorApi("SOLICITUD_INVALIDA", mensaje));
    }

    @ExceptionHandler(ReglaNegocioException.class)
    ResponseEntity<ErrorApi> regla(ReglaNegocioException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorApi(e.getCodigo(), e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorApi> demasiadoGrande() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorApi("ARCHIVO_DEMASIADO_GRANDE", "El archivo supera el tamaño máximo permitido"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorApi> metodo() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new ErrorApi("METODO_NO_PERMITIDO"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorApi> inesperado(Exception e) {
        LOG.error("Error no controlado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorApi("ERROR_INTERNO"));
    }
}
