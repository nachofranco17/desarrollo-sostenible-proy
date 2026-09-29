package uy.edu.um.xperience.contenido;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "leccion")
public class Leccion implements ElementoNumerado {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contenido_id", nullable = false, updatable = false)
    private Contenido contenido;

    @Column(nullable = false)
    private int numero;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 20000)
    private String cuerpo;

    protected Leccion() {
    }

    Leccion(Contenido contenido, int numero, String titulo, String cuerpo) {
        this.id = UUID.randomUUID();
        this.contenido = contenido;
        this.numero = numero;
        this.titulo = titulo;
        this.cuerpo = cuerpo;
    }

    void actualizar(String titulo, String cuerpo) {
        this.titulo = titulo;
        this.cuerpo = cuerpo;
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

    public String getCuerpo() {
        return cuerpo;
    }
}
