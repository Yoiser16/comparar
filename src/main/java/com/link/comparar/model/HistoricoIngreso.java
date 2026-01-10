package com.link.comparar.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "historico_ingresos")
public class HistoricoIngreso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String identificacion;

    @Column(name = "nombre_completo", length = 500)
    private String nombreCompleto;

    @Column(name = "monedas")
    private Double monedas;

    @Column(name = "total_monedas")
    private Double totalMonedas;

    @Column(name = "bono_agencia")
    private Double bonoAgencia;

    @Column(name = "recompensa_evento")
    private Double recompensaEvento;

    @Column(name = "bonus_top_100")
    private Double bonusTop100;

    @Column(name = "loyalty_credits")
    private Double loyaltyCredits;

    @Column(name = "semana")
    private Integer semana;

    @Column(name = "pais", length = 100)
    private String pais;

    @Column(name = "whatsapp", length = 50)
    private String whatsapp;

    @Column(name = "fuente", length = 20)
    private String fuente; // CSV, Excel, Ambos

    @Column(name = "sheet", length = 50)
    private String sheet; // SALSA, LIVEJOY, OLIVE

    @Column(name = "periodo_comparacion", length = 200)
    private String periodoComparacion; // Etiqueta de fecha ingresada por el usuario (ej: "Semana 1 - Enero 2026")

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    // Constructor vacío requerido por JPA
    public HistoricoIngreso() {
        this.fechaRegistro = LocalDateTime.now();
    }

    // Constructor con parámetros
    public HistoricoIngreso(String identificacion, String nombreCompleto, Double monedas, Double totalMonedas,
            Double bonoAgencia, Double recompensaEvento, Double bonusTop100, Double loyaltyCredits, Integer semana,
            String pais, String whatsapp, String fuente, String sheet, String periodoComparacion) {
        this.identificacion = identificacion;
        this.nombreCompleto = nombreCompleto;
        this.monedas = monedas;
        this.totalMonedas = totalMonedas;
        this.bonoAgencia = bonoAgencia;
        this.recompensaEvento = recompensaEvento;
        this.bonusTop100 = bonusTop100;
        this.loyaltyCredits = loyaltyCredits;
        this.semana = semana;
        this.pais = pais;
        this.whatsapp = whatsapp;
        this.fuente = fuente;
        this.sheet = sheet;
        this.periodoComparacion = periodoComparacion;
        this.fechaRegistro = LocalDateTime.now();
    }

    // Getters y Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdentificacion() {
        return identificacion;
    }

    public void setIdentificacion(String identificacion) {
        this.identificacion = identificacion;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public Double getMonedas() {
        return monedas;
    }

    public void setMonedas(Double monedas) {
        this.monedas = monedas;
    }

    public Double getTotalMonedas() {
        return totalMonedas;
    }

    public void setTotalMonedas(Double totalMonedas) {
        this.totalMonedas = totalMonedas;
    }

    public Double getBonoAgencia() {
        return bonoAgencia;
    }

    public void setBonoAgencia(Double bonoAgencia) {
        this.bonoAgencia = bonoAgencia;
    }

    public Double getRecompensaEvento() {
        return recompensaEvento;
    }

    public void setRecompensaEvento(Double recompensaEvento) {
        this.recompensaEvento = recompensaEvento;
    }

    public Double getBonusTop100() {
        return bonusTop100;
    }

    public void setBonusTop100(Double bonusTop100) {
        this.bonusTop100 = bonusTop100;
    }

    public Double getLoyaltyCredits() {
        return loyaltyCredits;
    }

    public void setLoyaltyCredits(Double loyaltyCredits) {
        this.loyaltyCredits = loyaltyCredits;
    }

    public Integer getSemana() {
        return semana;
    }

    public void setSemana(Integer semana) {
        this.semana = semana;
    }

    public String getPais() {
        return pais;
    }

    public void setPais(String pais) {
        this.pais = pais;
    }

    public String getWhatsapp() {
        return whatsapp;
    }

    public void setWhatsapp(String whatsapp) {
        this.whatsapp = whatsapp;
    }

    public String getFuente() {
        return fuente;
    }

    public void setFuente(String fuente) {
        this.fuente = fuente;
    }

    public String getSheet() {
        return sheet;
    }

    public void setSheet(String sheet) {
        this.sheet = sheet;
    }

    public String getPeriodoComparacion() {
        return periodoComparacion;
    }

    public void setPeriodoComparacion(String periodoComparacion) {
        this.periodoComparacion = periodoComparacion;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    /**
     * Calcula el total de ingresos sumando todas las fuentes
     */
    public Double getTotalIngresos() {
        double total = 0.0;
        if (totalMonedas != null)
            total += totalMonedas;
        if (bonoAgencia != null)
            total += bonoAgencia;
        if (recompensaEvento != null)
            total += recompensaEvento;
        return total;
    }

    @Override
    public String toString() {
        return "HistoricoIngreso{" +
                "id=" + id +
                ", identificacion='" + identificacion + '\'' +
                ", nombreCompleto='" + nombreCompleto + '\'' +
                ", totalMonedas=" + totalMonedas +
                ", fechaRegistro=" + fechaRegistro +
                '}';
    }
}
