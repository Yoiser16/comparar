package com.link.comparar.service;

import com.link.comparar.model.ComparisonResult;
import com.link.comparar.model.FileRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

@Service
public class FileComparisonService {

    private static final Logger logger = LoggerFactory.getLogger(FileComparisonService.class);

    /**
     * Extrae registros completos desde un archivo CSV con autodetección de
     * delimitador
     * y columna de ID.
     */
    public Map<String, FileRecord> extractRecordsFromCsv(MultipartFile file) throws IOException {
        Map<String, FileRecord> records = new HashMap<>();

        // Leemos el contenido una sola vez para poder inspeccionarlo y luego parsearlo
        byte[] content = file.getBytes();

        char delimiter = detectDelimiter(content);
        logger.info("CSV delimitador detectado: '{}'", delimiter == '\t' ? "TAB" : String.valueOf(delimiter));

        // Detectar si es un archivo OLIVE (tiene formato especial con resumen al
        // inicio)
        int headerRowIndex = detectHeaderRowForOlive(content, delimiter);
        logger.info("Fila de encabezado detectada en índice: {}", headerRowIndex);

        // Si es un archivo OLIVE, saltar las filas de resumen
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8));

        // Saltar líneas hasta llegar al encabezado real
        for (int i = 0; i < headerRowIndex - 1; i++) {
            try {
                reader.readLine();
            } catch (IOException e) {
                logger.warn("Error al saltar líneas de resumen: {}", e.getMessage());
            }
        }

        try (CSVParser csvParser = new CSVParser(reader,
                CSVFormat.DEFAULT.builder()
                        .setDelimiter(delimiter)
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreHeaderCase(true)
                        .setTrim(true)
                        .setIgnoreEmptyLines(true)
                        .setAllowMissingColumnNames(true)
                        .setQuote('"')
                        .build())) {

            Map<String, Integer> headerMap = csvParser.getHeaderMap();
            logger.info("CSV Headers detectados: {}", headerMap.keySet());

            // Detectar si es un archivo OLIVE basándose en los headers
            boolean isOliveFile = headerMap.keySet().stream()
                    .anyMatch(h -> h.toLowerCase().contains("redeemable") ||
                            h.toLowerCase().contains("streamers income") ||
                            h.toLowerCase().contains("agency payment"));

            // Detectar si es un archivo LIVEJOY basándose en los headers
            boolean isLivejoyFile = headerMap.keySet().stream()
                    .anyMatch(h -> h.toLowerCase().contains("loyalty") ||
                            h.toLowerCase().contains("loyalty credits"));

            if (isOliveFile) {
                logger.info("Detectado archivo OLIVE - Solo se mostrarán campos específicos de OLIVE");
            }
            
            if (isLivejoyFile) {
                logger.info("Detectado archivo LIVEJOY - Se excluirán Loyalty Credits, Bonos de Streamers y Bonus");
            }

            for (CSVRecord record : csvParser) {
                Map<String, String> data = new LinkedHashMap<>();

                String rawId = getIdFromRecord(record, headerMap);
                if (rawId == null || rawId.trim().isEmpty()) {
                    // No se pudo detectar ID en esta fila, continuar
                    continue;
                }

                String cleanId = cleanId(rawId);
                if (cleanId.isEmpty()) {
                    continue;
                }

                // Extraer solo los campos importantes y con nombres legibles
                for (String header : headerMap.keySet()) {
                    String value = safeGet(record, header);
                    if (value != null && !value.trim().isEmpty()) {
                        String displayName = getDisplayName(header);
                        if (displayName != null) {
                            // Si es archivo OLIVE, solo mostrar los 3 campos solicitados
                            if (isOliveFile) {
                                if (displayName.equals("Puntos Canjeables") ||
                                        displayName.equals("Ingresos Streamer") ||
                                        displayName.equals("Pago Agencia")) {
                                    data.put(displayName, value.trim());
                                }
                            } else {
                                // Excluir Loyalty Credits, Bonos de Streamers y Bonus si es LIVEJOY
                                if (isLivejoyFile && 
                                    (displayName.equals("Loyalty Credits") || 
                                     displayName.equals("Bonos de Streamers") || 
                                     displayName.equals("Bonus"))) {
                                    continue;
                                }
                                // Para archivos CSV (SALSA), excluir Balance, Monedas y Total
                                if (displayName.equals("Balance") || 
                                    displayName.equals("Monedas") || 
                                    displayName.equals("Total")) {
                                    continue;
                                }
                                data.put(displayName, value.trim());
                            }
                        }
                    }
                }

                FileRecord fileRecord = new FileRecord(cleanId, data, "CSV");
                records.put(cleanId, fileRecord);
                logger.debug("CSV Record agregado - ID: {}, Data: {}", cleanId, data);
            }
        }

        logger.info("Total registros extraídos del CSV: {}", records.size());
        return records;
    }

    /**
     * Extrae registros completos desde un archivo Excel (.xlsx o .xls)
     */
    public Map<String, FileRecord> extractRecordsFromExcel(MultipartFile file) throws IOException {
        Map<String, FileRecord> records = new HashMap<>();

        // Hojas que deseamos procesar (case-insensitive)
        List<String> targetSheets = Arrays.asList("LIVEJOY", "SALSA", "Olive");
        Set<String> normalizedTargets = new HashSet<>();
        for (String s : targetSheets) {
            normalizedTargets.add(s.toLowerCase(Locale.ROOT));
        }

        Workbook workbook = null;
        try {
            String filename = file.getOriginalFilename();
            if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(file.getInputStream());
            } else {
                workbook = new HSSFWorkbook(file.getInputStream());
            }

            logger.info("Excel - Total de hojas en archivo: {}", workbook.getNumberOfSheets());

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();
                String normalizedSheetName = sheetName.toLowerCase(Locale.ROOT).trim();

                if (!normalizedTargets.contains(normalizedSheetName)) {
                    logger.debug("Ignorando hoja '{}' (no está en la lista objetivo)", sheetName);
                    continue; // Solo procesamos las hojas solicitadas
                }

                logger.info("Procesando hoja objetivo: '{}'", sheetName);

                // Encabezados de esta hoja
                Row headerRow = sheet.getRow(0);
                int idColumnIndex = -1;
                List<String> headers = new ArrayList<>();

                if (headerRow != null) {
                    for (Cell cell : headerRow) {
                        String headerValue = getCellValueAsString(cell).trim();
                        headers.add(headerValue);
                        if (headerValue.equalsIgnoreCase("id")) {
                            idColumnIndex = cell.getColumnIndex();
                            logger.info("Hoja '{}' - Columna ID encontrada en posición: {}", sheetName, idColumnIndex);
                        }
                    }
                }

                if (idColumnIndex == -1) {
                    idColumnIndex = 0; // fallback
                    logger.info("Hoja '{}' - No se encontró columna 'ID', usando la columna 0", sheetName);
                }

                // Filas de datos
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null)
                        continue;

                    Cell idCell = row.getCell(idColumnIndex);
                    if (idCell == null)
                        continue;

                    String rawId = getCellValueAsString(idCell).trim();
                    if (rawId.isEmpty())
                        continue;

                    String cleanId = cleanId(rawId);
                    if (cleanId.isEmpty())
                        continue;

                    Map<String, String> data = new LinkedHashMap<>();
                    data.put("Sheet", canonicalSheetName(normalizedSheetName)); // guardar hoja canónica

                    for (int j = 0; j < headers.size(); j++) {
                        Cell cell = row.getCell(j);
                        if (cell == null)
                            continue;
                        String value = getCellValueAsString(cell).trim();
                        if (value.isEmpty())
                            continue;
                        // Mostrar "Bonus Top 100" específicamente para la hoja SALSA
                        String headerName = headers.get(j);
                        String normalizedHeader = headerName == null ? "" : headerName.toLowerCase(Locale.ROOT).trim();
                        boolean salsaBonusTop = normalizedSheetName.equals("salsa") &&
                                (normalizedHeader.contains("bonus top 100") ||
                                        (normalizedHeader.contains("bonus") && normalizedHeader.contains("top")
                                                && normalizedHeader.contains("100")));

                        if (salsaBonusTop) {
                            data.put("Bonus Top 100", value);
                            continue;
                        }

                        String displayName = getDisplayName(headerName);
                        if (displayName != null) {
                            // Excluir Balance, Monedas y Total de la hoja SALSA
                            if (normalizedSheetName.equals("salsa") && 
                                (displayName.equals("Balance") || 
                                 displayName.equals("Monedas") || 
                                 displayName.equals("Total"))) {
                                continue;
                            }
                            // Excluir Loyalty Credits, Bonos de Streamers y Bonus de la hoja LIVEJOY
                            if (normalizedSheetName.equals("livejoy") && 
                                (displayName.equals("Loyalty Credits") || 
                                 displayName.equals("Bonos de Streamers") || 
                                 displayName.equals("Bonus"))) {
                                continue;
                            }
                            data.put(displayName, value);
                        }
                    }

                    if (records.containsKey(cleanId)) {
                        // Merge: evitar perder info si el mismo ID aparece en otra hoja.
                        FileRecord existing = records.get(cleanId);
                        Map<String, String> existingData = existing.getData();
                        // Añadir solo campos nuevos (prefijando si ya existe la clave)
                        for (Map.Entry<String, String> entry : data.entrySet()) {
                            String key = entry.getKey();
                            String value = entry.getValue();
                            if (!existingData.containsKey(key)) {
                                existingData.put(key, value);
                            } else if (!Objects.equals(existingData.get(key), value)) {
                                // Si la clave existe con distinto valor, crear una variante con prefijo de hoja
                                String mergedKey = sheetName + "_" + key;
                                if (!existingData.containsKey(mergedKey)) {
                                    existingData.put(mergedKey, value);
                                }
                            }
                        }
                        logger.debug("ID '{}' ya existía; datos combinados desde hoja '{}'", cleanId, sheetName);
                    } else {
                        FileRecord fileRecord = new FileRecord(cleanId, data, "Excel");
                        records.put(cleanId, fileRecord);
                        logger.debug("Excel Record agregado - Hoja: {}, ID: {}, Data: {}", sheetName, cleanId, data);
                    }
                }
            }
        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }

        logger.info("Total registros extraídos del Excel (todas las hojas objetivo): {}", records.size());
        return records;
    }

    /**
     * Compara múltiples archivos CSV y Excel
     */
    public ComparisonResult compareFiles(MultipartFile[] csvFiles, MultipartFile[] excelFiles) throws IOException {
        // Combinar todos los registros CSV (fusionando datos de IDs duplicados)
        Map<String, FileRecord> csvRecords = new HashMap<>();
        for (MultipartFile csvFile : csvFiles) {
            if (csvFile != null && !csvFile.isEmpty()) {
                logger.info("Procesando archivo CSV: {}", csvFile.getOriginalFilename());
                Map<String, FileRecord> records = extractRecordsFromCsv(csvFile);

                // Fusionar registros en lugar de sobrescribir
                for (Map.Entry<String, FileRecord> entry : records.entrySet()) {
                    String id = entry.getKey();
                    FileRecord nuevoRecord = entry.getValue();

                    if (csvRecords.containsKey(id)) {
                        // Ya existe, fusionar datos
                        FileRecord existente = csvRecords.get(id);
                        Map<String, String> datosFusionados = new LinkedHashMap<>(existente.getData());

                        // Agregar/sobrescribir con nuevos datos no vacíos
                        nuevoRecord.getData().forEach((key, value) -> {
                            if (value != null && !value.trim().isEmpty()) {
                                datosFusionados.put(key, value);
                            }
                        });

                        csvRecords.put(id, new FileRecord(id, datosFusionados, "CSV"));
                    } else {
                        csvRecords.put(id, nuevoRecord);
                    }
                }
            }
        }

        // Combinar todos los registros Excel (fusionando datos de IDs duplicados)
        Map<String, FileRecord> excelRecords = new HashMap<>();
        for (MultipartFile excelFile : excelFiles) {
            if (excelFile != null && !excelFile.isEmpty()) {
                logger.info("Procesando archivo Excel: {}", excelFile.getOriginalFilename());
                Map<String, FileRecord> records = extractRecordsFromExcel(excelFile);

                // Fusionar registros en lugar de sobrescribir
                for (Map.Entry<String, FileRecord> entry : records.entrySet()) {
                    String id = entry.getKey();
                    FileRecord nuevoRecord = entry.getValue();

                    if (excelRecords.containsKey(id)) {
                        // Ya existe, fusionar datos
                        FileRecord existente = excelRecords.get(id);
                        Map<String, String> datosFusionados = new LinkedHashMap<>(existente.getData());

                        // Agregar/sobrescribir con nuevos datos no vacíos
                        nuevoRecord.getData().forEach((key, value) -> {
                            if (value != null && !value.trim().isEmpty()) {
                                datosFusionados.put(key, value);
                            }
                        });

                        excelRecords.put(id, new FileRecord(id, datosFusionados, "Excel"));
                    } else {
                        excelRecords.put(id, nuevoRecord);
                    }
                }
            }
        }

        Set<String> csvIds = csvRecords.keySet();
        Set<String> excelIds = excelRecords.keySet();

        logger.info("=== COMPARACIÓN MÚLTIPLE ===");
        logger.info("Total archivos CSV procesados: {}", csvFiles.length);
        logger.info("Total archivos Excel procesados: {}", excelFiles.length);
        logger.info("Total IDs únicos en CSV: {}", csvIds.size());
        logger.info("Total IDs únicos en Excel: {}", excelIds.size());

        // IDs que coinciden en ambos archivos
        Set<String> matchingIds = new HashSet<>(csvIds);
        matchingIds.retainAll(excelIds);
        logger.info("IDs coincidentes: {}", matchingIds.size());

        // IDs solo en CSV
        Set<String> onlyInCsv = new HashSet<>(csvIds);
        onlyInCsv.removeAll(excelIds);
        logger.info("IDs solo en CSV: {}", onlyInCsv.size());

        // IDs solo en Excel
        Set<String> onlyInExcel = new HashSet<>(excelIds);
        onlyInExcel.removeAll(csvIds);
        logger.info("IDs solo en Excel: {}", onlyInExcel.size());

        // Convertir a listas ordenadas
        List<String> matchingList = new ArrayList<>(matchingIds);
        List<String> onlyInCsvList = new ArrayList<>(onlyInCsv);
        List<String> onlyInExcelList = new ArrayList<>(onlyInExcel);

        Collections.sort(matchingList);
        Collections.sort(onlyInCsvList);
        Collections.sort(onlyInExcelList);

        // Crear lista de registros coincidentes con todos los datos
        List<FileRecord> matchingRecords = new ArrayList<>();
        for (String id : matchingList) {
            FileRecord csvRecord = csvRecords.get(id);
            FileRecord excelRecord = excelRecords.get(id);

            // Combinar datos de ambos archivos
            Map<String, String> combinedData = new LinkedHashMap<>();

            if (csvRecord != null && csvRecord.getData() != null) {
                csvRecord.getData().forEach((key, value) -> combinedData.put("CSV_" + key, value));
            }

            if (excelRecord != null && excelRecord.getData() != null) {
                excelRecord.getData().forEach((key, value) -> combinedData.put("Excel_" + key, value));
            }

            FileRecord matchingRecord = new FileRecord(id, combinedData, "Coincidente");
            matchingRecords.add(matchingRecord);
        }

        ComparisonResult result = new ComparisonResult(matchingList, onlyInCsvList, onlyInExcelList);
        result.setMatchingRecords(matchingRecords);

        return result;
    }

    /**
     * Compara los registros de dos archivos (método original mantenido para
     * compatibilidad)
     */
    public ComparisonResult compareFiles(MultipartFile csvFile, MultipartFile excelFile) throws IOException {
        Map<String, FileRecord> csvRecords = extractRecordsFromCsv(csvFile);
        Map<String, FileRecord> excelRecords = extractRecordsFromExcel(excelFile);

        Set<String> csvIds = csvRecords.keySet();
        Set<String> excelIds = excelRecords.keySet();

        logger.info("=== COMPARACIÓN ===");
        logger.info("Total IDs en CSV: {}", csvIds.size());
        logger.info("Total IDs en Excel: {}", excelIds.size());

        // IDs que coinciden en ambos archivos
        Set<String> matchingIds = new HashSet<>(csvIds);
        matchingIds.retainAll(excelIds);
        logger.info("IDs coincidentes: {}", matchingIds.size());

        // IDs solo en CSV
        Set<String> onlyInCsv = new HashSet<>(csvIds);
        onlyInCsv.removeAll(excelIds);
        logger.info("IDs solo en CSV: {}", onlyInCsv.size());

        // IDs solo en Excel
        Set<String> onlyInExcel = new HashSet<>(excelIds);
        onlyInExcel.removeAll(csvIds);
        logger.info("IDs solo en Excel: {}", onlyInExcel.size());

        // Convertir a listas ordenadas
        List<String> matchingList = new ArrayList<>(matchingIds);
        List<String> onlyInCsvList = new ArrayList<>(onlyInCsv);
        List<String> onlyInExcelList = new ArrayList<>(onlyInExcel);

        Collections.sort(matchingList);
        Collections.sort(onlyInCsvList);
        Collections.sort(onlyInExcelList);

        // Crear lista de registros coincidentes con todos los datos
        List<FileRecord> matchingRecords = new ArrayList<>();
        for (String id : matchingList) {
            FileRecord csvRecord = csvRecords.get(id);
            FileRecord excelRecord = excelRecords.get(id);

            // Combinar datos de ambos archivos
            Map<String, String> combinedData = new LinkedHashMap<>();

            if (csvRecord != null && csvRecord.getData() != null) {
                csvRecord.getData().forEach((key, value) -> combinedData.put("CSV_" + key, value));
            }

            if (excelRecord != null && excelRecord.getData() != null) {
                excelRecord.getData().forEach((key, value) -> combinedData.put("Excel_" + key, value));
            }

            FileRecord matchingRecord = new FileRecord(id, combinedData, "Coincidente");
            matchingRecords.add(matchingRecord);
        }

        ComparisonResult result = new ComparisonResult(matchingList, onlyInCsvList, onlyInExcelList);
        result.setMatchingRecords(matchingRecords);
        result.setCsvRecordsMap(csvRecords);
        result.setExcelRecordsMap(excelRecords);

        return result;
    }

    /**
     * Obtiene el valor de una celda como String
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Convertir número a string sin notación científica
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == (long) numericValue) {
                        return String.valueOf((long) numericValue);
                    } else {
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // Para fórmulas, intentar obtener el valor calculado
                try {
                    return cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    try {
                        double numericValue = cell.getNumericCellValue();
                        if (numericValue == (long) numericValue) {
                            return String.valueOf((long) numericValue);
                        } else {
                            return String.valueOf(numericValue);
                        }
                    } catch (IllegalStateException e2) {
                        return "";
                    }
                }
            default:
                return "";
        }
    }

    /**
     * Limpia un ID removiendo espacios, caracteres especiales y normalizando el
     * formato
     * Extrae solo los números del inicio del string
     */
    public String cleanId(String id) {
        if (id == null || id.trim().isEmpty()) {
            return "";
        }

        // Remover todos los espacios en blanco
        String cleaned = id.replaceAll("\\s+", "");

        // Si el ID es un número con decimales .0, remover los decimales
        if (cleaned.matches("\\d+\\.0+")) {
            cleaned = cleaned.substring(0, cleaned.indexOf('.'));
        }

        // Extraer solo los dígitos del inicio del string
        // Esto maneja casos como "279054954Elianamora" -> "279054954"
        StringBuilder numericId = new StringBuilder();
        for (char c : cleaned.toCharArray()) {
            if (Character.isDigit(c)) {
                numericId.append(c);
            } else {
                // Detenerse al encontrar el primer carácter no numérico
                break;
            }
        }

        return numericId.toString();
    }

    /**
     * Detecta en qué fila está el encabezado real para archivos OLIVE
     * Los archivos OLIVE tienen filas de resumen al inicio antes de los datos
     * tabulares
     */
    private int detectHeaderRowForOlive(byte[] content, char delimiter) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = br.readLine()) != null) {
                lineNumber++;
                // La fila de encabezado típicamente contiene "User ID"
                if (line.toLowerCase().contains("user id") || line.toLowerCase().contains("userid")) {
                    logger.info("Detectada fila de encabezado OLIVE en línea {}", lineNumber);
                    return lineNumber;
                }
            }
        }
        return 0; // Si no se detecta formato OLIVE, asumir formato normal
    }

    /**
     * Detecta el delimitador probable del CSV inspeccionando las primeras líneas.
     */
    private char detectDelimiter(byte[] content) throws IOException {
        char[] candidates = new char[] { ',', ';', '\t', '|' };
        int[] counts = new int[candidates.length];

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            String line;
            int lines = 0;
            while ((line = br.readLine()) != null && lines < 10) {
                if (line.trim().isEmpty())
                    continue;
                for (int i = 0; i < candidates.length; i++) {
                    counts[i] += countOccurrences(line, candidates[i]);
                }
                lines++;
            }
        }

        int maxIdx = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[maxIdx])
                maxIdx = i;
        }
        // Por defecto, coma
        return counts[maxIdx] == 0 ? ',' : candidates[maxIdx];
    }

    private int countOccurrences(String s, char ch) {
        int c = 0;
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) == ch)
                c++;
        return c;
    }

    /** Obtiene de forma robusta el ID de un registro CSV */
    private String getIdFromRecord(CSVRecord record, Map<String, Integer> headerMap) {
        // 1) Buscar columna cuyo header sugiera ser ID
        String[] idCandidates = new String[] { "id", "identificacion", "identificación", "documento", "cedula",
                "cédula", "dni", "rut", "nit" };

        for (String header : headerMap.keySet()) {
            String norm = normalize(header);
            for (String cand : idCandidates) {
                if (norm.equals(normalize(cand)) || norm.contains(normalize(cand))) {
                    String v = safeGet(record, header);
                    if (v != null && !v.isBlank())
                        return v;
                }
            }
        }

        // 2) Fallback: usar primera columna
        if (record.size() > 0) {
            String v = record.get(0);
            String cleaned = cleanId(v);
            if (!cleaned.isEmpty() && cleaned.length() >= 5)
                return v;
        }

        // 3) Fallback: buscar la primera celda que luzca como un ID numérico largo
        for (int i = 0; i < record.size(); i++) {
            String v = record.get(i);
            if (v == null)
                continue;
            String cleaned = cleanId(v);
            if (!cleaned.isEmpty() && cleaned.length() >= 5)
                return v;
        }
        return null;
    }

    private String safeGet(CSVRecord record, String header) {
        try {
            return record.get(header);
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalize(String s) {
        String tmp = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        tmp = tmp.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return tmp;
    }

    private String canonicalSheetName(String normalizedSheetName) {
        if (normalizedSheetName == null) {
            return "";
        }
        return switch (normalizedSheetName.trim()) {
            case "livejoy" -> "LIVEJOY";
            case "salsa" -> "SALSA";
            case "olive" -> "OLIVE";
            default -> normalizedSheetName.trim().toUpperCase(Locale.ROOT);
        };
    }

    /**
     * Traduce nombres de campos técnicos a nombres amigables para el usuario
     * Retorna null si el campo no debe mostrarse
     */
    private String getDisplayName(String fieldName) {
        if (fieldName == null)
            return null;

        // Evitar fallar con encabezados vacíos o en blanco
        if (fieldName.trim().isEmpty()) {
            return null;
        }

        String normalized = fieldName.toLowerCase().trim();

        // Campos que NO deben mostrarse (demasiado técnicos o irrelevantes)
        if (normalized.equals("id") ||
                normalized.contains("conversion") ||
                normalized.contains("malicious") ||
                normalized.contains("penalty") ||
                normalized.contains("bet") ||
                normalized.contains("turnover") ||
                normalized.contains("remaining") ||
                // Ocultar campos con $ que no son útiles
                normalized.contains("coin $") ||
                normalized.contains("match $") ||
                // Ocultar "coins" simple (mantener solo "total coins")
                normalized.equals("coins") ||
                // Ocultar métricas internas que el usuario no necesita ver
                normalized.equals("matchs") ||
                normalized.equals("matches") ||
                normalized.equals("required matchs") ||
                normalized.equals("required matches") ||
                normalized.equals("coincidencias") ||
                normalized.equals("requeridas")) {
            return null;
        }

        // Mapeo de nombres técnicos a nombres amigables en español
        Map<String, String> displayNames = new HashMap<>();

        // Nombres y usuarios
        displayNames.put("full name", "Nombre Completo");
        displayNames.put("name", "Nombre");
        displayNames.put("user", "Usuario");
        displayNames.put("username", "Usuario");
        displayNames.put("nombre completo", "Nombre Completo");
        displayNames.put("nombre", "Nombre");

        // Fechas y tiempo
        displayNames.put("week", "Semana");
        displayNames.put("fecha", "Fecha");
        displayNames.put("feacha", "Fecha"); // Corrección de error común en Excel
        displayNames.put("date", "Fecha");
        displayNames.put("time", "Hora");
        displayNames.put("hora", "Hora");

        // Contacto
        displayNames.put("numero de whatsapp", "WhatsApp");
        displayNames.put("numero de whatssap", "WhatsApp"); // Corrección de error común
        displayNames.put("whatsapp", "WhatsApp");
        displayNames.put("whatssap", "WhatsApp"); // Corrección de error común
        displayNames.put("phone", "Teléfono");
        displayNames.put("telefono", "Teléfono");
        displayNames.put("email", "Correo");
        displayNames.put("correo", "Correo");

        // Ubicación
        displayNames.put("pais", "País");
        displayNames.put("country", "País");
        displayNames.put("ciudad", "Ciudad");
        displayNames.put("city", "Ciudad");

        // Campos relacionados con monedas/balance
        displayNames.put("total coins", "Total de Monedas");
        displayNames.put("coins", "Monedas");
        displayNames.put("balance", "Balance");
        displayNames.put("total balance", "Balance Total");
        displayNames.put("saldo", "Saldo");
        displayNames.put("total", "Total");
        displayNames.put("amount", "Monto");
        displayNames.put("monto", "Monto");

        // Campos específicos de OLIVE
        displayNames.put("redeemable points", "Puntos Canjeables");
        displayNames.put("redeemable level", "Nivel Canjeable");
        displayNames.put("streamers income", "Ingresos Streamer");
        displayNames.put("agency payment", "Pago Agencia");
        displayNames.put("agency commission", "Comisión Agencia");
        displayNames.put("total revenue", "Ingresos Totales");
        displayNames.put("agency paym", "Pago Agencia"); // Versión abreviada por si está truncado

        // Bonos y recompensas
        displayNames.put("agency bonus $", "Bono de Agencia $");
        displayNames.put("agency bonus", "Bono de Agencia");
        displayNames.put("event reward $", "Recompensa de Evento $");
        displayNames.put("event reward", "Recompensa de Evento");
        displayNames.put("bonus top 100", "Bonus Top 100");
        displayNames.put("loyalty credits", "Loyalty Credits");
        displayNames.put("bonos de streamers", "Bonos de Streamers");
        displayNames.put("streamers bonus", "Bonos de Streamers");
        displayNames.put("bonus", "Bonus");

        // Estados y otros
        displayNames.put("ok", "Estado");
        displayNames.put("status", "Estado");
        displayNames.put("estado", "Estado");
        displayNames.put("sheet", "Hoja");
        displayNames.put("idioma", "Idioma");
        displayNames.put("language", "Idioma");
        displayNames.put("salsa o café", "Tipo");
        displayNames.put("salsa o cafe", "Tipo");

        // Buscar coincidencia exacta
        String display = displayNames.get(normalized);
        if (display != null) {
            return display;
        }

        // Si contiene alguna palabra clave conocida, usar el nombre amigable
        for (Map.Entry<String, String> entry : displayNames.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Para campos desconocidos pero que no están en la lista negra, capitalizar
        return fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }
}
