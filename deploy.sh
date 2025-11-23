#!/bin/bash
# Script para desplegar en Google Cloud Run (Linux/Mac)

echo "========================================"
echo " Despliegue en Google Cloud Run"
echo "========================================"
echo

# Verificar si gcloud está instalado
if ! command -v gcloud &> /dev/null; then
    echo "ERROR: Google Cloud SDK no está instalado."
    echo "Por favor, instálalo desde: https://cloud.google.com/sdk/docs/install"
    exit 1
fi

# Solicitar el Project ID
read -p "Ingresa tu Google Cloud Project ID: " PROJECT_ID
if [ -z "$PROJECT_ID" ]; then
    echo "ERROR: Debes proporcionar un Project ID"
    exit 1
fi

# Configurar el proyecto
echo
echo "Configurando proyecto..."
gcloud config set project $PROJECT_ID

# Solicitar región
echo
echo "Regiones disponibles:"
echo "1. us-central1 (Iowa, USA)"
echo "2. us-east1 (South Carolina, USA)"
echo "3. europe-west1 (Bélgica)"
echo "4. asia-northeast1 (Tokio, Japón)"
echo "5. southamerica-east1 (São Paulo, Brasil)"
echo
read -p "Selecciona la región (1-5): " REGION_OPTION

case $REGION_OPTION in
    1) REGION="us-central1" ;;
    2) REGION="us-east1" ;;
    3) REGION="europe-west1" ;;
    4) REGION="asia-northeast1" ;;
    5) REGION="southamerica-east1" ;;
    *) echo "Opción inválida. Usando us-central1 por defecto."
       REGION="us-central1" ;;
esac

# Nombre del servicio
SERVICE_NAME="comparador-archivos"

echo
echo "Desplegando aplicación..."
echo "Proyecto: $PROJECT_ID"
echo "Región: $REGION"
echo "Servicio: $SERVICE_NAME"
echo

# Desplegar en Cloud Run
gcloud run deploy $SERVICE_NAME \
    --source . \
    --region $REGION \
    --platform managed \
    --allow-unauthenticated \
    --memory 512Mi \
    --cpu 1 \
    --max-instances 10

if [ $? -eq 0 ]; then
    echo
    echo "========================================"
    echo " ¡Despliegue exitoso!"
    echo "========================================"
    echo
    echo "Tu aplicación está disponible en la URL que aparece arriba."
    echo
else
    echo
    echo "ERROR: El despliegue falló."
    echo "Verifica los mensajes de error anteriores."
    exit 1
fi
