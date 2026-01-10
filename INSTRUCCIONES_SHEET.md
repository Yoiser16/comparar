# Agregar campo Sheet al Histórico

Para agregar el campo que identifica si el registro proviene de SALSA, LIVEJOY u OLIVE:

## 1. Modificar HistoricoIngreso.java

Agregar el campo después de `fuente`:

```java
@Column(name = "sheet", length = 50)
private String sheet; // SALSA, LIVEJOY, OLIVE
```

Actualizar el constructor agregando el parámetro `String sheet` entre `fuente` y `periodoComparacion`:

```java
public HistoricoIngreso(String identificacion, String nombreCompleto, Double monedas, Double totalMonedas,
        Double bonoAgencia, Double recompensaEvento, Double bonusTop100, Double loyaltyCredits, Integer semana,
        String pais, String whatsapp, String fuente, String sheet, String periodoComparacion) {
```

Y dentro del constructor agregar:
```java
this.sheet = sheet;
```

Agregar getters y setters después del método `getFuente()`:

```java
public String getSheet() {
    return sheet;
}

public void setSheet(String sheet) {
    this.sheet = sheet;
}
```

## 2. Modificar HistoricoService.java

En el método `guardarRegistrosConIngresos`, agregar extracción del sheet:

```java
String sheet = obtenerValor(data, "Sheet");
```

Y actualizar la creación del objeto:

```java
HistoricoIngreso historico = new HistoricoIngreso(
        id, nombreCompleto, monedas, totalMonedas, bonoAgencia,
        recompensaEvento, bonusTop100, loyaltyCredits, semana, pais, whatsapp, fuente, sheet, periodoComparacion);
```

## 3. Modificar historico.html

Agregar columna en el encabezado después de "País":

```html
<th class="px-4 py-3 text-left text-xs font-bold text-slate-700 dark:text-slate-200 uppercase tracking-wider border border-slate-300 dark:border-gray-600">Sheet</th>
```

Agregar celda en las filas después de la celda de "País":

```html
<td class="px-4 py-3 whitespace-nowrap text-sm text-slate-700 dark:text-slate-300 border border-slate-300 dark:border-gray-600" th:text="${registro.sheet != null ? registro.sheet : '-'}">-</td>
```

Actualizar colspan en el mensaje vacío de 12 a 13.
