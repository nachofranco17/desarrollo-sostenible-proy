package uy.edu.um.xperience.contenido;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import uy.edu.um.xperience.comun.ReglaNegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Curso o proyecto publicado por una empresa (RF5). */
@Entity
@Table(name = "contenido")
public class Contenido {

    static final int MAX_ELEMENTOS = 100;

    @Id
    private UUID id;

    /** Empresa de quien lo crea; no se puede cambiar. */
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private TipoContenido tipo;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 4000)
    private String descripcion;

    @Column(nullable = false, length = 60)
    private String tecnologia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Nivel nivel;

    @Column(name = "duracion_horas", nullable = false)
    private int duracionHoras;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal costo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoContenido estado;

    @Column(name = "creado_por", nullable = false, updatable = false)
    private UUID creadoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    /** Nulo hasta que se persiste, así Spring Data lo reconoce como nuevo aunque el id ya esté asignado. */
    @Version
    private Long version;

    @OneToMany(mappedBy = "contenido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numero")
    private List<Leccion> lecciones = new ArrayList<>();

    @OneToMany(mappedBy = "contenido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numero")
    private List<Entregable> entregables = new ArrayList<>();

    protected Contenido() {
    }

    public Contenido(UUID empresaId, UUID creadoPor, TipoContenido tipo, DatosContenido datos) {
        this.id = UUID.randomUUID();
        this.empresaId = empresaId;
        this.creadoPor = creadoPor;
        this.tipo = tipo;
        this.estado = EstadoContenido.BORRADOR;
        this.creadoEn = Instant.now();
        aplicar(datos);
    }

    /** Campos editables. Tipo, empresa, estado y autor no se modifican por esta vía. */
    public record DatosContenido(String titulo, String descripcion, String tecnologia, Nivel nivel,
                                 int duracionHoras, BigDecimal costo) {
    }

    public void actualizar(DatosContenido datos) {
        exigirModificable();
        aplicar(datos);
    }

    private void aplicar(DatosContenido datos) {
        this.titulo = datos.titulo().trim();
        this.descripcion = datos.descripcion().trim();
        this.tecnologia = datos.tecnologia().trim();
        this.nivel = datos.nivel();
        this.duracionHoras = datos.duracionHoras();
        this.costo = datos.costo().setScale(2, RoundingMode.HALF_UP);
        tocar();
    }

    // ---- Lecciones (sólo cursos) ----

    public Leccion agregarLeccion(String titulo, String cuerpo) {
        exigirTipo(TipoContenido.CURSO, "Sólo los cursos tienen lecciones");
        exigirModificable();
        if (lecciones.size() >= MAX_ELEMENTOS) {
            throw new ReglaNegocioException("LIMITE_ALCANZADO", "Se alcanzó el máximo de lecciones");
        }
        Leccion leccion = new Leccion(this, lecciones.size() + 1, titulo.trim(), cuerpo);
        lecciones.add(leccion);
        tocar();
        return leccion;
    }

    public void actualizarLeccion(Leccion leccion, String titulo, String cuerpo) {
        exigirModificable();
        leccion.actualizar(titulo.trim(), cuerpo);
        tocar();
    }

    /** Sólo en borrador: una vez publicado puede haber Talentos con esa lección completada. */
    public void eliminarLeccion(Leccion leccion) {
        exigirBorrador("Sólo se pueden eliminar lecciones de un curso en borrador");
        lecciones.remove(leccion);
        renumerar(lecciones);
        tocar();
    }

    // ---- Entregables (sólo proyectos) ----

    public Entregable agregarEntregable(String titulo, String consigna, String pista, List<String> hashesRespuestas) {
        exigirTipo(TipoContenido.PROYECTO, "Sólo los proyectos tienen entregables");
        exigirModificable();
        if (entregables.size() >= MAX_ELEMENTOS) {
            throw new ReglaNegocioException("LIMITE_ALCANZADO", "Se alcanzó el máximo de entregables");
        }
        Entregable entregable = new Entregable(this, entregables.size() + 1, titulo.trim(), consigna.trim(), pista.trim());
        entregable.reemplazarRespuestas(hashesRespuestas);
        entregables.add(entregable);
        tocar();
        return entregable;
    }

    public void actualizarEntregable(Entregable entregable, String titulo, String consigna, String pista,
                                     List<String> hashesRespuestas) {
        exigirModificable();
        entregable.actualizar(titulo.trim(), consigna.trim(), pista.trim());
        if (hashesRespuestas != null) {
            entregable.reemplazarRespuestas(hashesRespuestas);
        }
        tocar();
    }

    public void eliminarEntregable(Entregable entregable) {
        exigirBorrador("Sólo se pueden eliminar entregables de un proyecto en borrador");
        entregables.remove(entregable);
        renumerar(entregables);
        tocar();
    }

    // ---- Ciclo de vida ----

    public void publicar() {
        if (estado == EstadoContenido.PUBLICADO) {
            throw new ReglaNegocioException("ESTADO_INVALIDO", "El contenido ya está publicado");
        }
        if (tipo == TipoContenido.CURSO && lecciones.isEmpty()) {
            throw new ReglaNegocioException("CONTENIDO_INCOMPLETO", "El curso necesita al menos una lección");
        }
        if (tipo == TipoContenido.PROYECTO && entregables.isEmpty()) {
            throw new ReglaNegocioException("CONTENIDO_INCOMPLETO", "El proyecto necesita al menos un entregable");
        }
        estado = EstadoContenido.PUBLICADO;
        tocar();
    }

    public void bajar() {
        if (estado != EstadoContenido.PUBLICADO) {
            throw new ReglaNegocioException("ESTADO_INVALIDO", "Sólo se puede dar de baja un contenido publicado");
        }
        estado = EstadoContenido.BAJADO;
        tocar();
    }

    /** Un contenido publicado o bajado puede tener inscriptos: sólo se borra si nunca se publicó. */
    public void exigirEliminable() {
        exigirBorrador("Sólo se puede eliminar un contenido en borrador; si ya se publicó, hay que darlo de baja");
    }

    public void exigirModificable() {
        if (estado == EstadoContenido.BAJADO) {
            throw new ReglaNegocioException("ESTADO_INVALIDO", "Un contenido dado de baja no se puede modificar");
        }
    }

    private void exigirBorrador(String mensaje) {
        if (estado != EstadoContenido.BORRADOR) {
            throw new ReglaNegocioException("ESTADO_INVALIDO", mensaje);
        }
    }

    private void exigirTipo(TipoContenido esperado, String mensaje) {
        if (tipo != esperado) {
            throw new ReglaNegocioException("TIPO_INVALIDO", mensaje);
        }
    }

    private static void renumerar(List<? extends ElementoNumerado> elementos) {
        for (int i = 0; i < elementos.size(); i++) {
            elementos.get(i).setNumero(i + 1);
        }
    }

    private void tocar() {
        actualizadoEn = Instant.now();
    }

    // ---- Lectura ----

    public UUID getId() {
        return id;
    }

    public UUID getEmpresaId() {
        return empresaId;
    }

    public TipoContenido getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getTecnologia() {
        return tecnologia;
    }

    public Nivel getNivel() {
        return nivel;
    }

    public int getDuracionHoras() {
        return duracionHoras;
    }

    public BigDecimal getCosto() {
        return costo;
    }

    public EstadoContenido getEstado() {
        return estado;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public List<Leccion> getLecciones() {
        return List.copyOf(lecciones);
    }

    public List<Entregable> getEntregables() {
        return List.copyOf(entregables);
    }

    public int cantidadElementos() {
        return tipo == TipoContenido.CURSO ? lecciones.size() : entregables.size();
    }
}
