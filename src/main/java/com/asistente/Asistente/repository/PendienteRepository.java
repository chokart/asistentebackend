package com.asistente.Asistente.repository;

import com.asistente.Asistente.model.Pendiente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PendienteRepository extends JpaRepository<Pendiente, Long> {
}
