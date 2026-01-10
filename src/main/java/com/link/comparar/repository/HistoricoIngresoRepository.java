package com.link.comparar.repository;

import com.link.comparar.model.HistoricoIngreso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HistoricoIngresoRepository extends JpaRepository<HistoricoIngreso, Long> {

    /**
     * Busca registros por identificación
     */
    List<HistoricoIngreso> findByIdentificacion(String identificacion);

    /**
     * Busca todos los registros ordenados por fecha más reciente primero
     */
    List<HistoricoIngreso> findAllByOrderByFechaRegistroDesc();

    /**
     * Busca registros dentro de un rango de fechas
     */
    List<HistoricoIngreso> findByFechaRegistroBetween(LocalDateTime inicio, LocalDateTime fin);

    /**
     * Busca por nombre completo (ignorando mayúsculas/minúsculas)
     */
    List<HistoricoIngreso> findByNombreCompletoContainingIgnoreCase(String nombre);

    /**
     * Busca registros por periodo de comparación
     */
    List<HistoricoIngreso> findByPeriodoComparacionOrderByFechaRegistroDesc(String periodoComparacion);

    /**
     * Obtiene todos los periodos únicos disponibles
     */
    @Query("SELECT DISTINCT h.periodoComparacion FROM HistoricoIngreso h WHERE h.periodoComparacion IS NOT NULL ORDER BY h.periodoComparacion DESC")
    List<String> findDistinctPeriodos();

    /**
     * Verifica si ya existe un registro para una identificación en un periodo
     * específico
     * (evitar duplicados exactos en el mismo periodo)
     */
    @Query("SELECT COUNT(h) > 0 FROM HistoricoIngreso h WHERE h.identificacion = ?1 AND h.periodoComparacion = ?2")
    boolean existsByIdentificacionAndPeriodoComparacion(String identificacion, String periodoComparacion);

    /**
     * Elimina todos los registros de un periodo específico
     */
    void deleteByPeriodoComparacion(String periodoComparacion);
}
