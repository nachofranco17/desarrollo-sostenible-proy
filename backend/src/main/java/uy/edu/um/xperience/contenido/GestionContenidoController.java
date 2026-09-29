package uy.edu.um.xperience.contenido;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uy.edu.um.xperience.contenido.ContenidoDtos.ActualizarContenido;
import uy.edu.um.xperience.contenido.ContenidoDtos.ActualizarEntregable;
import uy.edu.um.xperience.contenido.ContenidoDtos.CrearContenido;
import uy.edu.um.xperience.contenido.ContenidoDtos.CrearEntregable;
import uy.edu.um.xperience.contenido.ContenidoDtos.DetalleContenido;
import uy.edu.um.xperience.contenido.ContenidoDtos.EscribirLeccion;
import uy.edu.um.xperience.contenido.ContenidoDtos.ResumenContenido;
import uy.edu.um.xperience.seguridad.Consumidor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Portal de gestión de cursos y proyectos de la empresa (RF5). Los servicios verifican que el rol
 * del consumidor incluya cada operación (R9).
 */
@RestController
@RequestMapping("/api/gestion/contenidos")
public class GestionContenidoController {

    private final GestionContenidoService gestion;
    private final MaterialService materiales;

    public GestionContenidoController(GestionContenidoService gestion, MaterialService materiales) {
        this.gestion = gestion;
        this.materiales = materiales;
    }

    @GetMapping
    public List<ResumenContenido> listar(Consumidor consumidor) {
        return gestion.listar(consumidor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DetalleContenido crear(Consumidor consumidor, @Valid @RequestBody CrearContenido datos) {
        return gestion.crear(consumidor, datos);
    }

    @GetMapping("/{id}")
    public DetalleContenido detalle(Consumidor consumidor, @PathVariable UUID id) {
        return gestion.detalle(consumidor, id);
    }

    @PutMapping("/{id}")
    public DetalleContenido actualizar(Consumidor consumidor, @PathVariable UUID id,
                                       @Valid @RequestBody ActualizarContenido datos) {
        return gestion.actualizar(consumidor, id, datos);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(Consumidor consumidor, @PathVariable UUID id) {
        gestion.eliminar(consumidor, id);
    }

    @PostMapping("/{id}/publicar")
    public DetalleContenido publicar(Consumidor consumidor, @PathVariable UUID id) {
        return gestion.publicar(consumidor, id);
    }

    @PostMapping("/{id}/bajar")
    public DetalleContenido bajar(Consumidor consumidor, @PathVariable UUID id) {
        return gestion.bajar(consumidor, id);
    }

    // ---- Lecciones ----

    @PostMapping("/{id}/lecciones")
    public DetalleContenido agregarLeccion(Consumidor consumidor, @PathVariable UUID id,
                                           @Valid @RequestBody EscribirLeccion datos) {
        return gestion.agregarLeccion(consumidor, id, datos);
    }

    @PutMapping("/{id}/lecciones/{leccionId}")
    public DetalleContenido actualizarLeccion(Consumidor consumidor, @PathVariable UUID id,
                                              @PathVariable UUID leccionId, @Valid @RequestBody EscribirLeccion datos) {
        return gestion.actualizarLeccion(consumidor, id, leccionId, datos);
    }

    @DeleteMapping("/{id}/lecciones/{leccionId}")
    public DetalleContenido eliminarLeccion(Consumidor consumidor, @PathVariable UUID id, @PathVariable UUID leccionId) {
        return gestion.eliminarLeccion(consumidor, id, leccionId);
    }

    // ---- Entregables ----

    @PostMapping("/{id}/entregables")
    public DetalleContenido agregarEntregable(Consumidor consumidor, @PathVariable UUID id,
                                              @Valid @RequestBody CrearEntregable datos) {
        return gestion.agregarEntregable(consumidor, id, datos);
    }

    @PutMapping("/{id}/entregables/{entregableId}")
    public DetalleContenido actualizarEntregable(Consumidor consumidor, @PathVariable UUID id,
                                                 @PathVariable UUID entregableId,
                                                 @Valid @RequestBody ActualizarEntregable datos) {
        return gestion.actualizarEntregable(consumidor, id, entregableId, datos);
    }

    @DeleteMapping("/{id}/entregables/{entregableId}")
    public DetalleContenido eliminarEntregable(Consumidor consumidor, @PathVariable UUID id,
                                               @PathVariable UUID entregableId) {
        return gestion.eliminarEntregable(consumidor, id, entregableId);
    }

    // ---- Material ----

    @PostMapping(path = "/{id}/materiales", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DetalleContenido subirMaterial(Consumidor consumidor, @PathVariable UUID id,
                                          @RequestPart("archivo") MultipartFile archivo) {
        return materiales.subir(consumidor, id, archivo);
    }

    @GetMapping("/{id}/materiales/{materialId}")
    public ResponseEntity<Resource> descargarMaterial(Consumidor consumidor, @PathVariable UUID id,
                                                      @PathVariable UUID materialId) {
        MaterialService.Descarga descarga = materiales.descargar(consumidor, id, materialId);
        Material material = descarga.material();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(material.getTipoMime()))
                .contentLength(material.getTamanioBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(material.getNombreOriginal(), StandardCharsets.UTF_8)
                        .build().toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(descarga.archivo());
    }

    @DeleteMapping("/{id}/materiales/{materialId}")
    public DetalleContenido eliminarMaterial(Consumidor consumidor, @PathVariable UUID id,
                                             @PathVariable UUID materialId) {
        return materiales.eliminar(consumidor, id, materialId);
    }
}
