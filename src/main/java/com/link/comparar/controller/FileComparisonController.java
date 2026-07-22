package com.link.comparar.controller;

import com.link.comparar.model.ComparisonResult;
import com.link.comparar.model.FileRecord;
import com.link.comparar.model.HistoricoIngreso;
import com.link.comparar.service.FileComparisonService;
import com.link.comparar.service.HistoricoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import jakarta.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@SessionAttributes("comparisonResult")
public class FileComparisonController {

    private static final String CONTRASEÑA_HISTORICO = "Doblett2025";

    @Autowired
    private FileComparisonService fileComparisonService;

    @Autowired
    private HistoricoService historicoService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * Muestra la página de login para el histórico
     */
    @GetMapping("/historico/login")
    public String mostrarLoginHistorico(Model model,
            @RequestParam(value = "error", required = false) String error) {
        if (error != null) {
            model.addAttribute("error", "Contraseña incorrecta. Intenta de nuevo.");
        }
        return "historico-login";
    }

    /**
     * Procesa la autenticación del histórico
     */
    @PostMapping("/historico/auth")
    public String autenticarHistorico(
            @RequestParam("contraseña") String contraseña,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // Validar contraseña en el servidor (seguro, no visible)
        if (contraseña != null && contraseña.equals(CONTRASEÑA_HISTORICO)) {
            // Marcar como autenticado en la sesión
            session.setAttribute("historicoAutenticado", true);
            return "redirect:/historico";
        } else {
            redirectAttributes.addFlashAttribute("error", "true");
            return "redirect:/historico/login";
        }
    }

    /**
     * Cierra sesión del histórico
     */
    @GetMapping("/historico/logout")
    public String cerrarSesionHistorico(HttpSession session) {
        session.removeAttribute("historicoAutenticado");
        return "redirect:/historico/login";
    }

    @PostMapping("/compare")
    public String compareFiles(
            @RequestParam(value = "csvFiles", required = false) MultipartFile[] csvFiles,
            @RequestParam("excelFiles") MultipartFile[] excelFiles,
            @RequestParam(value = "salsaPastedText", required = false) String salsaPastedText,
            @RequestParam(value = "periodoComparacion", required = false) String periodoComparacion,
            Model model,
            RedirectAttributes redirectAttributes,
            jakarta.servlet.http.HttpSession session) {

        // Procesar texto pegado de SALSA si se proporcionó
        if (salsaPastedText != null && !salsaPastedText.trim().isEmpty()) {
            PastedMultipartFile pastedFile = new PastedMultipartFile("salsa_pegado.txt", salsaPastedText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            csvFiles = new MultipartFile[] { pastedFile };
        }

        // Validar que se hayan seleccionado archivos o proporcionado texto pegado
        if ((csvFiles == null || csvFiles.length == 0) && (excelFiles == null || excelFiles.length == 0)) {
            redirectAttributes.addFlashAttribute("error",
                    "Por favor, selecciona archivos o pega el reporte de SALSA, y selecciona un archivo Excel.");
            return "redirect:/";
        }

        // Validar periodo de comparación
        if (periodoComparacion == null || periodoComparacion.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "Por favor, ingresa un nombre/fecha para identificar esta comparación.");
            return "redirect:/";
        }

        // Validar que los archivos no estén vacíos
        boolean allCsvEmpty = true;
        if (csvFiles != null) {
            for (MultipartFile csv : csvFiles) {
                if (!csv.isEmpty()) {
                    allCsvEmpty = false;
                    break;
                }
            }
        }

        boolean allExcelEmpty = true;
        for (MultipartFile excel : excelFiles) {
            if (!excel.isEmpty()) {
                allExcelEmpty = false;
                break;
            }
        }

        if (allCsvEmpty || allExcelEmpty) {
            redirectAttributes.addFlashAttribute("error", "Los archivos o el texto pegado seleccionados están vacíos.");
            return "redirect:/";
        }

        // Validar extensiones de archivos
        if (csvFiles != null) {
            for (MultipartFile csvFile : csvFiles) {
                if (csvFile.isEmpty())
                    continue;
                String csvFileName = csvFile.getOriginalFilename();
                if (csvFileName == null || 
                    (!csvFileName.toLowerCase().endsWith(".csv") && !csvFileName.toLowerCase().endsWith(".txt"))) {
                    redirectAttributes.addFlashAttribute("error",
                            "Todos los archivos de datos deben tener extensión .csv o .txt (pasted text): " + csvFileName);
                    return "redirect:/";
                }
            }
        }

        for (MultipartFile excelFile : excelFiles) {
            if (excelFile.isEmpty())
                continue;
            String excelFileName = excelFile.getOriginalFilename();
            if (excelFileName == null ||
                    (!excelFileName.toLowerCase().endsWith(".xlsx") && !excelFileName.toLowerCase().endsWith(".xls"))) {
                redirectAttributes.addFlashAttribute("error",
                        "Todos los archivos Excel deben tener extensión .xlsx o .xls: " + excelFileName);
                return "redirect:/";
            }
        }

        try {
            // Comparar archivos (el service ahora acepta múltiples archivos)
            ComparisonResult result = fileComparisonService.compareFiles(csvFiles, excelFiles);

            // Guardar automáticamente registros con ingresos en el histórico con el periodo
            historicoService.guardarRegistrosConIngresos(result.getMatchingRecords(), periodoComparacion.trim());

            // Guardar periodo en la sesión para actualizar porcentajes después
            session.setAttribute("periodoComparacion", periodoComparacion.trim());

            // Preparar nombres de archivos para mostrar
            StringBuilder csvNames = new StringBuilder();
            StringBuilder excelNames = new StringBuilder();

            for (int i = 0; i < csvFiles.length; i++) {
                if (!csvFiles[i].isEmpty()) {
                    if (csvNames.length() > 0)
                        csvNames.append(", ");
                    csvNames.append(csvFiles[i].getOriginalFilename());
                }
            }

            for (int i = 0; i < excelFiles.length; i++) {
                if (!excelFiles[i].isEmpty()) {
                    if (excelNames.length() > 0)
                        excelNames.append(", ");
                    excelNames.append(excelFiles[i].getOriginalFilename());
                }
            }

            model.addAttribute("result", result);
            model.addAttribute("csvFileName", csvNames.toString());
            model.addAttribute("excelFileName", excelNames.toString());

            // Guardar resultado en sesión para permitir descargas posteriores
            model.addAttribute("comparisonResult", result);

            return "result";
        } catch (java.io.IOException e) {
            String errorMsg;
            if (e.getMessage() != null && e.getMessage().contains("invalid char")) {
                errorMsg = "Uno de los archivos CSV tiene un formato incorrecto. Verifica que usen punto y coma (;) como separador y que no tengan comillas mal cerradas.";
            } else {
                errorMsg = "Error al procesar los archivos. Asegúrate de que:\n" +
                        "• Los CSV usen punto y coma (;) como separador\n" +
                        "• Los Excel estén en formato válido (.xlsx o .xls)\n" +
                        "• Todos los archivos tengan una columna 'ID'";
            }
            redirectAttributes.addFlashAttribute("error", errorMsg);
            return "redirect:/";
        } catch (Exception e) {
            String errorMsg = "Error inesperado: "
                    + (e.getMessage() != null ? e.getMessage() : "Verifica el formato de tus archivos");
            redirectAttributes.addFlashAttribute("error", errorMsg);
            return "redirect:/";
        }
    }

