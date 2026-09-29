package uy.edu.um.xperience.contenido;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import uy.edu.um.xperience.comun.NoEncontradoException;
import uy.edu.um.xperience.comun.ReglaNegocioException;
import uy.edu.um.xperience.comun.SolicitudInvalidaException;
import uy.edu.um.xperience.contenido.ContenidoDtos.DetalleContenido;
import uy.edu.um.xperience.seguridad.Accion;
import uy.edu.um.xperience.seguridad.Consumidor;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Material de cursos y proyectos (RF5). Cada archivo pertenece a un contenido y se alcanza a través
 * de él. Subir, descargar y eliminar son acciones separadas en la matriz de roles (R9).
 */
@Service
public class MaterialService {

    static final int MAX_MATERIALES_POR_CONTENIDO = 20;
    private static final int MAX_NOMBRE = 255;

    private final GestionContenidoService gestion;
    private final MaterialRepository materiales;
    private final AlmacenArchivos almacen;

    public MaterialService(GestionContenidoService gestion, MaterialRepository materiales, AlmacenArchivos almacen) {
        this.gestion = gestion;
        this.materiales = materiales;
        this.almacen = almacen;
    }

    public record Descarga(Material material, Resource archivo) {
    }

    @Transactional
    public DetalleContenido subir(Consumidor consumidor, UUID contenidoId, MultipartFile archivo) {
        Contenido contenido = gestion.cargar(consumidor, contenidoId, Accion.MATERIAL_SUBIR);
        contenido.exigirModificable();
        if (archivo == null || archivo.isEmpty()) {
            throw new SolicitudInvalidaException("El archivo está vacío");
        }
        if (materiales.countByContenidoId(contenido.getId()) >= MAX_MATERIALES_POR_CONTENIDO) {
            throw new ReglaNegocioException("LIMITE_ALCANZADO", "Se alcanzó el máximo de archivos para este contenido");
        }

        String clave;
        TipoArchivo tipo;
        try (InputStream entrada = new BufferedInputStream(archivo.getInputStream())) {
            entrada.mark(TipoArchivo.BYTES_CABECERA);
            byte[] cabecera = entrada.readNBytes(TipoArchivo.BYTES_CABECERA);
            entrada.reset();
            tipo = TipoArchivo.detectar(cabecera).orElseThrow(() ->
                    new SolicitudInvalidaException("Tipo de archivo no admitido. Se aceptan PDF, PNG, JPG, ZIP y MP4"));
            clave = almacen.guardar(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", e);
        }

        // Si la transacción no confirma, el archivo ya escrito se borra.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int estado) {
                if (estado != STATUS_COMMITTED) {
                    almacen.eliminar(clave);
                }
            }
        });

        materiales.save(new Material(contenido.getId(), limpiarNombre(archivo.getOriginalFilename()), tipo.mime(),
                archivo.getSize(), clave, consumidor.usuarioId()));
        return gestion.detalleDe(contenido);
    }

    @Transactional(readOnly = true)
    public Descarga descargar(Consumidor consumidor, UUID contenidoId, UUID materialId) {
        Contenido contenido = gestion.cargar(consumidor, contenidoId, Accion.MATERIAL_DESCARGAR);
        Material material = buscar(contenido, materialId);
        return new Descarga(material, almacen.abrir(material.getClaveAlmacen()));
    }

    @Transactional
    public DetalleContenido eliminar(Consumidor consumidor, UUID contenidoId, UUID materialId) {
        Contenido contenido = gestion.cargar(consumidor, contenidoId, Accion.MATERIAL_ELIMINAR);
        contenido.exigirModificable();
        Material material = buscar(contenido, materialId);
        materiales.delete(material);
        String clave = material.getClaveAlmacen();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                almacen.eliminar(clave);
            }
        });
        return gestion.detalleDe(contenido);
    }

    private Material buscar(Contenido contenido, UUID materialId) {
        return materiales.findByIdAndContenidoId(materialId, contenido.getId())
                .orElseThrow(() -> new NoEncontradoException("Material"));
    }

    /** Sólo el último segmento, sin caracteres de control, acotado. Se usa para mostrar y descargar. */
    static String limpiarNombre(String original) {
        if (original == null) {
            return "archivo";
        }
        String nombre = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        nombre = nombre.replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (nombre.isEmpty() || nombre.equals(".") || nombre.equals("..")) {
            return "archivo";
        }
        return nombre.length() <= MAX_NOMBRE ? nombre : nombre.substring(nombre.length() - MAX_NOMBRE);
    }
}
