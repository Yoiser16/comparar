package com.link.comparar.service;

import com.link.comparar.model.FileRecord;
import com.link.comparar.model.HistoricoIngreso;
import com.link.comparar.repository.HistoricoIngresoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class HistoricoService {

    private static final Logger logger = LoggerFactory.getLogger(HistoricoService.class);

    @Autowired
    private HistoricoIngresoRepository historicoRepository;

    /**
     * Guarda en el histórico aquellos registros que tienen ingresos
     * (Total de Monedas > 0 o cualquier campo de dinero > 0)
     */
    @Transactional
    public void guardarRegistrosConIngresos(List<FileRecord> matchingRecords, String periodoComparacion) {
        int guardados = 0;
        LocalDateTime ahora = LocalDateTime.now();

        // Si ya existen registros con este periodo, eliminarlos primero (reemplazo
        // completo)
        List<HistoricoIngreso> registrosExistentes = historicoRepository
                .findByPeriodoComparacionOrderByFechaRegistroDesc(periodoComparacion);
        if (!registrosExistentes.isEmpty()) {
            historicoRepository.deleteByPeriodoComparacion(periodoComparacion);
            logger.info("Eliminados {} registros anteriores del periodo '{}'", registrosExistentes.size(),
                    periodoComparacion);
        }

        for (FileRecord record : matchingRecords) {
            try {
                // Extraer datos del registro
                String id = record.getId();
                Map<String, String> data = record.getData();

                // Extraer valores con mapeo de campos LIVEJOY y OLIVE
                // OLIVE: Ingresos Streamer → MONEDAS, Pago Agencia → BONO AGENCIA, Puntos
                // Canjeables → RECOMPENSA
                String nombreCompleto = obtenerNombreCompleto(data);
                Double monedas = extraerNumeroMultiple(data, "Monedas", "Ingresos", "Ingresos Streamer");
                Double totalMonedas = extraerNumero(data, "Total de Monedas");
                Double bonoAgencia = extraerNumeroMultiple(data, "Bono de Agencia $", "Bonos de streamers",
                        "Pago Agencia");
                Double recompensaEvento = extraerNumeroMultiple(data, "Recompensa de Evento $",
                        "Puntos Ganados por Usuaria", "Puntos Canjeables");
                Double bonusTop100 = extraerNumeroMultiple(data, "Bonus Top 100", "Bonus");
                Double loyaltyCredits = extraerNumero(data, "Loyalty Credits");
                Integer semana = extraerEntero(data, "Semana");
                String pais = obtenerValor(data, "País");
                String whatsapp = obtenerValor(data, "WhatsApp");
                String fuente = determinarFuente(data);
                String sheet = obtenerValor(data, "Sheet");

                // Crear y guardar registro
                HistoricoIngreso historico = new HistoricoIngreso(
                        id, nombreCompleto, monedas, totalMonedas, bonoAgencia,
                        recompensaEvento, bonusTop100, loyaltyCredits, semana, pais, whatsapp, fuente,
                        sheet, periodoComparacion);

                historicoRepository.save(historico);
                guardados++;

                logger.info("Guardado en histórico: {} - {} (Periodo: {})", id, nombreCompleto, periodoComparacion);

            } catch (Exception e) {
                logger.error("Error al guardar registro en histórico: {}", record.getId(), e);
            }
        }

        logger.info("Total de registros con ingresos guardados en histórico: {}", guardados);
    }

    /**
     * Obtiene todos los registros del histórico ordenados por fecha descendente
     */
    public List<HistoricoIngreso> obtenerTodosLosRegistros() {
        return historicoRepository.findAllByOrderByFechaRegistroDesc();
    }

    /**
     * Busca registros por identificación
     */
    public List<HistoricoIngreso> buscarPorIdentificacion(String identificacion) {
        return historicoRepository.findByIdentificacion(identificacion);
    }

    /**
     * Busca registros por nombre
     */
    public List<HistoricoIngreso> buscarPorNombre(String nombre) {
        return historicoRepository.findByNombreCompletoContainingIgnoreCase(nombre);
    }

    /**
     * Busca registros por periodo de comparación
     */
    public List<HistoricoIngreso> buscarPorPeriodo(String periodoComparacion) {
        return historicoRepository.findByPeriodoComparacionOrderByFechaRegistroDesc(periodoComparacion);
    }

    /**
     * Obtiene todos los periodos únicos disponibles
     */
    public List<String> obtenerPeriodosDisponibles() {
        return historicoRepository.findDistinctPeriodos();
    }

    /**
     * Elimina todos los registros de un período específico
     */
    @Transactional
    public void eliminarPorPeriodo(String periodoComparacion) {
        historicoRepository.deleteByPeriodoComparacion(periodoComparacion);
        logger.info("Se han eliminado todos los registros del período '{}'", periodoComparacion);
    }

    /**
     * Elimina todos los registros del histórico
     */
    public long limpiarHistorico() {
        long count = historicoRepository.count();
        historicoRepository.deleteAll();
        logger.warn("Se han eliminado {} registros del histórico", count);
        return count;
    }

    /**
     * Verifica si un registro tiene ingresos
     */
    private boolean tieneIngresos(Map<String, String> data) {
        Double totalMonedas = extraerNumero(data, "Total de Monedas");
        Double bonoAgencia = extraerNumero(data, "Bono de Agencia $");
        Double recompensaEvento = extraerNumero(data, "Recompensa de Evento $");

        return (totalMonedas != null && totalMonedas > 0) ||
                (bonoAgencia != null && bonoAgencia > 0) ||
                (recompensaEvento != null && recompensaEvento > 0);
    }

    /**
     * Obtiene el nombre completo de los datos (prioriza Excel)
     */
    private String obtenerNombreCompleto(Map<String, String> data) {
        String nombre = data.get("Excel_Nombre Completo");
        if (nombre == null || nombre.isEmpty()) {
            nombre = data.get("CSV_Nombre Completo");
        }
        if (nombre == null || nombre.isEmpty()) {
            nombre = data.get("Nombre Completo");
        }
        return nombre != null ? nombre : "Sin nombre";
    }

    /**
     * Obtiene un valor de los datos (busca en CSV y Excel)
     */
    private String obtenerValor(Map<String, String> data, String clave) {
        String valor = data.get("Excel_" + clave);
        if (valor == null || valor.isEmpty()) {
            valor = data.get("CSV_" + clave);
        }
        if (valor == null || valor.isEmpty()) {
            valor = data.get(clave);
        }
        return valor;
    }

    /**
     * Extrae un número de los datos
     */
    private Double extraerNumero(Map<String, String> data, String clave) {
        String valor = obtenerValor(data, clave);
        if (valor == null || valor.isEmpty()) {
            return null;
        }

        try {
            // Limpiar el string de caracteres no numéricos excepto punto y coma
            valor = valor.replaceAll("[^0-9.,]", "").replace(",", ".");
            return Double.parseDouble(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extrae un número probando múltiples claves (para soportar diferentes nombres
     * de campos)
     */
    private Double extraerNumeroMultiple(Map<String, String> data, String... claves) {
        for (String clave : claves) {
            Double valor = extraerNumero(data, clave);
            if (valor != null) {
                return valor;
            }
        }
        return null;
    }

    /**
     * Extrae un entero de los datos
     */
    private Integer extraerEntero(Map<String, String> data, String clave) {
        Double numero = extraerNumero(data, clave);
        return numero != null ? numero.intValue() : null;
    }

    /**
     * Determina la fuente del dato (CSV, Excel o Ambos)
     */
    private String determinarFuente(Map<String, String> data) {
        boolean tieneCsv = data.keySet().stream().anyMatch(k -> k.startsWith("CSV_"));
        boolean tieneExcel = data.keySet().stream().anyMatch(k -> k.startsWith("Excel_"));

        if (tieneCsv && tieneExcel)
            return "Ambos";
        if (tieneExcel)
            return "Excel";
        if (tieneCsv)
            return "CSV";
        return "Desconocido";
    }
}
