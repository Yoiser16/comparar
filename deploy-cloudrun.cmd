@echo off
REM Script de despliegue a Google Cloud Run
REM Asegurate de tener instalado gcloud CLI: https://cloud.google.com/sdk/docs/install

echo ========================================
echo  Desplegando a Google Cloud Run
echo ========================================
echo.

REM Configuracion
set PROJECT_ID=tu-proyecto-id
set SERVICE_NAME=comparador-archivos
set REGION=us-central1
set IMAGE_NAME=gcr.io/%PROJECT_ID%/%SERVICE_NAME%

echo [1/4] Verificando autenticacion...
gcloud auth list
if %ERRORLEVEL% NEQ 0 (
    echo Error: No estas autenticado. Ejecuta: gcloud auth login
    pause
    exit /b 1
)

echo.
echo [2/4] Construyendo imagen Docker...
docker build -t %IMAGE_NAME% .
if %ERRORLEVEL% NEQ 0 (
    echo Error al construir imagen Docker
    pause
    exit /b 1
)

echo.
echo [3/4] Subiendo imagen a Google Container Registry...
docker push %IMAGE_NAME%
if %ERRORLEVEL% NEQ 0 (
    echo Error al subir imagen. Ejecuta: gcloud auth configure-docker
    pause
    exit /b 1
)

echo.
echo [4/4] Desplegando a Cloud Run...
gcloud run deploy %SERVICE_NAME% ^
    --image %IMAGE_NAME% ^
    --platform managed ^
    --region %REGION% ^
    --allow-unauthenticated ^
    --port 8080 ^
    --memory 512Mi ^
    --cpu 1 ^
    --min-instances 0 ^
    --max-instances 10 ^
    --project %PROJECT_ID%

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo  Despliegue exitoso!
    echo ========================================
    echo.
    gcloud run services describe %SERVICE_NAME% --region %REGION% --project %PROJECT_ID% --format="value(status.url)"
) else (
    echo Error al desplegar a Cloud Run
    pause
    exit /b 1
)

pause
