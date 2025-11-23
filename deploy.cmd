@echo off
REM Script para desplegar en Google Cloud Run

echo ========================================
echo  Despliegue en Google Cloud Run
echo ========================================
echo.

REM Verificar si gcloud está instalado
where gcloud >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Google Cloud SDK no está instalado.
    echo Por favor, instalalo desde: https://cloud.google.com/sdk/docs/install
    pause
    exit /b 1
)

REM Solicitar el Project ID
set /p PROJECT_ID="Ingresa tu Google Cloud Project ID: "
if "%PROJECT_ID%"=="" (
    echo ERROR: Debes proporcionar un Project ID
    pause
    exit /b 1
)

REM Configurar el proyecto
echo.
echo Configurando proyecto...
gcloud config set project %PROJECT_ID%

REM Solicitar región
echo.
echo Regiones disponibles:
echo 1. us-central1 (Iowa, USA)
echo 2. us-east1 (South Carolina, USA)
echo 3. europe-west1 (Bélgica)
echo 4. asia-northeast1 (Tokio, Japón)
echo 5. southamerica-east1 (São Paulo, Brasil)
echo.
set /p REGION_OPTION="Selecciona la región (1-5): "

if "%REGION_OPTION%"=="1" set REGION=us-central1
if "%REGION_OPTION%"=="2" set REGION=us-east1
if "%REGION_OPTION%"=="3" set REGION=europe-west1
if "%REGION_OPTION%"=="4" set REGION=asia-northeast1
if "%REGION_OPTION%"=="5" set REGION=southamerica-east1

if "%REGION%"=="" (
    echo Opción inválida. Usando us-central1 por defecto.
    set REGION=us-central1
)

REM Nombre del servicio
set SERVICE_NAME=comparador-archivos

echo.
echo Desplegando aplicación...
echo Proyecto: %PROJECT_ID%
echo Región: %REGION%
echo Servicio: %SERVICE_NAME%
echo.

REM Desplegar en Cloud Run
gcloud run deploy %SERVICE_NAME% ^
    --source . ^
    --region %REGION% ^
    --platform managed ^
    --allow-unauthenticated ^
    --memory 512Mi ^
    --cpu 1 ^
    --max-instances 10

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo  ¡Despliegue exitoso!
    echo ========================================
    echo.
    echo Tu aplicación está disponible en la URL que aparece arriba.
    echo.
) else (
    echo.
    echo ERROR: El despliegue falló.
    echo Verifica los mensajes de error anteriores.
)

pause
