@echo off
echo ========================================
echo  Iniciando Aplicación de Comparación
echo ========================================
echo.
echo Compilando y ejecutando la aplicación...
echo La aplicación estará disponible en: http://localhost:8080
echo.
echo Presiona Ctrl+C para detener la aplicación
echo.

call mvnw.cmd spring-boot:run

pause
