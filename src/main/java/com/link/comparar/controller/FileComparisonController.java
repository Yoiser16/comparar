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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import jakarta.servlet.http.HttpSession;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
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
            @RequestParam("csvFiles") MultipartFile[] csvFiles,
            @RequestParam("excelFiles") MultipartFile[] excelFiles,
            @RequestParam(value = "periodoComparacion", required = false) String periodoComparacion,
            Model model,
            RedirectAttributes redirectAttributes) {

        // Validar que se hayan seleccionado archivos
        if (csvFiles == null || csvFiles.length == 0 || excelFiles == null || excelFiles.length == 0) {
            redirectAttributes.addFlashAttribute("error",
                    "Por favor, selecciona al menos un archivo CSV y un archivo Excel.");
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
        for (MultipartFile csv : csvFiles) {
            if (!csv.isEmpty()) {
                allCsvEmpty = false;
                break;
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
            redirectAttributes.addFlashAttribute("error", "Los archivos seleccionados están vacíos.");
            return "redirect:/";
        }

        // Validar extensiones de archivos
        for (MultipartFile csvFile : csvFiles) {
            if (csvFile.isEmpty())
                continue;
            String csvFileName = csvFile.getOriginalFilename();
            if (csvFileName == null || !csvFileName.toLowerCase().endsWith(".csv")) {
                redirectAttributes.addFlashAttribute("error",
                        "Todos los archivos CSV deben tener extensión .csv: " + csvFileName);
                return "redirect:/";
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
                case "pdf":
                    data = generatePdf(result.getMatchingRecords(), "Registros Coincidentes");
                    filename = "coincidencias.pdf";
                    mediaType = MediaType.APPLICATION_PDF;
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
            @SessionAttribute("comparisonResult") ComparisonResult result) {
        try {
            byte[] data = generateLivejoyTutoraExcel(result.getMatchingRecords());
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

    private byte[] generateLivejoyTutoraExcel(List<FileRecord> records) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("LIVEJOY");

        // Filtrar solo registros de LIVEJOY
        List<FileRecord> livejoyRecords = records.stream()
                .filter(r -> r.getData() != null && 
                        (r.getData().containsKey("Excel_Sheet") && 
                         "LIVEJOY".equalsIgnoreCase(r.getData().get("Excel_Sheet"))))
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
            
            // Tutora (Calculado: Ingresos * 12% * 40%)
            double tutoraValue = 0.0;
            try {
                if (ingresos != null && !ingresos.trim().isEmpty()) {
                    double ingresosNum = Double.parseDouble(ingresos.replace(",", ""));
                    tutoraValue = ingresosNum * 0.12 * 0.40;
                }
            } catch (NumberFormatException e) {
                // Si no se puede parsear, dejar en 0
            }
            row.createCell(6).setCellValue(String.format("%.2f", tutoraValue));
        }

        // Auto-ajustar columnas
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        workbook.write(baos);
        workbook.close();
        return baos.toByteArray();
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

    private byte[] generatePdf(List<FileRecord> records, String title) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        // Título
        Paragraph titlePara = new Paragraph(title)
                .setFontSize(18)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);
        document.add(titlePara);

        document.add(new Paragraph(" ")); // Espacio

        if (records.isEmpty()) {
            document.add(new Paragraph("No hay registros para exportar"));
            document.close();
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

        // Crear tabla
        Table table = new Table(UnitValue.createPercentArray(allColumns.size()))
                .useAllAvailableWidth();

        // Encabezados
        for (String column : allColumns) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(column).setBold())
                    .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY));
        }

        // Filas de datos
        for (FileRecord record : records) {
            for (String column : allColumns) {
                String value;
                if (column.equals("ID")) {
                    value = record.getId();
                } else {
                    value = record.getData() != null ? record.getData().get(column) : "";
                }
                table.addCell(new Cell().add(new Paragraph(value != null ? value : "")));
            }
        }

        document.add(table);

        // Pie de página
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Total de registros: " + records.size())
                .setFontSize(10)
                .setTextAlignment(TextAlignment.RIGHT));

        document.close();
        return baos.toByteArray();
    }

    /**
     * Muestra la página del histórico de ingresos
     */
    @GetMapping("/historico")
    public String verHistorico(Model model) {
        List<HistoricoIngreso> registros = historicoService.obtenerTodosLosRegistros();
        List<String> periodosDisponibles = historicoService.obtenerPeriodosDisponibles();

        model.addAttribute("registros", registros);
        model.addAttribute("periodosDisponibles", periodosDisponibles);
        model.addAttribute("periodoSeleccionado", null);
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

        model.addAttribute("registros", registros);
        model.addAttribute("periodosDisponibles", periodosDisponibles);
        model.addAttribute("periodoSeleccionado", periodo);
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

        model.addAttribute("registros", registros);
        model.addAttribute("periodosDisponibles", periodosDisponibles);
        model.addAttribute("query", query);
        return "historico";
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
}
