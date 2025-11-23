# Archivos de Ejemplo para Pruebas

Esta carpeta contiene archivos de ejemplo para probar la funcionalidad de comparación.

## Archivos Incluidos

### ejemplo-csv.csv
Archivo CSV con 10 IDs de ejemplo (1-10)

### ejemplo-excel.xlsx
Crea este archivo manualmente en Excel con la siguiente estructura:

| id | nombre | departamento |
|----|--------|--------------|
| 2 | María García | Ventas |
| 3 | Carlos López | IT |
| 5 | Pedro Sánchez | RRHH |
| 8 | Isabel González | Marketing |
| 11 | Roberto Díaz | Finanzas |
| 12 | Sofía Moreno | Operaciones |

## Resultado Esperado

Al comparar estos dos archivos:

- **IDs Coincidentes**: 2, 3, 5, 8 (4 coincidencias)
- **Solo en CSV**: 1, 4, 6, 7, 9, 10
- **Solo en Excel**: 11, 12

## Cómo Usar

1. Descarga o crea ambos archivos
2. Accede a la aplicación
3. Sube `ejemplo-csv.csv` como archivo CSV
4. Sube `ejemplo-excel.xlsx` como archivo Excel
5. Haz clic en "Comparar Archivos"
6. Verifica que los resultados coincidan con lo esperado

## Notas Importantes

- La columna "id" puede estar en mayúsculas o minúsculas
- Si no existe una columna llamada "id", se usará la primera columna
- Los espacios al inicio y final de los IDs se eliminan automáticamente
- Los IDs se comparan como texto (case-sensitive)
