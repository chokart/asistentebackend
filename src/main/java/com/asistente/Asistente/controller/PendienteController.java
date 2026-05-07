package com.asistente.Asistente.controller;

import com.asistente.Asistente.model.Pendiente;
import com.asistente.Asistente.repository.PendienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pendientes")
@CrossOrigin(origins = "*") // Permitir peticiones desde cualquier origen por ahora
public class PendienteController {

    @Autowired
    private PendienteRepository pendienteRepository;

    @GetMapping
    public List<Pendiente> listarTodos() {
        return pendienteRepository.findAll();
    }

    @PostMapping
    public Pendiente guardar(@RequestBody Pendiente pendiente) {
        return pendienteRepository.save(pendiente);
    }

    @PutMapping("/{id}")
    public Pendiente actualizar(@PathVariable Long id, @RequestBody Pendiente pendienteDetalles) {
        Pendiente pendiente = pendienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe el pendiente con id: " + id));
        
        pendiente.setDescripcion(pendienteDetalles.getDescripcion());
        pendiente.setArea(pendienteDetalles.getArea());
        pendiente.setResponsable(pendienteDetalles.getResponsable());
        pendiente.setEstado(pendienteDetalles.getEstado());
        
        return pendienteRepository.save(pendiente);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        pendienteRepository.deleteById(id);
    }
}
