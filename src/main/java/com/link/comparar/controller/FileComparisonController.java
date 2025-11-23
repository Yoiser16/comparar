package com.link.comparar.controller;

import com.link.comparar.model.ComparisonResult;
import com.link.comparar.service.FileComparisonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FileComparisonController {

    @Autowired
    private FileComparisonService fileComparisonService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/compare")
    public String compareFiles(
            @RequestParam("csvFile") MultipartFile csvFile,
            @RequestParam("excelFile") MultipartFile excelFile,
            Model model,
            RedirectAttributes redirectAttributes) {

        // Validar que los archivos no estén vacíos
        if (csvFile.isEmpty() || excelFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Por favor, selecciona ambos archivos.");
            return "redirect:/";
        }

        // Validar extensiones de archivo
        String csvFileName = csvFile.getOriginalFilename();
        String excelFileName = excelFile.getOriginalFilename();

        if (csvFileName == null || !csvFileName.toLowerCase().endsWith(".csv")) {
            redirectAttributes.addFlashAttribute("error", "El primer archivo debe ser formato CSV (.csv)");
            return "redirect:/";
        }

        if (excelFileName == null ||
                (!excelFileName.toLowerCase().endsWith(".xlsx") && !excelFileName.toLowerCase().endsWith(".xls"))) {
            redirectAttributes.addFlashAttribute("error", "El segundo archivo debe ser formato Excel (.xlsx o .xls)");
            return "redirect:/";
        }

        try {
            // Comparar archivos
            ComparisonResult result = fileComparisonService.compareFiles(csvFile, excelFile);

            model.addAttribute("result", result);
            model.addAttribute("csvFileName", csvFileName);
            model.addAttribute("excelFileName", excelFileName);

            return "result";
        } catch (java.io.IOException e) {
            String errorMsg;
            if (e.getMessage() != null && e.getMessage().contains("invalid char")) {
                errorMsg = "El archivo CSV tiene un formato incorrecto. Verifica que use punto y coma (;) como separador y que no tenga comillas mal cerradas.";
            } else {
                errorMsg = "Error al procesar los archivos. Asegúrate de que:\n" +
                        "• El CSV use punto y coma (;) como separador\n" +
                        "• El Excel esté en formato válido (.xlsx o .xls)\n" +
                        "• Ambos archivos tengan una columna 'ID'";
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
}
