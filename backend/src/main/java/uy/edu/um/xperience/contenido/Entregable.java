package uy.edu.um.xperience.contenido;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "entregable")
public class Entregable implements ElementoNumerado {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contenido_id", nullable = false, updatable = false)
    private Contenido contenido;

    @Column(nullable = false)
    private int numero;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 4000)
    private String consigna;

    /** Lo que ve el Talento cuando su respuesta no coincide. Nunca es una medida de cercanía. */
    @Column(nullable = false, length = 500)
    private String pista;

    /** Sólo hashes; no hay getter que exponga las respuestas. */
    @OneToMany(mappedBy = "entregable", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RespuestaAceptada> respuestas = new ArrayList<>();

    protected Entregable() {
    }

    Entregable(Contenido contenido, int numero, String titulo, String consigna, String pista) {
        this.id = UUID.randomUUID();
        this.contenido = contenido;
        this.numero = numero;
        this.titulo = titulo;
        this.consigna = consigna;
        this.pista = pista;
    }

    void actualizar(String titulo, String consigna, String pista) {
        this.titulo = titulo;
        this.consigna = consigna;
        this.pista = pista;
    }

    /** La empresa no puede leer las respuestas aceptadas, sólo reemplazarlas en bloque. */
    void reemplazarRespuestas(List<String> hashes) {
        respuestas.clear();
        new LinkedHashSet<>(hashes).forEach(h -> respuestas.add(new RespuestaAceptada(this, h)));
    }

    @Override
    public void setNumero(int numero) {
        this.numero = numero;
    }

    public UUID getId() {
        return id;
    }

    public int getNumero() {
        return numero;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getConsigna() {
        return consigna;
    }

    public String getPista() {
        return pista;
    }

    public int cantidadRespuestasAceptadas() {
        return respuestas.size();
    }
}
