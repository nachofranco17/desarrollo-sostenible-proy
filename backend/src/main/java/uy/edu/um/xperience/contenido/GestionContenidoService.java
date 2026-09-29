package uy.edu.um.xperience.contenido;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uy.edu.um.xperience.comun.NoEncontradoException;
import uy.edu.um.xperience.contenido.ContenidoDtos.ActualizarContenido;
import uy.edu.um.xperience.contenido.ContenidoDtos.ActualizarEntregable;
import uy.edu.um.xperience.contenido.ContenidoDtos.CrearContenido;
import uy.edu.um.xperience.contenido.ContenidoDtos.CrearEntregable;
import uy.edu.um.xperience.contenido.ContenidoDtos.DetalleContenido;
import uy.edu.um.xperience.contenido.ContenidoDtos.EscribirLeccion;
import uy.edu.um.xperience.contenido.ContenidoDtos.ResumenContenido;
import uy.edu.um.xperience.seguridad.Accion;
import uy.edu.um.xperience.seguridad.Consumidor;
import uy.edu.um.xperience.seguridad.PermisosPorRol;

import java.util.List;
import java.util.UUID;

/**
 * Alta, baja y modificación de cursos y proyectos por parte de la empresa (RF5). Cada operación
 * verifica primero que el rol del consumidor la incluya (R9).
 */
@Service
public class GestionContenidoService {

    private final ContenidoRepository contenidos;
    private final MaterialRepository materiales;
    private final HasherRespuestas hasher;
    private final AlmacenArchivos almacen;

    public GestionContenidoService(ContenidoRepository contenidos, MaterialRepository materiales,
                                   HasherRespuestas hasher, AlmacenArchivos almacen) {
        this.contenidos = contenidos;
        this.materiales = materiales;
        this.hasher = hasher;
        this.almacen = almacen;
    }

