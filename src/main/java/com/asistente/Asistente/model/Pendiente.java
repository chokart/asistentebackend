package com.asistente.Asistente.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "pendientes")
public class Pendiente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descripcion;
    private String area;
    private String responsable;
    private String estado; // Pendiente, En Proceso, Completado
    
    @Column(name = "fecha_creacion")
    private LocalDate fechaCreacion;

    public Pendiente() {
        this.fechaCreacion = LocalDate.now();
    }

    // El campo dias no se guarda en la DB, se calcula al vuelo
    @Transient
    private Long dias;

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getResponsable() { return responsable; }
    public void setResponsable(String responsable) { this.responsable = responsable; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDate getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDate fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public Long getDias() {
        if (fechaCreacion == null) return 0L;
        return ChronoUnit.DAYS.between(fechaCreacion, LocalDate.now());
    }
}