    @GetMapping("/download/matches")
    public ResponseEntity<byte[]> downloadMatches(
            @SessionAttribute("comparisonResult") ComparisonResult result,
            @RequestParam(defaultValue = "csv") String format) {
        try {
            byte[] data;
            String filename;
            MediaType mediaType;

            switch (format.toLowerCase()) {
                case "excel":
                    data = generateExcel(result.getMatchingRecords());
                    filename = "coincidencias.xlsx";
                    mediaType = MediaType
                            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    break;
                default:
                    data = generateCsv(result.getMatchingRecords(), "matches");
                    filename = "coincidencias.csv";
                    mediaType = MediaType.parseMediaType("text/csv;charset=UTF-8");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(data.length);
            return ResponseEntity.ok().headers(headers).body(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    @GetMapping("/download/livejoy-tutora")
    public ResponseEntity<byte[]> downloadLivejoyTutora(
            @SessionAttribute("comparisonResult") ComparisonResult result,
            jakarta.servlet.http.HttpSession session,
            @RequestParam(value = "p1", defaultValue = "12") double porcentaje1,
            @RequestParam(value = "p2", defaultValue = "40") double porcentaje2) {
        try {
            // Guardar en base de datos los porcentajes personalizados usados
            String periodo = (String) session.getAttribute("periodoComparacion");
            if (periodo != null) {
                historicoService.actualizarPorcentajes(periodo, "LIVEJOY", null, porcentaje1, porcentaje2);
            }
            byte[] data = generateLivejoyTutoraExcel(result.getMatchingRecords(), porcentaje1, porcentaje2);
            String filename = "livejoy_tutora.xlsx";
            MediaType mediaType = MediaType
                    .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(data.length);
            return ResponseEntity.ok().headers(headers).body(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/download/salsa-resumen")
    public ResponseEntity<byte[]> downloadSalsaResumen(
            @SessionAttribute("comparisonResult") ComparisonResult result,
            jakarta.servlet.http.HttpSession session,
            @RequestParam(value = "descuento", defaultValue = "60") double porcentajeDescuento) {
        try {
            // Guardar en base de datos el porcentaje de descuento personalizado usado
            String periodo = (String) session.getAttribute("periodoComparacion");
            if (periodo != null) {
                historicoService.actualizarPorcentajes(periodo, "SALSA", porcentajeDescuento, null, null);
            }
            byte[] data = generateSalsaResumenExcel(result.getMatchingRecords(), porcentajeDescuento);
            String filename = "salsa_resumen.xlsx";
            MediaType mediaType = MediaType
                    .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(data.length);
            return ResponseEntity.ok().headers(headers).body(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/download/olive-resumen")
    public ResponseEntity<byte[]> downloadOliveResumen(
            @SessionAttribute("comparisonResult") ComparisonResult result,
            jakarta.servlet.http.HttpSession session,
            @RequestParam(value = "p1", defaultValue = "60") double p1,
            @RequestParam(value = "p2", defaultValue = "40") double p2) {
        try {
            // Guardar en base de datos los porcentajes personalizados usados
            String periodo = (String) session.getAttribute("periodoComparacion");
            if (periodo != null) {
                historicoService.actualizarPorcentajes(periodo, "OLIVE", null, p1, p2);
            }
            byte[] data = generateOliveResumenExcel(result.getMatchingRecords(), p1, p2);
            String filename = "olive_resumen.xlsx";
            MediaType mediaType = MediaType
                    .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(data.length);
            return ResponseEntity.ok().headers(headers).body(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/download/csv-only")
    public ResponseEntity<byte[]> downloadCsvOnly(@SessionAttribute("comparisonResult") ComparisonResult result) {
        try {
            List<FileRecord> records = result.getOnlyInCsv().stream()
                    .map(id -> result.getCsvRecordsMap().get(id))
                    .filter(r -> r != null)
                    .toList();
            byte[] csvData = generateCsv(records, "csv-only");
            return buildCsvResponse(csvData, "solo-csv.csv");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/download/excel-only")
    public ResponseEntity<byte[]> downloadExcelOnly(@SessionAttribute("comparisonResult") ComparisonResult result) {
        try {
            List<FileRecord> records = result.getOnlyInExcel().stream()
                    .map(id -> result.getExcelRecordsMap().get(id))
                    .filter(r -> r != null)
                    .toList();
            byte[] csvData = generateCsv(records, "excel-only");
            return buildCsvResponse(csvData, "solo-excel.csv");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private byte[] generateCsv(List<FileRecord> records, String type) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8));

        if (records.isEmpty()) {
            writer.println("No hay registros para exportar");
            writer.flush();
            return baos.toByteArray();
        }

        // Recolectar todas las columnas únicas
        Set<String> allColumns = new LinkedHashSet<>();
        allColumns.add("ID");
        for (FileRecord record : records) {
            if (record.getData() != null) {
                allColumns.addAll(record.getData().keySet());
            }
        }

        // Escribir encabezados
        writer.println(String.join(",", allColumns.stream()
                .map(this::escapeCsvValue)
                .toArray(String[]::new)));

        // Escribir filas
        for (FileRecord record : records) {
            StringBuilder row = new StringBuilder();
            row.append(escapeCsvValue(record.getId()));

            for (String column : allColumns) {
                if (!column.equals("ID")) {
                    row.append(",");
                    String value = record.getData() != null ? record.getData().get(column) : "";
                    row.append(escapeCsvValue(value != null ? value : ""));
                }
            }
            writer.println(row.toString());
        }

        writer.flush();
        return baos.toByteArray();
    }

    private String escapeCsvValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        // Si contiene coma, comillas o salto de línea, envolver en comillas y escapar
        // comillas internas
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private ResponseEntity<byte[]> buildCsvResponse(byte[] data, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(data.length);
        return ResponseEntity.ok().headers(headers).body(data);
    }

    private byte[] generateExcel(List<FileRecord> records) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Coincidencias");

        if (records.isEmpty()) {
            Row row = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Cell cell = row.createCell(0);
            cell.setCellValue("No hay registros para exportar");
            workbook.write(baos);
            workbook.close();
            return baos.toByteArray();
        }

        // Recolectar columnas
        Set<String> allColumns = new LinkedHashSet<>();
        allColumns.add("ID");
        for (FileRecord record : records) {
            if (record.getData() != null) {
                allColumns.addAll(record.getData().keySet());
            }
        }

        // Estilo para encabezados
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Crear encabezados
        Row headerRow = sheet.createRow(0);
        int colIndex = 0;
        for (String column : allColumns) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(colIndex++);
            cell.setCellValue(column);
            cell.setCellStyle(headerStyle);
        }

        // Crear filas de datos
        int rowIndex = 1;
        for (FileRecord record : records) {
            Row row = sheet.createRow(rowIndex++);
            colIndex = 0;

            for (String column : allColumns) {
                org.apache.poi.ss.usermodel.Cell cell = row.createCell(colIndex++);
                if (column.equals("ID")) {
                    cell.setCellValue(record.getId());
                } else {
                    String value = record.getData() != null ? record.getData().get(column) : "";
                    cell.setCellValue(value != null ? value : "");
                }
            }
        }

        // Auto-ajustar columnas
        for (int i = 0; i < allColumns.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        workbook.write(baos);
        workbook.close();
        return baos.toByteArray();
    }

    private byte[] generateLivejoyTutoraExcel(List<FileRecord> records, double porcentaje1, double porcentaje2) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("LIVEJOY");

        // Filtrar solo registros de LIVEJOY
        List<FileRecord> livejoyRecords = records.stream()
            .filter(r -> isRecordFromSheet(r, "LIVEJOY"))
            .toList();

        if (livejoyRecords.isEmpty()) {
            Row row = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Cell cell = row.createCell(0);
            cell.setCellValue("No hay registros de LIVEJOY para exportar");
            workbook.write(baos);
            workbook.close();
            return baos.toByteArray();
        }

        // Estilo para encabezados
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Crear encabezados según la especificación
        Row headerRow = sheet.createRow(0);
        String[] headers = {"ID LIVEJOY", "Usuario", "Email", "Puntos", "Monto Dólares", "Nombre Streamers", "Tutora"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Crear filas de datos
        int rowIndex = 1;
        double totalTutora = 0.0;
        for (FileRecord record : livejoyRecords) {
            Row row = sheet.createRow(rowIndex++);
            
            // ID LIVEJOY
            row.createCell(0).setCellValue(record.getId());
            
            // Usuario (del CSV)
            String usuario = getFieldValue(record, "CSV_Nombre", "CSV_Usuario", "CSV_Nombre Completo");
            row.createCell(1).setCellValue(usuario);
            
            // Email (del CSV)
            String email = getFieldValue(record, "CSV_Correo", "CSV_Email");
            row.createCell(2).setCellValue(email);
            
            // Puntos (del CSV)
            String puntos = getFieldValue(record, "CSV_Puntos Ganados por Usuaria", "CSV_Puntos");
            row.createCell(3).setCellValue(puntos);
            
            // Monto Dólares / Ingresos (del CSV)
            String ingresos = getFieldValue(record, "CSV_Ingresos", "CSV_Monto Dólares", "CSV_Monto Dolares");
            row.createCell(4).setCellValue(ingresos);
            
            // Nombre Streamers (del Excel)
            String nombreCompleto = getFieldValue(record, "Excel_Nombre Completo", "Excel_Nombre");
            row.createCell(5).setCellValue(nombreCompleto);
            
            // Tutora (Calculado: Ingresos * porcentaje1 * porcentaje2)
            double tutoraValue = 0.0;
            try {
                if (ingresos != null && !ingresos.trim().isEmpty()) {
                    double ingresosNum = Double.parseDouble(ingresos.replace(",", ""));
                    tutoraValue = ingresosNum * (porcentaje1 / 100.0) * (porcentaje2 / 100.0);
                }
            } catch (NumberFormatException e) {
                // Si no se puede parsear, dejar en 0
            }
            row.createCell(6).setCellValue(String.format("%.2f", tutoraValue));
            totalTutora += tutoraValue;
        }

        // Agregar fila de TOTAL
        CellStyle totalStyle = workbook.createCellStyle();
        Font totalFont = workbook.createFont();
        totalFont.setBold(true);
        totalStyle.setFont(totalFont);
        totalStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row totalRow = sheet.createRow(rowIndex);
        org.apache.poi.ss.usermodel.Cell totalLabelCell = totalRow.createCell(0);
        totalLabelCell.setCellValue("TOTAL");
        totalLabelCell.setCellStyle(totalStyle);
        
        org.apache.poi.ss.usermodel.Cell totalValueCell = totalRow.createCell(6);
        totalValueCell.setCellValue(String.format("%.2f", totalTutora));
        totalValueCell.setCellStyle(totalStyle);

        // Auto-ajustar columnas
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        workbook.write(baos);
        workbook.close();
        return baos.toByteArray();
    }

    private byte[] generateSalsaResumenExcel(List<FileRecord> records, double porcentajeDescuento) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("SALSA");

        // Filtrar solo registros cuya hoja de Excel sea SALSA
        List<FileRecord> salsaRecords = records.stream()
            .filter(r -> isRecordFromSheet(r, "SALSA"))
            .toList();

        if (salsaRecords.isEmpty()) {
            Row row = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Cell cell = row.createCell(0);
            cell.setCellValue("No hay registros de SALSA para exportar");
            workbook.write(baos);
            workbook.close();
            return baos.toByteArray();
        }

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Encabezados con nueva columna Tutora
        String[] headers = { "ID", "Nombre", "Total Coins", "Tutora" };
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Calcular porcentaje a retener (si descuento es 60%, se retiene 40%)
        double porcentajeRetener = (100.0 - porcentajeDescuento) / 100.0;

        int rowIndex = 1;
        double totalTutora = 0.0;
        for (FileRecord record : salsaRecords) {
            // Obtener Bono de Agencia $
            String bonoAgencia = getFieldValue(record, "CSV_Bono de Agencia $", "Bono de Agencia $", "CSV_Bono de Agencia");
            Double bonoAgenciaNum = parseOptionalDouble(bonoAgencia);

            // Obtener Bonus Top 100 (o Bonus), incluyendo variante con $
            String bonusTop100 = getFieldValue(record, "CSV_Bonus Top 100$", "CSV_Bonus Top 100", "Bonus Top 100", "CSV_Bonus", "Bonus");
            Double bonusTop100Num = parseOptionalDouble(bonusTop100);

            // Excluir solo cuando ambos valores no aportan al cálculo (<= 0 o vacíos)
            double bonoAgenciaValue = bonoAgenciaNum != null ? bonoAgenciaNum : 0.0;
            double bonusTop100Value = bonusTop100Num != null ? bonusTop100Num : 0.0;
            if (bonoAgenciaValue <= 0.0 && bonusTop100Value <= 0.0) {
                continue;
            }

            Row row = sheet.createRow(rowIndex++);

            // ID <- CSV: ID
            row.createCell(0).setCellValue(record.getId() != null ? record.getId() : "");

            // Nombre <- CSV: Nombre Completo
            String nombre = getFieldValue(record, "CSV_Nombre Completo", "CSV_Nombre");
            row.createCell(1).setCellValue(nombre);

            // Total Coins <- CSV: Total Monedas
            String totalCoins = getFieldValue(record, "CSV_Total de Monedas", "CSV_Total Monedas", "CSV_Total Coins");
            row.createCell(2).setCellValue(totalCoins);

            // Tutora <- Cálculo: (Bono de Agencia - descuento%) (ya incluye el bono top)
            double tutora = bonoAgenciaValue * porcentajeRetener;

            row.createCell(3).setCellValue(String.format("%.2f", tutora));
            totalTutora += tutora;
        }

        // Agregar fila de TOTAL
        CellStyle totalStyle = workbook.createCellStyle();
        Font totalFont = workbook.createFont();
        totalFont.setBold(true);
        totalStyle.setFont(totalFont);
        totalStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row totalRow = sheet.createRow(rowIndex);
        org.apache.poi.ss.usermodel.Cell totalLabelCell = totalRow.createCell(0);
        totalLabelCell.setCellValue("TOTAL");
        totalLabelCell.setCellStyle(totalStyle);
        
        org.apache.poi.ss.usermodel.Cell totalValueCell = totalRow.createCell(3);
        totalValueCell.setCellValue(String.format("%.2f", totalTutora));
        totalValueCell.setCellStyle(totalStyle);

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        workbook.write(baos);
        workbook.close();
        return baos.toByteArray();
    }

    private byte[] generateOliveResumenExcel(List<FileRecord> records, double oliveP1, double oliveP2) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("OLIVE");

        // Filtrar solo registros de OLIVE
        List<FileRecord> oliveRecords = records.stream()
            .filter(r -> isRecordFromSheet(r, "OLIVE"))
            .toList();

        if (oliveRecords.isEmpty()) {
            Row row = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Cell cell = row.createCell(0);
            cell.setCellValue("No hay registros de OLIVE para exportar");
            workbook.write(baos);
            workbook.close();
            return baos.toByteArray();
        }

        // Estilo para encabezados (Color Dorado)
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GOLD.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        DataFormat dataFormat = workbook.createDataFormat();
        
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(dataFormat.getFormat("#,##0.00"));

        CellStyle integerStyle = workbook.createCellStyle();
        integerStyle.setDataFormat(dataFormat.getFormat("#,##0"));

        CellStyle currencyStyle = workbook.createCellStyle();
        currencyStyle.setDataFormat(dataFormat.getFormat("$#,##0.00"));

        // Estilo para el total de tutora
        CellStyle totalTutoraStyle = workbook.createCellStyle();
        totalTutoraStyle.setDataFormat(dataFormat.getFormat("$#,##0.00"));
        Font totalFont = workbook.createFont();
        totalFont.setBold(true);
        totalTutoraStyle.setFont(totalFont);
        totalTutoraStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        totalTutoraStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Crear encabezados exactos del mockup
        Row headerRow = sheet.createRow(0);
        String[] headers = { "ID OLIVE", "PUNTOS", "MONTO DOL", "NOMBRE STREAMERS", "TUTORA" };
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIndex = 1;
        double totalPagoTutora = 0.0;

        for (FileRecord record : oliveRecords) {
            String apStr = getFieldValue(record, "CSV_Pago Agencia", "CSV_Agency Payment", "CSV_Comisión Agencia", "CSV_Bono de Agencia");
            double ap = parseOptionalDouble(apStr) != null ? parseOptionalDouble(apStr) : 0.0;
            if (ap == 0.0) {
                continue;
            }

            Row row = sheet.createRow(rowIndex++);

            // Column 0: ID OLIVE (como String para evitar notación científica)
            row.createCell(0).setCellValue(record.getId() != null ? record.getId() : "");

            // Column 1: PUNTOS (Puntos Canjeables / Redeemable Points)
            String recStr = getFieldValue(record, "CSV_Puntos Canjeables", "CSV_Redeemable Points", "CSV_Recompensa Evento", "CSV_Recompensa");
            double recompensa = parseOptionalDouble(recStr) != null ? parseOptionalDouble(recStr) : 0.0;
            org.apache.poi.ss.usermodel.Cell cellRec = row.createCell(1);
            cellRec.setCellValue(recompensa);
            cellRec.setCellStyle(integerStyle);

            // Column 2: MONTO DOL (Monto Dólares / Streamers Income / Monedas)
            String ingresosStr = getFieldValue(record, "CSV_Ingresos Streamer", "CSV_Monedas", "CSV_Ingresos", "CSV_Total Coins", "CSV_Coins");
            double monedas = parseOptionalDouble(ingresosStr) != null ? parseOptionalDouble(ingresosStr) : 0.0;
            org.apache.poi.ss.usermodel.Cell cellCoins = row.createCell(2);
            cellCoins.setCellValue(monedas);
            cellCoins.setCellStyle(currencyStyle);

            // Column 3: NOMBRE STREAMERS
            String nombre = getFieldValue(record, "CSV_Nombre Completo", "CSV_Nombre", "Excel_Nombre Completo", "Excel_Nombre");
            row.createCell(3).setCellValue(nombre != null ? nombre : "");

            // Column 4: TUTORA (Calculado)
            String brStr = getFieldValue(record, "CSV_Bonus Revenue");
            double br = parseOptionalDouble(brStr) != null ? parseOptionalDouble(brStr) : 0.0;

            double comisionBase = monedas * 0.10;
            double pagoTutora = roundTwoDecimals((comisionBase * (oliveP2 / 100.0)) + (br / 3.0));

            org.apache.poi.ss.usermodel.Cell cellTutora = row.createCell(4);
            cellTutora.setCellValue(pagoTutora);
            cellTutora.setCellStyle(currencyStyle);

            totalPagoTutora += pagoTutora;
        }

        // Agregar fila de TOTAL (solo sumatoria bajo la columna TUTORA)
        Row totalRow = sheet.createRow(rowIndex);
        org.apache.poi.ss.usermodel.Cell cellTotal = totalRow.createCell(4);
        cellTotal.setCellValue(roundTwoDecimals(totalPagoTutora));
        cellTotal.setCellStyle(totalTutoraStyle);

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        workbook.write(baos);
        workbook.close();
        return baos.toByteArray();
    }

    private Double parseOptionalDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            String cleaned = value.replace("$", "").replace(",", "").trim();
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getFieldValue(FileRecord record, String... fieldNames) {
        if (record.getData() == null) return "";
        
        for (String fieldName : fieldNames) {
            String value = record.getData().get(fieldName);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /**
     * Muestra la página del histórico de ingresos
     */
    @GetMapping("/historico")
    public String verHistorico(
            @RequestParam(value = "tab", required = false) String tab,
            @RequestParam(value = "compPA", required = false) String compPA,
            @RequestParam(value = "compPB", required = false) String compPB,
            @RequestParam(value = "descuento", defaultValue = "60") double descuento,
            @RequestParam(value = "p1", defaultValue = "12") double p1,
            @RequestParam(value = "p2", defaultValue = "40") double p2,
            @RequestParam(value = "oliveP1", defaultValue = "60") double oliveP1,
            @RequestParam(value = "oliveP2", defaultValue = "40") double oliveP2,
            Model model) {
        List<HistoricoIngreso> registros = historicoService.obtenerTodosLosRegistros();
        List<String> periodosDisponibles = historicoService.obtenerPeriodosDisponibles();

        prepararDatosHistoricoPorSheet(model, registros, periodosDisponibles, null, descuento, p1, p2, oliveP1, oliveP2);

        // Activar pestaña por defecto
        model.addAttribute("activeTab", tab != null ? tab.trim().toLowerCase() : "consolidado");
        model.addAttribute("descuento", descuento);
        model.addAttribute("p1", p1);
        model.addAttribute("p2", p2);
        model.addAttribute("oliveP1", oliveP1);
        model.addAttribute("oliveP2", oliveP2);

        // Si hay una comparación activa
        if (compPA != null && !compPA.trim().isEmpty() && compPB != null && !compPB.trim().isEmpty() && tab != null) {
            String pA = compPA.trim();
            String pB = compPB.trim();
            String activeTab = tab.trim().toLowerCase();
            model.addAttribute("compPA", pA);
            model.addAttribute("compPB", pB);
            model.addAttribute("compararActive", true);

            if ("salsa".equals(activeTab)) {
                List<SalsaComparacion> comparacion = obtenerComparacionSalsa(pA, pB, descuento);
                model.addAttribute("salsaComparacion", comparacion);
                double totalA = comparacion.stream().mapToDouble(SalsaComparacion::getTutoraA).sum();
                double totalB = comparacion.stream().mapToDouble(SalsaComparacion::getTutoraB).sum();
                model.addAttribute("compSalsaTotalA", totalA);
                model.addAttribute("compSalsaTotalB", totalB);
                model.addAttribute("compSalsaTotalDiff", totalB - totalA);
                model.addAttribute("compSalsaTotalSuma", totalA + totalB);
            } else if ("livejoy".equals(activeTab)) {
                List<LivejoyComparacion> comparacion = obtenerComparacionLivejoy(pA, pB, p1, p2);
                model.addAttribute("livejoyComparacion", comparacion);
                double totalA = comparacion.stream().mapToDouble(LivejoyComparacion::getTutoraA).sum();
                double totalB = comparacion.stream().mapToDouble(LivejoyComparacion::getTutoraB).sum();
                model.addAttribute("compLivejoyTotalA", totalA);
                model.addAttribute("compLivejoyTotalB", totalB);
                model.addAttribute("compLivejoyTotalDiff", totalB - totalA);
                model.addAttribute("compLivejoyTotalSuma", totalA + totalB);
            } else if ("olive".equals(activeTab)) {
                List<OliveComparacion> comparacion = obtenerComparacionOlive(pA, pB, oliveP1, oliveP2);
                model.addAttribute("oliveComparacion", comparacion);
                double totalA = comparacion.stream().mapToDouble(OliveComparacion::getTotalA).sum();
                double totalB = comparacion.stream().mapToDouble(OliveComparacion::getTotalB).sum();
                model.addAttribute("compOliveTotalA", totalA);
                model.addAttribute("compOliveTotalB", totalB);
                model.addAttribute("compOliveTotalDiff", totalB - totalA);
                model.addAttribute("compOliveTotalSuma", totalA + totalB);
            }
        } else {
            model.addAttribute("compararActive", false);
        }

        return "historico";
    }

    /**
     * Filtra el histórico por periodo
     */
    @GetMapping("/historico/periodo")
    public String verHistoricoPorPeriodo(
            @RequestParam("p") String periodo,
            Model model) {

        List<HistoricoIngreso> registros = historicoService.buscarPorPeriodo(periodo);
        List<String> periodosDisponibles = historicoService.obtenerPeriodosDisponibles();

        prepararDatosHistoricoPorSheet(model, registros, periodosDisponibles, periodo);
        return "historico";
    }

    /**
     * Busca en el histórico por ID o nombre
     */
    @GetMapping("/historico/buscar")
    public String buscarEnHistorico(
            @RequestParam(value = "query", required = false) String query,
            Model model) {

        List<HistoricoIngreso> registros;

        if (query == null || query.trim().isEmpty()) {
            registros = historicoService.obtenerTodosLosRegistros();
        } else {
            // Buscar primero por ID
            registros = historicoService.buscarPorIdentificacion(query.trim());

            // Si no hay resultados, buscar por nombre
            if (registros.isEmpty()) {
                registros = historicoService.buscarPorNombre(query.trim());
            }
        }

        List<String> periodosDisponibles = historicoService.obtenerPeriodosDisponibles();

        prepararDatosHistoricoPorSheet(model, registros, periodosDisponibles, null);
        model.addAttribute("query", query);
        return "historico";
    }

    /**
     * Muestra el detalle histórico de un usuario específico
     */
    @GetMapping("/historico/usuario/{id}")
    public String verDetalleUsuario(
            @PathVariable("id") String id,
            Model model) {
        
        List<HistoricoIngreso> registros = historicoService.buscarPorIdentificacion(id.trim());
        
        if (registros.isEmpty()) {
            model.addAttribute("error", "No se encontraron registros para el usuario con ID: " + id);
            return "redirect:/historico";
        }
        
        String nombreUsuario = registros.get(0).getNombreCompleto();
        
        // Calcular totales por plataforma y totales acumulados
        double totalSalsa = 0.0;
        double totalLivejoy = 0.0;
        double totalOlive = 0.0;
        double totalAcumulado = 0.0;
        int semanasCobradas = 0;
        Set<String> plataformas = new java.util.HashSet<>();
        
        List<Map<String, Object>> timeline = new ArrayList<>();
        
        for (HistoricoIngreso reg : registros) {
            String sheet = normalizeSheet(reg.getSheet());
            plataformas.add(sheet);
            
            double valor = 0.0;
            double coins = 0.0;
            if ("SALSA".equals(sheet)) {
                double ba = reg.getBonoAgencia() != null ? reg.getBonoAgencia() : 0.0;
                double bt = reg.getBonusTop100() != null ? reg.getBonusTop100() : 0.0;
                double appliedDescuento = (reg.getPorcentajeDescuento() != null) ? reg.getPorcentajeDescuento() : 60.0;
                valor = ba * (1.0 - appliedDescuento/100.0);
                coins = reg.getTotalMonedas() != null ? reg.getTotalMonedas() : 0.0;
                totalSalsa += valor;
            } else if ("LIVEJOY".equals(sheet)) {
                double ing = reg.getMonedas() != null ? reg.getMonedas() : 0.0;
                double appliedP1 = (reg.getPorcentaje1() != null) ? reg.getPorcentaje1() : 12.0;
                double appliedP2 = (reg.getPorcentaje2() != null) ? reg.getPorcentaje2() : 40.0;
                valor = ing * (appliedP1/100.0) * (appliedP2/100.0);
                coins = ing;
                totalLivejoy += valor;
            } else if ("OLIVE".equals(sheet)) {
                coins = reg.getTotalMonedas() != null ? reg.getTotalMonedas() : (reg.getMonedas() != null ? reg.getMonedas() : 0.0);
                double ba = reg.getBonoAgencia() != null ? reg.getBonoAgencia() : 0.0;
                double rec = reg.getRecompensaEvento() != null ? reg.getRecompensaEvento() : 0.0;
                valor = ba + rec;
                totalOlive += valor;
            }
            
            if (valor > 0.0) {
                semanasCobradas++;
                totalAcumulado += valor;
            }
            
            Map<String, Object> item = new HashMap<>();
            item.put("periodo", reg.getPeriodoComparacion());
            item.put("plataforma", sheet);
            item.put("monedas", coins);
            item.put("monto", valor);
            item.put("fecha", reg.getFechaRegistro());
            timeline.add(item);
        }
        
        // Ordenar timeline por período/fecha descendente
        timeline.sort((a, b) -> ((java.time.LocalDateTime) b.get("fecha")).compareTo((java.time.LocalDateTime) a.get("fecha")));
        
        model.addAttribute("usuarioId", id);
        model.addAttribute("nombreUsuario", nombreUsuario);
        model.addAttribute("plataformas", plataformas);
        model.addAttribute("totalSalsa", totalSalsa);
        model.addAttribute("totalLivejoy", totalLivejoy);
        model.addAttribute("totalOlive", totalOlive);
        model.addAttribute("totalAcumulado", totalAcumulado);
        model.addAttribute("semanasCobradas", semanasCobradas);
        model.addAttribute("timeline", timeline);
        
        return "historico-detalle-usuario";
    }

    /**
     * Descarga el histórico en formato Excel (filtrado por periodo si se
     * especifica)
     */
    @GetMapping("/historico/descargar/excel")
    public ResponseEntity<byte[]> descargarHistoricoExcel(
            @RequestParam(value = "periodo", required = false) String periodo) throws Exception {

        // Obtener registros según filtro
        List<HistoricoIngreso> registros;
        if (periodo != null && !periodo.trim().isEmpty()) {
            registros = historicoService.buscarPorPeriodo(periodo);
        } else {
            registros = historicoService.obtenerTodosLosRegistros();
        }

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Histórico de Ingresos");

        // Crear estilo para encabezado
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Encabezados
        Row headerRow = sheet.createRow(0);
        String[] headers = { "ID", "Nombre Completo", "Monedas", "Total Monedas", "Bono Agencia $",
                "Recompensa Evento $", "Semana", "País", "WhatsApp", "Fecha Registro" };

        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Datos
        int rowNum = 1;
        for (HistoricoIngreso registro : registros) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(registro.getIdentificacion());
            row.createCell(1).setCellValue(registro.getNombreCompleto());
            row.createCell(2).setCellValue(registro.getMonedas() != null ? registro.getMonedas() : 0);
            row.createCell(3).setCellValue(registro.getTotalMonedas() != null ? registro.getTotalMonedas() : 0);
            row.createCell(4).setCellValue(registro.getBonoAgencia() != null ? registro.getBonoAgencia() : 0);
            row.createCell(5).setCellValue(registro.getRecompensaEvento() != null ? registro.getRecompensaEvento() : 0);
            row.createCell(6).setCellValue(registro.getSemana() != null ? registro.getSemana().toString() : "");
            row.createCell(7).setCellValue(registro.getPais() != null ? registro.getPais() : "");
            row.createCell(8).setCellValue(registro.getWhatsapp() != null ? registro.getWhatsapp() : "");
            row.createCell(9).setCellValue(registro.getFechaRegistro().toString());
        }

        // Ajustar ancho de columnas
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        httpHeaders.setContentDispositionFormData("attachment", "historico_ingresos.xlsx");

        return ResponseEntity.ok().headers(httpHeaders).body(baos.toByteArray());
    }

    /**
     * Descarga el histórico en formato CSV (filtrado por periodo si se especifica)
     */
    @GetMapping("/historico/descargar/csv")
    public ResponseEntity<byte[]> descargarHistoricoCsv(
            @RequestParam(value = "periodo", required = false) String periodo) throws Exception {

        // Obtener registros según filtro
        List<HistoricoIngreso> registros;
        if (periodo != null && !periodo.trim().isEmpty()) {
            registros = historicoService.buscarPorPeriodo(periodo);
        } else {
            registros = historicoService.obtenerTodosLosRegistros();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8));

        // Encabezados
        writer.println(
                "ID;Nombre Completo;Monedas;Total Monedas;Bono Agencia $;Recompensa Evento $;Semana;País;WhatsApp;Fecha Registro");

        // Datos
        for (HistoricoIngreso registro : registros) {
            writer.printf("%s;%s;%s;%s;%s;%s;%s;%s;%s;%s%n",
                    registro.getIdentificacion(),
                    registro.getNombreCompleto(),
                    registro.getMonedas() != null ? registro.getMonedas() : "",
                    registro.getTotalMonedas() != null ? registro.getTotalMonedas() : "",
                    registro.getBonoAgencia() != null ? registro.getBonoAgencia() : "",
                    registro.getRecompensaEvento() != null ? registro.getRecompensaEvento() : "",
                    registro.getSemana() != null ? registro.getSemana() : "",
                    registro.getPais() != null ? registro.getPais() : "",
                    registro.getWhatsapp() != null ? registro.getWhatsapp() : "",
                    registro.getFechaRegistro());
        }

        writer.flush();
        writer.close();

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        httpHeaders.setContentDispositionFormData("attachment", "historico_ingresos.csv");

        return ResponseEntity.ok().headers(httpHeaders).body(baos.toByteArray());
    }

    /**
     * Descarga un Excel del período seleccionado en tres hojas (SALSA, LIVEJOY, OLIVE),
     * excluyendo las columnas Periodo y Fecha Registro.
     */
    @GetMapping("/historico/descargar/excel-periodo")
    public ResponseEntity<byte[]> descargarExcelPeriodoSeleccionado(
            @RequestParam("periodo") String periodo,
            @RequestParam(value = "descuento", defaultValue = "60") double descuento,
            @RequestParam(value = "p1", defaultValue = "12") double p1,
            @RequestParam(value = "p2", defaultValue = "40") double p2,
            @RequestParam(value = "oliveP1", defaultValue = "60") double oliveP1,
            @RequestParam(value = "oliveP2", defaultValue = "40") double oliveP2) throws Exception {

        if (periodo == null || periodo.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<HistoricoIngreso> registros = historicoService.buscarPorPeriodo(periodo.trim());

        Workbook workbook = new XSSFWorkbook();
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        DataFormat dataFormat = workbook.createDataFormat();
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(dataFormat.getFormat("#,##0.00"));

        CellStyle totalLabelStyle = workbook.createCellStyle();
        Font totalFont = workbook.createFont();
        totalFont.setBold(true);
        totalLabelStyle.setFont(totalFont);
        totalLabelStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        totalLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle totalNumberStyle = workbook.createCellStyle();
        totalNumberStyle.cloneStyleFrom(numberStyle);
        totalNumberStyle.setFont(totalFont);
        totalNumberStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        totalNumberStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Sheet salsaSheet = workbook.createSheet("SALSA");
        String[] salsaHeaders = { "ID", "Nombre", "Total Coins", "Tutora", "Tutora (Archivo)" };
        Row salsaHeaderRow = salsaSheet.createRow(0);
        for (int i = 0; i < salsaHeaders.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = salsaHeaderRow.createCell(i);
            cell.setCellValue(salsaHeaders[i]);
            cell.setCellStyle(headerStyle);
        }

        Sheet livejoySheet = workbook.createSheet("LIVEJOY");
        String[] livejoyHeaders = { "ID LIVEJOY", "Usuario", "Monto Dólares", "Nombre Streamers", "Tutora", "Tutora (Archivo)" };
        Row livejoyHeaderRow = livejoySheet.createRow(0);
        for (int i = 0; i < livejoyHeaders.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = livejoyHeaderRow.createCell(i);
            cell.setCellValue(livejoyHeaders[i]);
            cell.setCellStyle(headerStyle);
        }

        Sheet oliveSheet = workbook.createSheet("OLIVE");
        String[] oliveHeaders = { "ID", "Nombre", "MONTO DOLARES", "Nivel", "Puntos Canjeables", "Pago Agencia", "Bonus Revenue", "Pago Tutora", "Tutora (Archivo)" };
        Row oliveHeaderRow = oliveSheet.createRow(0);
        for (int i = 0; i < oliveHeaders.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = oliveHeaderRow.createCell(i);
            cell.setCellValue(oliveHeaders[i]);
            cell.setCellStyle(headerStyle);
        }

        int salsaRowNum = 1;
        int livejoyRowNum = 1;
        int oliveRowNum = 1;

        double salsaTotalCoins = 0.0;
        double salsaTotalTutora = 0.0;
        double livejoyTotalMonto = 0.0;
        double livejoyTotalTutora = 0.0;
        double oliveTotalMonedas = 0.0;
        double oliveTotalRecompensa = 0.0;
        double oliveTotalBonoAgencia = 0.0;
        double oliveTotalBonusRevenue = 0.0;
        double oliveTotalPagoTutora = 0.0;

        for (HistoricoIngreso registro : registros) {
            String sheet = normalizeSheet(registro.getSheet());

            if ("SALSA".equals(sheet)) {
                Row row = salsaSheet.createRow(salsaRowNum++);
                double bonoAgencia = registro.getBonoAgencia() != null ? registro.getBonoAgencia() : 0.0;
                double bonusTop100 = registro.getBonusTop100() != null ? registro.getBonusTop100() : 0.0;
                double appliedDescuento = (registro.getPorcentajeDescuento() != null) ? registro.getPorcentajeDescuento() : descuento;
                double tutora = roundTwoDecimals(bonoAgencia * (1.0 - appliedDescuento/100.0));
                double totalCoins = roundTwoDecimals(registro.getTotalMonedas() != null ? registro.getTotalMonedas() : 0.0);

                row.createCell(0).setCellValue(registro.getIdentificacion() != null ? registro.getIdentificacion() : "");
                row.createCell(1).setCellValue(registro.getNombreCompleto() != null ? registro.getNombreCompleto() : "");
                row.createCell(2).setCellValue(totalCoins);
                row.getCell(2).setCellStyle(numberStyle);
                row.createCell(3).setCellValue(tutora);
                row.getCell(3).setCellStyle(numberStyle);
                row.createCell(4).setCellValue(registro.getNombreTutora() != null ? registro.getNombreTutora() : "-");

                salsaTotalCoins += totalCoins;
                salsaTotalTutora += tutora;
            } else if ("LIVEJOY".equals(sheet)) {
                Row row = livejoySheet.createRow(livejoyRowNum++);
                double ingresos = roundTwoDecimals(registro.getMonedas() != null ? registro.getMonedas() : 0.0);
                double appliedP1 = (registro.getPorcentaje1() != null) ? registro.getPorcentaje1() : p1;
                double appliedP2 = (registro.getPorcentaje2() != null) ? registro.getPorcentaje2() : p2;
                double tutora = roundTwoDecimals(ingresos * (appliedP1/100.0) * (appliedP2/100.0));

                row.createCell(0).setCellValue(registro.getIdentificacion() != null ? registro.getIdentificacion() : "");
                row.createCell(1).setCellValue(registro.getNombreCompleto() != null ? registro.getNombreCompleto() : "");
                row.createCell(2).setCellValue(ingresos);
                row.getCell(2).setCellStyle(numberStyle);
                row.createCell(3).setCellValue(registro.getNombreCompleto() != null ? registro.getNombreCompleto() : "");
                row.createCell(4).setCellValue(tutora);
                row.getCell(4).setCellStyle(numberStyle);
                row.createCell(5).setCellValue(registro.getNombreTutora() != null ? registro.getNombreTutora() : "-");

                livejoyTotalMonto += ingresos;
                livejoyTotalTutora += tutora;
            } else if ("OLIVE".equals(sheet)) {
                Row row = oliveSheet.createRow(oliveRowNum++);

                double monedas = roundTwoDecimals(registro.getMonedas() != null ? registro.getMonedas() : 0.0);
                String nivel = registro.getNivel() != null ? registro.getNivel() : "-";
                double recompensa = roundTwoDecimals(registro.getRecompensaEvento() != null ? registro.getRecompensaEvento() : 0.0);
                
                double ap = registro.getBonoAgencia() != null ? registro.getBonoAgencia() : 0.0;
                double br = registro.getBonusRevenue() != null ? registro.getBonusRevenue() : 0.0;
                double p1Val = registro.getPorcentaje1() != null ? registro.getPorcentaje1() : oliveP1;
                double p2Val = registro.getPorcentaje2() != null ? registro.getPorcentaje2() : oliveP2;

                double comisionBase = monedas * 0.10;
                double pagoTutora = roundTwoDecimals((comisionBase * (p2Val / 100.0)) + (br / 3.0));

                row.createCell(0).setCellValue(registro.getIdentificacion() != null ? registro.getIdentificacion() : "");
                row.createCell(1).setCellValue(registro.getNombreCompleto() != null ? registro.getNombreCompleto() : "");
                
                org.apache.poi.ss.usermodel.Cell cellCoins = row.createCell(2);
                cellCoins.setCellValue(monedas);
                cellCoins.setCellStyle(numberStyle);
                
                row.createCell(3).setCellValue(nivel);
                
                org.apache.poi.ss.usermodel.Cell cellRec = row.createCell(4);
                cellRec.setCellValue(recompensa);
                cellRec.setCellStyle(numberStyle);

                org.apache.poi.ss.usermodel.Cell cellAp = row.createCell(5);
                cellAp.setCellValue(ap);
                cellAp.setCellStyle(numberStyle);

                org.apache.poi.ss.usermodel.Cell cellBr = row.createCell(6);
                cellBr.setCellValue(br);
                cellBr.setCellStyle(numberStyle);

                org.apache.poi.ss.usermodel.Cell cellTutora = row.createCell(7);
                cellTutora.setCellValue(pagoTutora);
                cellTutora.setCellStyle(numberStyle);

                row.createCell(8).setCellValue(registro.getNombreTutora() != null ? registro.getNombreTutora() : "-");

                oliveTotalMonedas += monedas;
                oliveTotalRecompensa += recompensa;
                oliveTotalBonoAgencia += ap;
                oliveTotalBonusRevenue += br;
                oliveTotalPagoTutora += pagoTutora;
            }
        }

        Row salsaTotalRow = salsaSheet.createRow(salsaRowNum);
        salsaTotalRow.createCell(1).setCellValue("TOTAL");
        salsaTotalRow.getCell(1).setCellStyle(totalLabelStyle);
        salsaTotalRow.createCell(2).setCellValue(roundTwoDecimals(salsaTotalCoins));
        salsaTotalRow.getCell(2).setCellStyle(totalNumberStyle);
        salsaTotalRow.createCell(3).setCellValue(roundTwoDecimals(salsaTotalTutora));
        salsaTotalRow.getCell(3).setCellStyle(totalNumberStyle);

        Row livejoyTotalRow = livejoySheet.createRow(livejoyRowNum);
        livejoyTotalRow.createCell(1).setCellValue("TOTAL");
        livejoyTotalRow.getCell(1).setCellStyle(totalLabelStyle);
        livejoyTotalRow.createCell(2).setCellValue(roundTwoDecimals(livejoyTotalMonto));
        livejoyTotalRow.getCell(2).setCellStyle(totalNumberStyle);
        livejoyTotalRow.createCell(4).setCellValue(roundTwoDecimals(livejoyTotalTutora));
        livejoyTotalRow.getCell(4).setCellStyle(totalNumberStyle);

        Row oliveTotalRow = oliveSheet.createRow(oliveRowNum);
        oliveTotalRow.createCell(1).setCellValue("TOTAL");
        oliveTotalRow.getCell(1).setCellStyle(totalLabelStyle);
        
        oliveTotalRow.createCell(2).setCellValue(roundTwoDecimals(oliveTotalMonedas));
        oliveTotalRow.getCell(2).setCellStyle(totalNumberStyle);
        
        oliveTotalRow.createCell(3).setCellValue("");
        oliveTotalRow.getCell(3).setCellStyle(totalLabelStyle);
        
        oliveTotalRow.createCell(4).setCellValue(roundTwoDecimals(oliveTotalRecompensa));
        oliveTotalRow.getCell(4).setCellStyle(totalNumberStyle);

        oliveTotalRow.createCell(5).setCellValue(roundTwoDecimals(oliveTotalBonoAgencia));
        oliveTotalRow.getCell(5).setCellStyle(totalNumberStyle);

        oliveTotalRow.createCell(6).setCellValue(roundTwoDecimals(oliveTotalBonusRevenue));
        oliveTotalRow.getCell(6).setCellStyle(totalNumberStyle);

        oliveTotalRow.createCell(7).setCellValue(roundTwoDecimals(oliveTotalPagoTutora));
        oliveTotalRow.getCell(7).setCellStyle(totalNumberStyle);
        
        oliveTotalRow.createCell(8).setCellValue("");
        oliveTotalRow.getCell(8).setCellStyle(totalLabelStyle);

        for (int i = 0; i < salsaHeaders.length; i++) {
            salsaSheet.autoSizeColumn(i);
        }
        for (int i = 0; i < livejoyHeaders.length; i++) {
            livejoySheet.autoSizeColumn(i);
        }
        for (int i = 0; i < oliveHeaders.length; i++) {
            oliveSheet.autoSizeColumn(i);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        String safePeriodo = periodo.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_");
        String fileName = safePeriodo.isEmpty() ? "periodo.xlsx" : safePeriodo + ".xlsx";

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        httpHeaders.setContentDispositionFormData("attachment", fileName);

        return ResponseEntity.ok().headers(httpHeaders).body(baos.toByteArray());
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    /**
     * Muestra la página para comparar archivos con el histórico
     */
    @GetMapping("/historico/comparar")
    public String mostrarComparacionHistorico() {
        return "comparar-historico";
    }

    /**
     * Compara archivos subidos con el histórico
     */
    @PostMapping("/historico/comparar")
    public String compararConHistorico(
            @RequestParam("files") MultipartFile[] files,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (files == null || files.length == 0) {
            redirectAttributes.addFlashAttribute("error", "Por favor, selecciona al menos un archivo.");
            return "redirect:/historico/comparar";
        }

        try {
            // Obtener todos los IDs del histórico
            List<HistoricoIngreso> historico = historicoService.obtenerTodosLosRegistros();
            Set<String> historicIds = new java.util.HashSet<>();
            Map<String, HistoricoIngreso> historicoMap = new java.util.HashMap<>();

            for (HistoricoIngreso registro : historico) {
                String id = fileComparisonService.cleanId(registro.getIdentificacion());
                historicIds.add(id);
                historicoMap.put(id, registro);
            }

            // Extraer IDs de los archivos subidos
            Set<String> uploadedIds = new java.util.HashSet<>();
            Map<String, FileRecord> uploadedRecords = new java.util.HashMap<>();

            for (MultipartFile file : files) {
                if (file.isEmpty())
                    continue;

                String filename = file.getOriginalFilename();
                if (filename == null)
                    continue;

                Map<String, FileRecord> records;
                if (filename.toLowerCase().endsWith(".csv")) {
                    records = fileComparisonService.extractRecordsFromCsv(file);
                } else if (filename.toLowerCase().endsWith(".xlsx") || filename.toLowerCase().endsWith(".xls")) {
                    records = fileComparisonService.extractRecordsFromExcel(file);
                } else {
                    continue;
                }

                uploadedIds.addAll(records.keySet());
                uploadedRecords.putAll(records);
            }

            // Comparar
            Set<String> matchingIds = new java.util.HashSet<>(uploadedIds);
            matchingIds.retainAll(historicIds);

            Set<String> onlyInUploaded = new java.util.HashSet<>(uploadedIds);
            onlyInUploaded.removeAll(historicIds);

            Set<String> onlyInHistorico = new java.util.HashSet<>(historicIds);
            onlyInHistorico.removeAll(uploadedIds);

            // Crear listas ordenadas
            List<String> matchingList = new java.util.ArrayList<>(matchingIds);
            List<String> onlyInUploadedList = new java.util.ArrayList<>(onlyInUploaded);
            List<String> onlyInHistoricoList = new java.util.ArrayList<>(onlyInHistorico);

            java.util.Collections.sort(matchingList);
            java.util.Collections.sort(onlyInUploadedList);
            java.util.Collections.sort(onlyInHistoricoList);

            // Crear registros combinados para los coincidentes
            List<FileRecord> matchingRecords = new java.util.ArrayList<>();
            for (String id : matchingList) {
                FileRecord uploadedRecord = uploadedRecords.get(id);
                HistoricoIngreso historicoRecord = historicoMap.get(id);

                Map<String, String> combinedData = new java.util.LinkedHashMap<>();

                if (uploadedRecord != null && uploadedRecord.getData() != null) {
                    uploadedRecord.getData().forEach((key, value) -> combinedData.put("Archivo_" + key, value));
                }

                if (historicoRecord != null) {
                    combinedData.put("Historico_Nombre", historicoRecord.getNombreCompleto());
                    combinedData.put("Historico_Total_Monedas",
                            historicoRecord.getTotalMonedas() != null ? historicoRecord.getTotalMonedas().toString()
                                    : "");
                    combinedData.put("Historico_Bono_Agencia",
                            historicoRecord.getBonoAgencia() != null ? historicoRecord.getBonoAgencia().toString()
                                    : "");
                    combinedData.put("Historico_Recompensa",
                            historicoRecord.getRecompensaEvento() != null
                                    ? historicoRecord.getRecompensaEvento().toString()
                                    : "");
                    combinedData.put("Historico_Pais",
                            historicoRecord.getPais() != null ? historicoRecord.getPais() : "");
                    combinedData.put("Historico_Fecha", historicoRecord.getFechaRegistro().toString());
                }

                FileRecord matchingRecord = new FileRecord(id, combinedData, "Coincidente");
                matchingRecords.add(matchingRecord);
            }

            ComparisonResult result = new ComparisonResult(matchingList, onlyInUploadedList, onlyInHistoricoList);
            result.setMatchingRecords(matchingRecords);

            model.addAttribute("result", result);
            model.addAttribute("csvFileName", "Archivos subidos");
            model.addAttribute("excelFileName", "Histórico de Ingresos");
            model.addAttribute("comparisonResult", result);

            return "result";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error al procesar los archivos: "
                            + (e.getMessage() != null ? e.getMessage() : "Verifica el formato"));
            return "redirect:/historico/comparar";
        }
    }

    /**
     * Elimina todos los registros de un período específico del histórico
     */
    @DeleteMapping("/historico/eliminar-periodo")
    public ResponseEntity<?> eliminarPeriodo(@RequestBody Map<String, String> request) {
        String periodo = request.get("periodo");

        if (periodo == null || periodo.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("El período no puede estar vacío");
        }

        try {
            historicoService.eliminarPorPeriodo(periodo.trim());
            return ResponseEntity.ok().body("Período eliminado exitosamente");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al eliminar el período: " + e.getMessage());
        }
    }

    /**
     * Limpia completamente el histórico (elimina todos los registros)
     */
    @DeleteMapping("/historico/limpiar-todo")
    public ResponseEntity<?> limpiarTodoHistorico() {
        try {
            historicoService.limpiarHistorico();
            return ResponseEntity.ok().body("Histórico limpiado exitosamente");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al limpiar el histórico: " + e.getMessage());
        }
    }

    /**
     * Actualiza los porcentajes para el histórico de ingresos a través de una petición AJAX.
     */
    @PostMapping("/historico/actualizar-porcentajes-ajax")
    public ResponseEntity<?> actualizarPorcentajesAjax(@RequestBody Map<String, Object> payload) {
        try {
            String plataforma = (String) payload.get("plataforma");
            String periodo = (String) payload.get("periodo");
            
            Double descuento = null;
            if (payload.containsKey("descuento") && payload.get("descuento") != null) {
                descuento = Double.valueOf(payload.get("descuento").toString());
            }
            
            Double p1 = null;
            if (payload.containsKey("p1") && payload.get("p1") != null) {
                p1 = Double.valueOf(payload.get("p1").toString());
            }
            
            Double p2 = null;
            if (payload.containsKey("p2") && payload.get("p2") != null) {
                p2 = Double.valueOf(payload.get("p2").toString());
            }
            
            if ("all".equalsIgnoreCase(periodo)) {
                List<String> periodos = historicoService.obtenerPeriodosDisponibles();
                for (String p : periodos) {
                    historicoService.actualizarPorcentajes(p, plataforma, descuento, p1, p2);
                }
            } else {
                historicoService.actualizarPorcentajes(periodo, plataforma, descuento, p1, p2);
            }
            
            return ResponseEntity.ok().body("Porcentajes actualizados exitosamente");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    private void prepararDatosHistoricoPorSheet(Model model, List<HistoricoIngreso> registros, 
                                                 List<String> periodosDisponibles, String periodoSeleccionado) {
        prepararDatosHistoricoPorSheet(model, registros, periodosDisponibles, periodoSeleccionado, 60.0, 12.0, 40.0, 60.0, 40.0);
    }

    /**
     * Prepara los datos del histórico agrupados por sheet (SALSA, LIVEJOY, OLIVE)
     * con cálculo de Tutora para SALSA y LIVEJOY
     */
    private void prepararDatosHistoricoPorSheet(Model model, List<HistoricoIngreso> registros, 
                                                 List<String> periodosDisponibles, String periodoSeleccionado,
                                                 double descuento, double p1, double p2, double oliveP1, double oliveP2) {
        // Agrupar registros por sheet
        List<Map<String, Object>> salsaRecords = new ArrayList<>();
        List<Map<String, Object>> livejoyRecords = new ArrayList<>();
        List<Map<String, Object>> oliveRecords = new ArrayList<>();
        
        for (HistoricoIngreso registro : registros) {
            String sheet = normalizeSheet(registro.getSheet());
            
            if ("SALSA".equals(sheet)) {
                Map<String, Object> data = new HashMap<>();
                data.put("id", registro.getIdentificacion());
                data.put("nombre", registro.getNombreCompleto());
                data.put("totalCoins", registro.getTotalMonedas() != null ? registro.getTotalMonedas() : 0.0);
                data.put("periodo", registro.getPeriodoComparacion());
                data.put("fechaRegistro", registro.getFechaRegistro());
                data.put("nombreTutora", registro.getNombreTutora() != null ? registro.getNombreTutora() : "-");
                
                // Calcular Tutora: (Bono de Agencia - descuento%) + (Bonus Top 100 - descuento%)
                double bonoAgencia = registro.getBonoAgencia() != null ? registro.getBonoAgencia() : 0.0;
                double bonusTop100 = registro.getBonusTop100() != null ? registro.getBonusTop100() : 0.0;
                double appliedDescuento = (registro.getPorcentajeDescuento() != null) ? registro.getPorcentajeDescuento() : descuento;
                double tutora = bonoAgencia * (1.0 - appliedDescuento/100.0);
                data.put("tutora", tutora);
                
                salsaRecords.add(data);
            } else if ("LIVEJOY".equals(sheet)) {
                Map<String, Object> data = new HashMap<>();
                data.put("id", registro.getIdentificacion());
                data.put("usuario", registro.getNombreCompleto());
                data.put("email", ""); // No disponible en histórico actual
                data.put("puntos", ""); // No disponible en histórico actual
                data.put("montoDolares", registro.getMonedas() != null ? registro.getMonedas() : 0.0);
                data.put("nombreStreamers", registro.getNombreCompleto());
                data.put("periodo", registro.getPeriodoComparacion());
                data.put("fechaRegistro", registro.getFechaRegistro());
                data.put("nombreTutora", registro.getNombreTutora() != null ? registro.getNombreTutora() : "-");
                
                // Calcular Tutora: Ingresos * p1% * p2%
                double ingresos = registro.getMonedas() != null ? registro.getMonedas() : 0.0;
                double appliedP1 = (registro.getPorcentaje1() != null) ? registro.getPorcentaje1() : p1;
                double appliedP2 = (registro.getPorcentaje2() != null) ? registro.getPorcentaje2() : p2;
                double tutora = ingresos * (appliedP1/100.0) * (appliedP2/100.0);
                data.put("tutora", tutora);
                
                livejoyRecords.add(data);
            } else if ("OLIVE".equals(sheet)) {
                Map<String, Object> data = new HashMap<>();
                data.put("id", registro.getIdentificacion());
                data.put("nombre", registro.getNombreCompleto());
                data.put("monedas", registro.getMonedas() != null ? registro.getMonedas() : 0.0);
                data.put("nivel", registro.getNivel() != null ? registro.getNivel() : "-");
                data.put("recompensa", registro.getRecompensaEvento() != null ? registro.getRecompensaEvento() : 0.0);
                data.put("periodo", registro.getPeriodoComparacion());
                data.put("fechaRegistro", registro.getFechaRegistro());
                data.put("nombreTutora", registro.getNombreTutora() != null ? registro.getNombreTutora() : "-");

                double ap = registro.getBonoAgencia() != null ? registro.getBonoAgencia() : 0.0;
                double br = registro.getBonusRevenue() != null ? registro.getBonusRevenue() : 0.0;
                double p1Val = registro.getPorcentaje1() != null ? registro.getPorcentaje1() : oliveP1;
                double p2Val = registro.getPorcentaje2() != null ? registro.getPorcentaje2() : oliveP2;

                double comisionBase = (registro.getMonedas() != null ? registro.getMonedas() : 0.0) * 0.10;
                double pagoChica = comisionBase * (p1Val / 100.0);
                double pagoTutora = (comisionBase * (p2Val / 100.0)) + (br / 3.0);

                data.put("pagoAgencia", ap);
                data.put("bonusRevenue", br);
                data.put("pagoChica", pagoChica);
                data.put("pagoTutora", pagoTutora);
                data.put("tutora", pagoTutora);
                
                oliveRecords.add(data);
            }
        }
        
        // Calcular totales de Tutora
        double totalTutoraSalsa = salsaRecords.stream()
                .mapToDouble(r -> (Double) r.get("tutora"))
                .sum();
        double totalTutoraLivejoy = livejoyRecords.stream()
                .mapToDouble(r -> (Double) r.get("tutora"))
                .sum();
        double totalTutoraOlive = oliveRecords.stream()
                .mapToDouble(r -> (Double) r.get("tutora"))
                .sum();
        
        // Extraer períodos únicos por hoja
        List<String> periodosSalsa = salsaRecords.stream()
                .map(r -> (String) r.get("periodo"))
                .filter(p -> p != null && !p.isEmpty())
                .distinct()
                .sorted()
                .toList();
        
        List<String> periodosLivejoy = livejoyRecords.stream()
                .map(r -> (String) r.get("periodo"))
                .filter(p -> p != null && !p.isEmpty())
                .distinct()
                .sorted()
                .toList();
        
        List<String> periodosOlive = oliveRecords.stream()
                .map(r -> (String) r.get("periodo"))
                .filter(p -> p != null && !p.isEmpty())
                .distinct()
                .sorted()
                .toList();
        
        model.addAttribute("salsaRecords", salsaRecords);
        model.addAttribute("livejoyRecords", livejoyRecords);
        model.addAttribute("oliveRecords", oliveRecords);
        model.addAttribute("totalTutoraSalsa", totalTutoraSalsa);
        model.addAttribute("totalTutoraLivejoy", totalTutoraLivejoy);
        model.addAttribute("totalTutoraOlive", totalTutoraOlive);
        model.addAttribute("periodosSalsa", periodosSalsa);
        model.addAttribute("periodosLivejoy", periodosLivejoy);
        model.addAttribute("periodosOlive", periodosOlive);
        model.addAttribute("registros", registros);
        model.addAttribute("periodosDisponibles", periodosDisponibles);
        model.addAttribute("periodoSeleccionado", periodoSeleccionado);

        // Consolidado General
        List<String> periodosOrdenados = new ArrayList<>(periodosDisponibles);
        java.util.Collections.reverse(periodosOrdenados);

        Map<String, ConsolidadoPersona> consolidadoMap = new java.util.LinkedHashMap<>();
        for (HistoricoIngreso registro : registros) {
            String id = registro.getIdentificacion();
            if (id == null || id.trim().isEmpty()) continue;
            
            String normalizedId = id.trim();
            ConsolidadoPersona persona = consolidadoMap.computeIfAbsent(normalizedId, k -> {
                String nombre = registro.getNombreCompleto();
                return new ConsolidadoPersona(normalizedId, nombre != null ? nombre : "Sin nombre");
            });
            
            if ("Sin nombre".equals(persona.getNombreCompleto()) && registro.getNombreCompleto() != null && !registro.getNombreCompleto().isEmpty()) {
                persona.nombreCompleto = registro.getNombreCompleto();
            }

            String sheet = normalizeSheet(registro.getSheet());
            persona.agregarIngreso(
                registro.getPeriodoComparacion(),
                sheet,
                registro.getMonedas(),
                registro.getTotalMonedas(),
                registro.getBonoAgencia(),
                registro.getBonusTop100(),
                registro.getRecompensaEvento(),
                registro.getPorcentajeDescuento(),
                registro.getPorcentaje1(),
                registro.getPorcentaje2()
            );
        }

        List<ConsolidadoPersona> consolidadoRecords = new ArrayList<>(consolidadoMap.values());
        consolidadoRecords.sort((a, b) -> Double.compare(b.getTotalAcumulado(), a.getTotalAcumulado()));

        model.addAttribute("consolidadoRecords", consolidadoRecords);
        model.addAttribute("periodosOrdenados", periodosOrdenados);
    }

    /**
     * Descarga el Consolidado General en formato Excel
     */
    @GetMapping("/historico/descargar/excel-consolidado")
    public ResponseEntity<byte[]> descargarExcelConsolidado() throws Exception {
        List<HistoricoIngreso> registros = historicoService.obtenerTodosLosRegistros();
        List<String> periodosDisponibles = historicoService.obtenerPeriodosDisponibles();
        List<String> periodosOrdenados = new ArrayList<>(periodosDisponibles);
        java.util.Collections.reverse(periodosOrdenados);

        Map<String, ConsolidadoPersona> consolidadoMap = new java.util.LinkedHashMap<>();
        for (HistoricoIngreso registro : registros) {
            String id = registro.getIdentificacion();
            if (id == null || id.trim().isEmpty()) continue;
            String normalizedId = id.trim();
            ConsolidadoPersona persona = consolidadoMap.computeIfAbsent(normalizedId, k -> {
                String nombre = registro.getNombreCompleto();
                return new ConsolidadoPersona(normalizedId, nombre != null ? nombre : "Sin nombre");
            });
            if ("Sin nombre".equals(persona.getNombreCompleto()) && registro.getNombreCompleto() != null && !registro.getNombreCompleto().isEmpty()) {
                persona.nombreCompleto = registro.getNombreCompleto();
            }
            persona.agregarIngreso(
                registro.getPeriodoComparacion(),
                normalizeSheet(registro.getSheet()),
                registro.getMonedas(),
                registro.getTotalMonedas(),
                registro.getBonoAgencia(),
                registro.getBonusTop100(),
                registro.getRecompensaEvento(),
                registro.getPorcentajeDescuento(),
                registro.getPorcentaje1(),
                registro.getPorcentaje2()
            );
        }

        List<ConsolidadoPersona> consolidadoRecords = new ArrayList<>(consolidadoMap.values());
        consolidadoRecords.sort((a, b) -> Double.compare(b.getTotalAcumulado(), a.getTotalAcumulado()));

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Consolidado General");

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        DataFormat dataFormat = workbook.createDataFormat();
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(dataFormat.getFormat("#,##0.00"));

        Row headerRow = sheet.createRow(0);
        int colIndex = 0;
        headerRow.createCell(colIndex++).setCellValue("ID");
        headerRow.createCell(colIndex++).setCellValue("Nombre Completo");
        headerRow.createCell(colIndex++).setCellValue("Plataformas");
        headerRow.createCell(colIndex++).setCellValue("Semanas Cobradas");
        headerRow.createCell(colIndex++).setCellValue("Total Acumulado ($)");

        for (String per : periodosOrdenados) {
            headerRow.createCell(colIndex++).setCellValue(per);
        }

        for (int i = 0; i < colIndex; i++) {
            headerRow.getCell(i).setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (ConsolidadoPersona persona : consolidadoRecords) {
            Row row = sheet.createRow(rowNum++);
            int cIndex = 0;
            row.createCell(cIndex++).setCellValue(persona.getIdentificacion());
            row.createCell(cIndex++).setCellValue(persona.getNombreCompleto());
            row.createCell(cIndex++).setCellValue(String.join(", ", persona.getPlataformas()));
            row.createCell(cIndex++).setCellValue(persona.getSemanasCobradas());
            
            org.apache.poi.ss.usermodel.Cell totalCell = row.createCell(cIndex++);
            totalCell.setCellValue(persona.getTotalAcumulado());
            totalCell.setCellStyle(numberStyle);

            for (String per : periodosOrdenados) {
                Double monto = persona.getIngresosPorPeriodo().get(per);
                org.apache.poi.ss.usermodel.Cell cell = row.createCell(cIndex++);
                if (monto != null && monto > 0.0) {
                    cell.setCellValue(monto);
                    cell.setCellStyle(numberStyle);
                } else {
                    cell.setCellValue("-");
                }
            }
        }

        for (int i = 0; i < colIndex; i++) {
            sheet.autoSizeColumn(i);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        httpHeaders.setContentDispositionFormData("attachment", "consolidado_general.xlsx");

        return ResponseEntity.ok().headers(httpHeaders).body(baos.toByteArray());
    }

    private boolean isRecordFromSheet(FileRecord record, String expectedSheet) {
        if (record == null || record.getData() == null) {
            return false;
        }
        String excelSheet = normalizeSheet(record.getData().get("Excel_Sheet"));
        String plainSheet = normalizeSheet(record.getData().get("Sheet"));
        String expected = normalizeSheet(expectedSheet);
        if (expected.equals(excelSheet) || expected.equals(plainSheet)) {
            return true;
        }
        if ("OLIVE".equals(expected)) {
            return record.getData().containsKey("CSV_Puntos Canjeables") ||
                   record.getData().containsKey("CSV_Pago Agencia") ||
                   record.getData().containsKey("CSV_Bonus Revenue");
        }
        return false;
    }

    private String normalizeSheet(String sheet) {
        if (sheet == null) {
            return "";
        }
        return sheet.trim().toUpperCase();
    }

    /**
     * DTO para representar el consolidado por persona en el histórico
     */
    public static class ConsolidadoPersona {
        private String identificacion;
        private String nombreCompleto;
        private Set<String> plataformas = new java.util.LinkedHashSet<>();
        private Map<String, Double> ingresosPorPeriodo = new java.util.HashMap<>();
        private Map<String, Double> monedasPorPeriodo = new java.util.HashMap<>();
        private int semanasCobradas = 0;
        private double totalAcumulado = 0.0;

        public ConsolidadoPersona(String identificacion, String nombreCompleto) {
            this.identificacion = identificacion;
            this.nombreCompleto = nombreCompleto;
        }

        public String getIdentificacion() { return identificacion; }
        public String getNombreCompleto() { return nombreCompleto; }
        public Set<String> getPlataformas() { return plataformas; }
        public Map<String, Double> getIngresosPorPeriodo() { return ingresosPorPeriodo; }
        public Map<String, Double> getMonedasPorPeriodo() { return monedasPorPeriodo; }
        public int getSemanasCobradas() { return semanasCobradas; }
        public double getTotalAcumulado() { return totalAcumulado; }

        public void agregarIngreso(String periodo, String sheet, Double monedas, Double totalMonedas, Double bonoAgencia, Double bonusTop100, Double recompensa, Double porcentajeDescuento, Double porcentaje1, Double porcentaje2) {
            this.plataformas.add(sheet);
            
            double valor = 0.0;
            double coins = 0.0;
            if ("SALSA".equals(sheet)) {
                double ba = bonoAgencia != null ? bonoAgencia : 0.0;
                double bt = bonusTop100 != null ? bonusTop100 : 0.0;
                double appliedDescuento = (porcentajeDescuento != null) ? porcentajeDescuento : 60.0;
                valor = ba * (1.0 - appliedDescuento/100.0);
                coins = totalMonedas != null ? totalMonedas : 0.0;
            } else if ("LIVEJOY".equals(sheet)) {
                double ing = monedas != null ? monedas : 0.0;
                double appliedP1 = (porcentaje1 != null) ? porcentaje1 : 12.0;
                double appliedP2 = (porcentaje2 != null) ? porcentaje2 : 40.0;
                valor = ing * (appliedP1/100.0) * (appliedP2/100.0);
                coins = ing;
            } else if ("OLIVE".equals(sheet)) {
                coins = totalMonedas != null ? totalMonedas : (monedas != null ? monedas : 0.0);
                double ba = bonoAgencia != null ? bonoAgencia : 0.0;
                double rec = recompensa != null ? recompensa : 0.0;
                valor = ba + rec;
            }

            if (valor > 0.0) {
                this.ingresosPorPeriodo.put(periodo, this.ingresosPorPeriodo.getOrDefault(periodo, 0.0) + valor);
            }
            if (coins > 0.0) {
                this.monedasPorPeriodo.put(periodo, this.monedasPorPeriodo.getOrDefault(periodo, 0.0) + coins);
            }
            
            recalcularTotales();
        }

        public void recalcularTotales() {
            this.totalAcumulado = this.ingresosPorPeriodo.values().stream().mapToDouble(Double::doubleValue).sum();
            this.semanasCobradas = (int) this.ingresosPorPeriodo.entrySet().stream()
                    .filter(e -> e.getValue() > 0.0)
                    .count();
        }
    }

    /**
     * DTOs para comparaciones de periodos por plataforma
     */
    public static class SalsaComparacion {
        private String id;
        private String nombre;
        private String nombreTutora = "-";
        private double coinsA;
        private double coinsB;
        private double tutoraA;
        private double tutoraB;

        public SalsaComparacion(String id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }

        public String getId() { return id; }
        public String getNombre() { return nombre; }
        public String getNombreTutora() { return nombreTutora; }
        public void setNombreTutora(String nombreTutora) { this.nombreTutora = nombreTutora; }
        public double getCoinsA() { return coinsA; }
        public void setCoinsA(double coinsA) { this.coinsA = coinsA; }
        public double getCoinsB() { return coinsB; }
        public void setCoinsB(double coinsB) { this.coinsB = coinsB; }
        public double getTutoraA() { return tutoraA; }
        public void setTutoraA(double tutoraA) { this.tutoraA = tutoraA; }
        public double getTutoraB() { return tutoraB; }
        public void setTutoraB(double tutoraB) { this.tutoraB = tutoraB; }
        public double getDiffTutora() { return tutoraB - tutoraA; }
        public double getTotalTutora() { return tutoraA + tutoraB; }
    }

    public static class LivejoyComparacion {
        private String id;
        private String usuario;
        private String nombreTutora = "-";
        private double montoDolaresA;
        private double montoDolaresB;
        private double tutoraA;
        private double tutoraB;

        public LivejoyComparacion(String id, String usuario) {
            this.id = id;
            this.usuario = usuario;
        }

        public String getId() { return id; }
        public String getUsuario() { return usuario; }
        public String getNombreTutora() { return nombreTutora; }
        public void setNombreTutora(String nombreTutora) { this.nombreTutora = nombreTutora; }
        public double getMontoDolaresA() { return montoDolaresA; }
        public void setMontoDolaresA(double montoDolaresA) { this.montoDolaresA = montoDolaresA; }
        public double getMontoDolaresB() { return montoDolaresB; }
        public void setMontoDolaresB(double montoDolaresB) { this.montoDolaresB = montoDolaresB; }
        public double getTutoraA() { return tutoraA; }
        public void setTutoraA(double tutoraA) { this.tutoraA = tutoraA; }
        public double getTutoraB() { return tutoraB; }
        public void setTutoraB(double tutoraB) { this.tutoraB = tutoraB; }
        public double getDiffTutora() { return tutoraB - tutoraA; }
        public double getTotalTutora() { return tutoraA + tutoraB; }
    }

    public static class OliveComparacion {
        private String id;
        private String nombre;
        private String nombreTutora = "-";
        private String nivel = "-";
        private double totalMonedasA;
        private double totalMonedasB;
        private double bonoAgenciaA;
        private double bonoAgenciaB;
        private double recompensaA;
        private double recompensaB;
        private double totalA;
        private double totalB;
        private double bonusRevenueA;
        private double bonusRevenueB;
        private double pagoChicaA;
        private double pagoChicaB;
        private double pagoTutoraA;
        private double pagoTutoraB;

        public OliveComparacion(String id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }

        public String getId() { return id; }
        public String getNombre() { return nombre; }
        public String getNombreTutora() { return nombreTutora; }
        public void setNombreTutora(String nombreTutora) { this.nombreTutora = nombreTutora; }
        public String getNivel() { return nivel; }
        public void setNivel(String nivel) { this.nivel = nivel; }
        public double getTotalMonedasA() { return totalMonedasA; }
        public void setTotalMonedasA(double totalMonedasA) { this.totalMonedasA = totalMonedasA; }
        public double getTotalMonedasB() { return totalMonedasB; }
        public void setTotalMonedasB(double totalMonedasB) { this.totalMonedasB = totalMonedasB; }
        public double getBonoAgenciaA() { return bonoAgenciaA; }
        public void setBonoAgenciaA(double bonoAgenciaA) { this.bonoAgenciaA = bonoAgenciaA; }
        public double getBonoAgenciaB() { return bonoAgenciaB; }
        public void setBonoAgenciaB(double bonoAgenciaB) { this.bonoAgenciaB = bonoAgenciaB; }
        public double getRecompensaA() { return recompensaA; }
        public void setRecompensaA(double recompensaA) { this.recompensaA = recompensaA; }
        public double getRecompensaB() { return recompensaB; }
        public void setRecompensaB(double recompensaB) { this.recompensaB = recompensaB; }
        public double getTotalA() { return totalA; }
        public void setTotalA(double totalA) { this.totalA = totalA; }
        public double getTotalB() { return totalB; }
        public void setTotalB(double totalB) { this.totalB = totalB; }
        public double getBonusRevenueA() { return bonusRevenueA; }
        public void setBonusRevenueA(double bonusRevenueA) { this.bonusRevenueA = bonusRevenueA; }
        public double getBonusRevenueB() { return bonusRevenueB; }
        public void setBonusRevenueB(double bonusRevenueB) { this.bonusRevenueB = bonusRevenueB; }
        public double getPagoChicaA() { return pagoChicaA; }
        public void setPagoChicaA(double pagoChicaA) { this.pagoChicaA = pagoChicaA; }
        public double getPagoChicaB() { return pagoChicaB; }
        public void setPagoChicaB(double pagoChicaB) { this.pagoChicaB = pagoChicaB; }
        public double getPagoTutoraA() { return pagoTutoraA; }
        public void setPagoTutoraA(double pagoTutoraA) { this.pagoTutoraA = pagoTutoraA; }
        public double getPagoTutoraB() { return pagoTutoraB; }
        public void setPagoTutoraB(double pagoTutoraB) { this.pagoTutoraB = pagoTutoraB; }
        public double getDiffTotal() { return totalB - totalA; }
        public double getTotalConsolidado() { return totalA + totalB; }
    }

    private List<SalsaComparacion> obtenerComparacionSalsa(String pA, String pB, double descuento) {
        List<HistoricoIngreso> regA = historicoService.buscarPorPeriodo(pA);
        List<HistoricoIngreso> regB = historicoService.buscarPorPeriodo(pB);

        Map<String, SalsaComparacion> map = new HashMap<>();
        for (HistoricoIngreso r : regA) {
            if ("SALSA".equals(normalizeSheet(r.getSheet()))) {
                SalsaComparacion sc = map.computeIfAbsent(r.getIdentificacion(), k -> new SalsaComparacion(r.getIdentificacion(), r.getNombreCompleto()));
                if (r.getNombreTutora() != null && !r.getNombreTutora().isEmpty() && !"-".equals(r.getNombreTutora())) {
                    sc.setNombreTutora(r.getNombreTutora());
                }
                double ba = r.getBonoAgencia() != null ? r.getBonoAgencia() : 0.0;
                double bt = r.getBonusTop100() != null ? r.getBonusTop100() : 0.0;
                double coins = r.getTotalMonedas() != null ? r.getTotalMonedas() : 0.0;
                double appliedDescuento = (r.getPorcentajeDescuento() != null) ? r.getPorcentajeDescuento() : descuento;
                double val = ba * (1.0 - appliedDescuento/100.0);
                if (coins > 0.0 || val > 0.0) {
                    sc.setCoinsA(sc.getCoinsA() + coins);
                    sc.setTutoraA(sc.getTutoraA() + val);
                }
            }
        }
        for (HistoricoIngreso r : regB) {
            if ("SALSA".equals(normalizeSheet(r.getSheet()))) {
                SalsaComparacion sc = map.computeIfAbsent(r.getIdentificacion(), k -> new SalsaComparacion(r.getIdentificacion(), r.getNombreCompleto()));
                if (r.getNombreTutora() != null && !r.getNombreTutora().isEmpty() && !"-".equals(r.getNombreTutora())) {
                    sc.setNombreTutora(r.getNombreTutora());
                }
                double ba = r.getBonoAgencia() != null ? r.getBonoAgencia() : 0.0;
                double bt = r.getBonusTop100() != null ? r.getBonusTop100() : 0.0;
                double coins = r.getTotalMonedas() != null ? r.getTotalMonedas() : 0.0;
                double appliedDescuento = (r.getPorcentajeDescuento() != null) ? r.getPorcentajeDescuento() : descuento;
                double val = ba * (1.0 - appliedDescuento/100.0);
                if (coins > 0.0 || val > 0.0) {
                    sc.setCoinsB(sc.getCoinsB() + coins);
                    sc.setTutoraB(sc.getTutoraB() + val);
                }
            }
        }
        List<SalsaComparacion> list = new ArrayList<>(map.values());
        list.sort((x, y) -> x.getNombre().compareToIgnoreCase(y.getNombre()));
        return list;
    }

    private List<LivejoyComparacion> obtenerComparacionLivejoy(String pA, String pB, double p1, double p2) {
        List<HistoricoIngreso> regA = historicoService.buscarPorPeriodo(pA);
        List<HistoricoIngreso> regB = historicoService.buscarPorPeriodo(pB);

        Map<String, LivejoyComparacion> map = new HashMap<>();
        for (HistoricoIngreso r : regA) {
            if ("LIVEJOY".equals(normalizeSheet(r.getSheet()))) {
                LivejoyComparacion lc = map.computeIfAbsent(r.getIdentificacion(), k -> new LivejoyComparacion(r.getIdentificacion(), r.getNombreCompleto()));
                if (r.getNombreTutora() != null && !r.getNombreTutora().isEmpty() && !"-".equals(r.getNombreTutora())) {
                    lc.setNombreTutora(r.getNombreTutora());
                }
                double ing = r.getMonedas() != null ? r.getMonedas() : 0.0;
                double appliedP1 = (r.getPorcentaje1() != null) ? r.getPorcentaje1() : p1;
                double appliedP2 = (r.getPorcentaje2() != null) ? r.getPorcentaje2() : p2;
                double val = ing * (appliedP1/100.0) * (appliedP2/100.0);
                if (ing > 0.0 || val > 0.0) {
                    lc.setMontoDolaresA(lc.getMontoDolaresA() + ing);
                    lc.setTutoraA(lc.getTutoraA() + val);
                }
            }
        }
        for (HistoricoIngreso r : regB) {
            if ("LIVEJOY".equals(normalizeSheet(r.getSheet()))) {
                LivejoyComparacion lc = map.computeIfAbsent(r.getIdentificacion(), k -> new LivejoyComparacion(r.getIdentificacion(), r.getNombreCompleto()));
                if (r.getNombreTutora() != null && !r.getNombreTutora().isEmpty() && !"-".equals(r.getNombreTutora())) {
                    lc.setNombreTutora(r.getNombreTutora());
                }
                double ing = r.getMonedas() != null ? r.getMonedas() : 0.0;
                double appliedP1 = (r.getPorcentaje1() != null) ? r.getPorcentaje1() : p1;
                double appliedP2 = (r.getPorcentaje2() != null) ? r.getPorcentaje2() : p2;
                double val = ing * (appliedP1/100.0) * (appliedP2/100.0);
                if (ing > 0.0 || val > 0.0) {
                    lc.setMontoDolaresB(lc.getMontoDolaresB() + ing);
                    lc.setTutoraB(lc.getTutoraB() + val);
                }
            }
        }
        List<LivejoyComparacion> list = new ArrayList<>(map.values());
        list.sort((x, y) -> x.getUsuario().compareToIgnoreCase(y.getUsuario()));
        return list;
    }

    private List<OliveComparacion> obtenerComparacionOlive(String pA, String pB, double p1, double p2) {
        List<HistoricoIngreso> regA = historicoService.buscarPorPeriodo(pA);
        List<HistoricoIngreso> regB = historicoService.buscarPorPeriodo(pB);

        Map<String, OliveComparacion> map = new HashMap<>();
        for (HistoricoIngreso r : regA) {
            if ("OLIVE".equals(normalizeSheet(r.getSheet()))) {
                OliveComparacion oc = map.computeIfAbsent(r.getIdentificacion(), k -> new OliveComparacion(r.getIdentificacion(), r.getNombreCompleto()));
                if (r.getNombreTutora() != null && !r.getNombreTutora().isEmpty() && !"-".equals(r.getNombreTutora())) {
                    oc.setNombreTutora(r.getNombreTutora());
                }
                if (r.getNivel() != null && !r.getNivel().isEmpty() && !"-".equals(r.getNivel())) {
                    oc.setNivel(r.getNivel());
                }
                double tm = r.getMonedas() != null ? r.getMonedas() : 0.0;
                double rec = r.getRecompensaEvento() != null ? r.getRecompensaEvento() : 0.0;

                double ap = r.getBonoAgencia() != null ? r.getBonoAgencia() : 0.0;
                double br = r.getBonusRevenue() != null ? r.getBonusRevenue() : 0.0;
                double p1Val = r.getPorcentaje1() != null ? r.getPorcentaje1() : p1;
                double p2Val = r.getPorcentaje2() != null ? r.getPorcentaje2() : p2;

                double comisionBase = tm * 0.10;
                double pagoChica = comisionBase * (p1Val / 100.0);
                double pagoTutora = (comisionBase * (p2Val / 100.0)) + (br / 3.0);

                oc.setTotalMonedasA(oc.getTotalMonedasA() + tm);
                oc.setRecompensaA(oc.getRecompensaA() + rec);
                oc.setBonoAgenciaA(oc.getBonoAgenciaA() + ap);
                oc.setBonusRevenueA(oc.getBonusRevenueA() + br);
                oc.setPagoChicaA(oc.getPagoChicaA() + pagoChica);
                oc.setPagoTutoraA(oc.getPagoTutoraA() + pagoTutora);
                oc.setTotalA(oc.getTotalA() + pagoTutora);
            }
        }
        for (HistoricoIngreso r : regB) {
            if ("OLIVE".equals(normalizeSheet(r.getSheet()))) {
                OliveComparacion oc = map.computeIfAbsent(r.getIdentificacion(), k -> new OliveComparacion(r.getIdentificacion(), r.getNombreCompleto()));
                if (r.getNombreTutora() != null && !r.getNombreTutora().isEmpty() && !"-".equals(r.getNombreTutora())) {
                    oc.setNombreTutora(r.getNombreTutora());
                }
                if (r.getNivel() != null && !r.getNivel().isEmpty() && !"-".equals(r.getNivel())) {
                    oc.setNivel(r.getNivel());
                }
                double tm = r.getMonedas() != null ? r.getMonedas() : 0.0;
                double rec = r.getRecompensaEvento() != null ? r.getRecompensaEvento() : 0.0;

                double ap = r.getBonoAgencia() != null ? r.getBonoAgencia() : 0.0;
                double br = r.getBonusRevenue() != null ? r.getBonusRevenue() : 0.0;
                double p1Val = r.getPorcentaje1() != null ? r.getPorcentaje1() : p1;
                double p2Val = r.getPorcentaje2() != null ? r.getPorcentaje2() : p2;

                double comisionBase = tm * 0.10;
                double pagoChica = comisionBase * (p1Val / 100.0);
                double pagoTutora = (comisionBase * (p2Val / 100.0)) + (br / 3.0);

                oc.setTotalMonedasB(oc.getTotalMonedasB() + tm);
                oc.setRecompensaB(oc.getRecompensaB() + rec);
                oc.setBonoAgenciaB(oc.getBonoAgenciaB() + ap);
                oc.setBonusRevenueB(oc.getBonusRevenueB() + br);
                oc.setPagoChicaB(oc.getPagoChicaB() + pagoChica);
                oc.setPagoTutoraB(oc.getPagoTutoraB() + pagoTutora);
                oc.setTotalB(oc.getTotalB() + pagoTutora);
            }
        }
        List<OliveComparacion> list = new ArrayList<>(map.values());
        list.sort((x, y) -> x.getNombre().compareToIgnoreCase(y.getNombre()));
        return list;
    }

    @GetMapping("/historico/descargar/comparar-salsa")
    public ResponseEntity<byte[]> descargarCompararSalsaExcel(
            @RequestParam("pA") String pA,
            @RequestParam("pB") String pB,
            @RequestParam(value = "descuento", defaultValue = "60") double descuento) throws Exception {

        List<SalsaComparacion> comparacion = obtenerComparacionSalsa(pA.trim(), pB.trim(), descuento);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Comparativa SALSA");

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        DataFormat dataFormat = workbook.createDataFormat();
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(dataFormat.getFormat("#,##0.00"));

        CellStyle totalLabelStyle = workbook.createCellStyle();
        Font totalFont = workbook.createFont();
        totalFont.setBold(true);
        totalLabelStyle.setFont(totalFont);
        totalLabelStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        totalLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle totalNumberStyle = workbook.createCellStyle();
        totalNumberStyle.cloneStyleFrom(numberStyle);
        totalNumberStyle.setFont(totalFont);
        totalNumberStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        totalNumberStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row headerRow = sheet.createRow(0);
        String[] headers = { "ID", "Nombre", "Coins " + pA, "Coins " + pB, "Tutora " + pA + " ($)", "Tutora " + pB + " ($)", "Variación ($)", "Total Consolidado ($)", "Tutora (Archivo)" };

        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        double totalCoinsA = 0.0;
        double totalCoinsB = 0.0;
        double totalTutoraA = 0.0;
        double totalTutoraB = 0.0;
        double totalDiff = 0.0;
        double totalSuma = 0.0;

        for (SalsaComparacion fila : comparacion) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(fila.getId());
            row.createCell(1).setCellValue(fila.getNombre());
            
            org.apache.poi.ss.usermodel.Cell cellCoinsA = row.createCell(2);
            cellCoinsA.setCellValue(fila.getCoinsA());
            cellCoinsA.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellCoinsB = row.createCell(3);
            cellCoinsB.setCellValue(fila.getCoinsB());
            cellCoinsB.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellTutA = row.createCell(4);
            cellTutA.setCellValue(fila.getTutoraA());
            cellTutA.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellTutB = row.createCell(5);
            cellTutB.setCellValue(fila.getTutoraB());
            cellTutB.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellDiff = row.createCell(6);
            cellDiff.setCellValue(fila.getDiffTutora());
            cellDiff.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellTotal = row.createCell(7);
            cellTotal.setCellValue(fila.getTotalTutora());
            cellTotal.setCellStyle(numberStyle);
            
            row.createCell(8).setCellValue(fila.getNombreTutora());

            totalCoinsA += fila.getCoinsA();
            totalCoinsB += fila.getCoinsB();
            totalTutoraA += fila.getTutoraA();
            totalTutoraB += fila.getTutoraB();
            totalDiff += fila.getDiffTutora();
            totalSuma += fila.getTotalTutora();
        }

        Row totalRow = sheet.createRow(rowNum);
        totalRow.createCell(0).setCellValue("TOTAL");
        totalRow.getCell(0).setCellStyle(totalLabelStyle);
        totalRow.createCell(1).setCellValue("");
        totalRow.getCell(1).setCellStyle(totalLabelStyle);

        totalRow.createCell(2).setCellValue(totalCoinsA);
        totalRow.getCell(2).setCellStyle(totalNumberStyle);
        totalRow.createCell(3).setCellValue(totalCoinsB);
        totalRow.getCell(3).setCellStyle(totalNumberStyle);
        totalRow.createCell(4).setCellValue(totalTutoraA);
        totalRow.getCell(4).setCellStyle(totalNumberStyle);
        totalRow.createCell(5).setCellValue(totalTutoraB);
        totalRow.getCell(5).setCellStyle(totalNumberStyle);
        totalRow.createCell(6).setCellValue(totalDiff);
        totalRow.getCell(6).setCellStyle(totalNumberStyle);
        totalRow.createCell(7).setCellValue(totalSuma);
        totalRow.getCell(7).setCellStyle(totalNumberStyle);

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        String filename = "salsa_comparativa_" + pA.replaceAll("\\s+", "_") + "_vs_" + pB.replaceAll("\\s+", "_") + ".xlsx";

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        httpHeaders.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok().headers(httpHeaders).body(baos.toByteArray());
    }

    @GetMapping("/historico/descargar/comparar-livejoy")
    public ResponseEntity<byte[]> descargarCompararLivejoyExcel(
            @RequestParam("pA") String pA,
            @RequestParam("pB") String pB,
            @RequestParam(value = "p1", defaultValue = "12") double p1,
            @RequestParam(value = "p2", defaultValue = "40") double p2) throws Exception {

        List<LivejoyComparacion> comparacion = obtenerComparacionLivejoy(pA.trim(), pB.trim(), p1, p2);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Comparativa LIVEJOY");

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        DataFormat dataFormat = workbook.createDataFormat();
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(dataFormat.getFormat("#,##0.00"));

        CellStyle totalLabelStyle = workbook.createCellStyle();
        Font totalFont = workbook.createFont();
        totalFont.setBold(true);
        totalLabelStyle.setFont(totalFont);
        totalLabelStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        totalLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle totalNumberStyle = workbook.createCellStyle();
        totalNumberStyle.cloneStyleFrom(numberStyle);
        totalNumberStyle.setFont(totalFont);
        totalNumberStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        totalNumberStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row headerRow = sheet.createRow(0);
        String[] headers = { "ID LIVEJOY", "Usuario", "Monto Dólares " + pA, "Monto Dólares " + pB, "Tutora " + pA + " ($)", "Tutora " + pB + " ($)", "Variación ($)", "Total Consolidado ($)", "Tutora (Archivo)" };

        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        double totalMontoA = 0.0;
        double totalMontoB = 0.0;
        double totalTutoraA = 0.0;
        double totalTutoraB = 0.0;
        double totalDiff = 0.0;
        double totalSuma = 0.0;

        for (LivejoyComparacion fila : comparacion) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(fila.getId());
            row.createCell(1).setCellValue(fila.getUsuario());
            
            org.apache.poi.ss.usermodel.Cell cellMonA = row.createCell(2);
            cellMonA.setCellValue(fila.getMontoDolaresA());
            cellMonA.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellMonB = row.createCell(3);
            cellMonB.setCellValue(fila.getMontoDolaresB());
            cellMonB.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellTutA = row.createCell(4);
            cellTutA.setCellValue(fila.getTutoraA());
            cellTutA.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellTutB = row.createCell(5);
            cellTutB.setCellValue(fila.getTutoraB());
            cellTutB.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellDiff = row.createCell(6);
            cellDiff.setCellValue(fila.getDiffTutora());
            cellDiff.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellTotal = row.createCell(7);
            cellTotal.setCellValue(fila.getTotalTutora());
            cellTotal.setCellStyle(numberStyle);
            
            row.createCell(8).setCellValue(fila.getNombreTutora());

            totalMontoA += fila.getMontoDolaresA();
            totalMontoB += fila.getMontoDolaresB();
            totalTutoraA += fila.getTutoraA();
            totalTutoraB += fila.getTutoraB();
            totalDiff += fila.getDiffTutora();
            totalSuma += fila.getTotalTutora();
        }

        Row totalRow = sheet.createRow(rowNum);
        totalRow.createCell(0).setCellValue("TOTAL");
        totalRow.getCell(0).setCellStyle(totalLabelStyle);
        totalRow.createCell(1).setCellValue("");
        totalRow.getCell(1).setCellStyle(totalLabelStyle);

        totalRow.createCell(2).setCellValue(totalMontoA);
        totalRow.getCell(2).setCellStyle(totalNumberStyle);
        totalRow.createCell(3).setCellValue(totalMontoB);
        totalRow.getCell(3).setCellStyle(totalNumberStyle);
        totalRow.createCell(4).setCellValue(totalTutoraA);
        totalRow.getCell(4).setCellStyle(totalNumberStyle);
        totalRow.createCell(5).setCellValue(totalTutoraB);
        totalRow.getCell(5).setCellStyle(totalNumberStyle);
        totalRow.createCell(6).setCellValue(totalDiff);
        totalRow.getCell(6).setCellStyle(totalNumberStyle);
        totalRow.createCell(7).setCellValue(totalSuma);
        totalRow.getCell(7).setCellStyle(totalNumberStyle);

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        String filename = "livejoy_comparativa_" + pA.replaceAll("\\s+", "_") + "_vs_" + pB.replaceAll("\\s+", "_") + ".xlsx";

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        httpHeaders.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok().headers(httpHeaders).body(baos.toByteArray());
    }

    @GetMapping("/historico/descargar/comparar-olive")
    public ResponseEntity<byte[]> descargarCompararOliveExcel(
            @RequestParam("pA") String pA,
            @RequestParam("pB") String pB,
            @RequestParam(value = "oliveP1", defaultValue = "60") double oliveP1,
            @RequestParam(value = "oliveP2", defaultValue = "40") double oliveP2) throws Exception {

        List<OliveComparacion> comparacion = obtenerComparacionOlive(pA.trim(), pB.trim(), oliveP1, oliveP2);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Comparativa OLIVE");

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        DataFormat dataFormat = workbook.createDataFormat();
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(dataFormat.getFormat("#,##0.00"));

        CellStyle totalLabelStyle = workbook.createCellStyle();
        Font totalFont = workbook.createFont();
        totalFont.setBold(true);
        totalLabelStyle.setFont(totalFont);
        totalLabelStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        totalLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle totalNumberStyle = workbook.createCellStyle();
        totalNumberStyle.cloneStyleFrom(numberStyle);
        totalNumberStyle.setFont(totalFont);
        totalNumberStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        totalNumberStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        String[] headers = { 
            "ID", "Nombre", 
            "MONTO DOLARES " + pA, "MONTO DOLARES " + pB, 
            "Nivel", 
            "Puntos Canjeables " + pA + " ($)", "Puntos Canjeables " + pB + " ($)", 
            "Pago Agencia " + pA + " ($)", "Pago Agencia " + pB + " ($)", 
            "Bonus Revenue " + pA + " ($)", "Bonus Revenue " + pB + " ($)", 
            "Pago Tutora " + pA + " ($)", "Pago Tutora " + pB + " ($)", 
            "Variación ($)", "Total Consolidado ($)", "Tutora (Archivo)" 
        };

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        double totalCoinsA = 0.0;
        double totalCoinsB = 0.0;
        double totalRecA = 0.0;
        double totalRecB = 0.0;
        double totalBonoA = 0.0;
        double totalBonoB = 0.0;
        double totalBRA = 0.0;
        double totalBRB = 0.0;
        double totalTutoraA = 0.0;
        double totalTutoraB = 0.0;
        double totalDiff = 0.0;
        double totalSuma = 0.0;

        for (OliveComparacion fila : comparacion) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(fila.getId());
            row.createCell(1).setCellValue(fila.getNombre());
            
            org.apache.poi.ss.usermodel.Cell cellCoinsA = row.createCell(2);
            cellCoinsA.setCellValue(fila.getTotalMonedasA());
            cellCoinsA.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellCoinsB = row.createCell(3);
            cellCoinsB.setCellValue(fila.getTotalMonedasB());
            cellCoinsB.setCellStyle(numberStyle);
            
            row.createCell(4).setCellValue(fila.getNivel());
            
            org.apache.poi.ss.usermodel.Cell cellRecA = row.createCell(5);
            cellRecA.setCellValue(fila.getRecompensaA());
            cellRecA.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellRecB = row.createCell(6);
            cellRecB.setCellValue(fila.getRecompensaB());
            cellRecB.setCellStyle(numberStyle);

            org.apache.poi.ss.usermodel.Cell cellBonoA = row.createCell(7);
            cellBonoA.setCellValue(fila.getBonoAgenciaA());
            cellBonoA.setCellStyle(numberStyle);

            org.apache.poi.ss.usermodel.Cell cellBonoB = row.createCell(8);
            cellBonoB.setCellValue(fila.getBonoAgenciaB());
            cellBonoB.setCellStyle(numberStyle);

            org.apache.poi.ss.usermodel.Cell cellBRA = row.createCell(9);
            cellBRA.setCellValue(fila.getBonusRevenueA());
            cellBRA.setCellStyle(numberStyle);

            org.apache.poi.ss.usermodel.Cell cellBRB = row.createCell(10);
            cellBRB.setCellValue(fila.getBonusRevenueB());
            cellBRB.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellTutoraA = row.createCell(11);
            cellTutoraA.setCellValue(fila.getPagoTutoraA());
            cellTutoraA.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellTutoraB = row.createCell(12);
            cellTutoraB.setCellValue(fila.getPagoTutoraB());
            cellTutoraB.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellDiff = row.createCell(13);
            cellDiff.setCellValue(fila.getDiffTotal());
            cellDiff.setCellStyle(numberStyle);
            
            org.apache.poi.ss.usermodel.Cell cellTotal = row.createCell(14);
            cellTotal.setCellValue(fila.getTotalConsolidado());
            cellTotal.setCellStyle(numberStyle);
            
            row.createCell(15).setCellValue(fila.getNombreTutora());

            totalCoinsA += fila.getTotalMonedasA();
            totalCoinsB += fila.getTotalMonedasB();
            totalRecA += fila.getRecompensaA();
            totalRecB += fila.getRecompensaB();
            totalBonoA += fila.getBonoAgenciaA();
            totalBonoB += fila.getBonoAgenciaB();
            totalBRA += fila.getBonusRevenueA();
            totalBRB += fila.getBonusRevenueB();
            totalTutoraA += fila.getPagoTutoraA();
            totalTutoraB += fila.getPagoTutoraB();
            totalDiff += fila.getDiffTotal();
            totalSuma += fila.getTotalConsolidado();
        }

        Row totalRow = sheet.createRow(rowNum);
        totalRow.createCell(0).setCellValue("TOTAL");
        totalRow.getCell(0).setCellStyle(totalLabelStyle);
        totalRow.createCell(1).setCellValue("");
        totalRow.getCell(1).setCellStyle(totalLabelStyle);

        totalRow.createCell(2).setCellValue(totalCoinsA);
        totalRow.getCell(2).setCellStyle(totalNumberStyle);
        totalRow.createCell(3).setCellValue(totalCoinsB);
        totalRow.getCell(3).setCellStyle(totalNumberStyle);
        totalRow.createCell(4).setCellValue("");
        totalRow.getCell(4).setCellStyle(totalLabelStyle);
        totalRow.createCell(5).setCellValue(totalRecA);
        totalRow.getCell(5).setCellStyle(totalNumberStyle);
        totalRow.createCell(6).setCellValue(totalRecB);
        totalRow.getCell(6).setCellStyle(totalNumberStyle);
        totalRow.createCell(7).setCellValue(totalBonoA);
        totalRow.getCell(7).setCellStyle(totalNumberStyle);
        totalRow.createCell(8).setCellValue(totalBonoB);
        totalRow.getCell(8).setCellStyle(totalNumberStyle);
        totalRow.createCell(9).setCellValue(totalBRA);
        totalRow.getCell(9).setCellStyle(totalNumberStyle);
        totalRow.createCell(10).setCellValue(totalBRB);
        totalRow.getCell(10).setCellStyle(totalNumberStyle);
        totalRow.createCell(11).setCellValue(totalTutoraA);
        totalRow.getCell(11).setCellStyle(totalNumberStyle);
        totalRow.createCell(12).setCellValue(totalTutoraB);
        totalRow.getCell(12).setCellStyle(totalNumberStyle);
        totalRow.createCell(13).setCellValue(totalDiff);
        totalRow.getCell(13).setCellStyle(totalNumberStyle);
        totalRow.createCell(14).setCellValue(totalSuma);
        totalRow.getCell(14).setCellStyle(totalNumberStyle);

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        String filename = "olive_comparativa_" + pA.replaceAll("\\s+", "_") + "_vs_" + pB.replaceAll("\\s+", "_") + ".xlsx";

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        httpHeaders.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok().headers(httpHeaders).body(baos.toByteArray());
    }
}