    @Transactional(readOnly = true)
    public List<ResumenContenido> listar(Consumidor consumidor) {
        PermisosPorRol.exigir(consumidor, Accion.CONTENIDO_GESTION_VER);
        return contenidos.findByEmpresaIdOrderByActualizadoEnDesc(consumidor.empresaId()).stream()
                .map(ResumenContenido::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public DetalleContenido detalle(Consumidor consumidor, UUID id) {
        return detalleDe(cargar(consumidor, id, Accion.CONTENIDO_GESTION_VER));
    }

    @Transactional
    public DetalleContenido crear(Consumidor consumidor, CrearContenido datos) {
        PermisosPorRol.exigir(consumidor, Accion.CONTENIDO_CREAR);
        Contenido contenido = new Contenido(consumidor.empresaId(), consumidor.usuarioId(), datos.tipo(), datos.datos());
        return detalleDe(contenidos.save(contenido));
    }

    @Transactional
    public DetalleContenido actualizar(Consumidor consumidor, UUID id, ActualizarContenido datos) {
        Contenido contenido = cargar(consumidor, id, Accion.CONTENIDO_EDITAR);
        contenido.actualizar(datos.datos());
        return detalleDe(contenido);
    }

    @Transactional
    public DetalleContenido publicar(Consumidor consumidor, UUID id) {
        Contenido contenido = cargar(consumidor, id, Accion.CONTENIDO_PUBLICAR);
        contenido.publicar();
        return detalleDe(contenido);
    }

    @Transactional
    public DetalleContenido bajar(Consumidor consumidor, UUID id) {
        Contenido contenido = cargar(consumidor, id, Accion.CONTENIDO_BAJAR);
        contenido.bajar();
        return detalleDe(contenido);
    }

    @Transactional
    public void eliminar(Consumidor consumidor, UUID id) {
        Contenido contenido = cargar(consumidor, id, Accion.CONTENIDO_ELIMINAR);
        contenido.exigirEliminable();
        List<Material> archivos = materiales.findByContenidoIdOrderBySubidoEnAsc(contenido.getId());
        materiales.deleteAll(archivos);
        contenidos.delete(contenido);
        // Los archivos se borran recién cuando la transacción confirma: si falla, siguen ahí.
        List<String> claves = archivos.stream().map(Material::getClaveAlmacen).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                claves.forEach(almacen::eliminar);
            }
        });
    }

    // ---- Lecciones ----

    @Transactional
    public DetalleContenido agregarLeccion(Consumidor consumidor, UUID id, EscribirLeccion datos) {
        Contenido contenido = cargar(consumidor, id, Accion.CONTENIDO_EDITAR);
        contenido.agregarLeccion(datos.titulo(), datos.cuerpo());
        return detalleDe(contenido);
    }

    @Transactional
    public DetalleContenido actualizarLeccion(Consumidor consumidor, UUID id, UUID leccionId, EscribirLeccion datos) {
        Contenido contenido = cargar(consumidor, id, Accion.CONTENIDO_EDITAR);
        contenido.actualizarLeccion(leccion(contenido, leccionId), datos.titulo(), datos.cuerpo());
        return detalleDe(contenido);
    }

    @Transactional
    public DetalleContenido eliminarLeccion(Consumidor consumidor, UUID id, UUID leccionId) {
        Contenido contenido = cargar(consumidor, id, Accion.CONTENIDO_EDITAR);
        contenido.eliminarLeccion(leccion(contenido, leccionId));
        return detalleDe(contenido);
    }

    // ---- Entregables ----

    @Transactional
    public DetalleContenido agregarEntregable(Consumidor consumidor, UUID id, CrearEntregable datos) {
        Contenido contenido = cargar(consumidor, id, Accion.CONTENIDO_EDITAR);
        contenido.agregarEntregable(datos.titulo(), datos.consigna(), datos.pista(), hashes(datos.respuestasAceptadas()));
        return detalleDe(contenido);
    }

    @Transactional
    public DetalleContenido actualizarEntregable(Consumidor consumidor, UUID id, UUID entregableId,
                                                 ActualizarEntregable datos) {
        Contenido contenido = cargar(consumidor, id, Accion.CONTENIDO_EDITAR);
        contenido.actualizarEntregable(entregable(contenido, entregableId),
                datos.titulo(), datos.consigna(), datos.pista(),
                datos.respuestasAceptadas() == null ? null : hashes(datos.respuestasAceptadas()));
        return detalleDe(contenido);
    }

    @Transactional
    public DetalleContenido eliminarEntregable(Consumidor consumidor, UUID id, UUID entregableId) {
        Contenido contenido = cargar(consumidor, id, Accion.CONTENIDO_EDITAR);
        contenido.eliminarEntregable(entregable(contenido, entregableId));
        return detalleDe(contenido);
    }

    // ---- Apoyo ----

    /** Verifica el rol y obtiene el contenido entre los de la empresa del consumidor. */
    Contenido cargar(Consumidor consumidor, UUID id, Accion accion) {
        PermisosPorRol.exigir(consumidor, accion);
        return contenidos.findByIdAndEmpresaId(id, consumidor.empresaId())
                .orElseThrow(() -> new NoEncontradoException("Contenido"));
    }

    DetalleContenido detalleDe(Contenido contenido) {
        return DetalleContenido.de(contenido, materiales.findByContenidoIdOrderBySubidoEnAsc(contenido.getId()));
    }

    private List<String> hashes(List<String> respuestas) {
        return respuestas.stream().map(hasher::hashear).toList();
    }

    private static Leccion leccion(Contenido contenido, UUID leccionId) {
        return contenido.getLecciones().stream()
                .filter(l -> l.getId().equals(leccionId))
                .findFirst()
                .orElseThrow(() -> new NoEncontradoException("Lección"));
    }

    private static Entregable entregable(Contenido contenido, UUID entregableId) {
        return contenido.getEntregables().stream()
                .filter(e -> e.getId().equals(entregableId))
                .findFirst()
                .orElseThrow(() -> new NoEncontradoException("Entregable"));
    }
}
