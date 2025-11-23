#!/bin/bash
# Script de despliegue a Google Cloud Run (Linux/Mac)
# Asegurate de tener instalado gcloud CLI: https://cloud.google.com/sdk/docs/install

set -e

echo "========================================"
echo " Desplegando a Google Cloud Run"
echo "========================================"
echo

# Configuracion
PROJECT_ID="tu-proyecto-id"
SERVICE_NAME="comparador-archivos"
REGION="us-central1"
IMAGE_NAME="gcr.io/$PROJECT_ID/$SERVICE_NAME"

echo "[1/4] Verificando autenticacion..."
gcloud auth list

echo
echo "[2/4] Construyendo imagen Docker..."
docker build -t $IMAGE_NAME .

echo
echo "[3/4] Subiendo imagen a Google Container Registry..."
docker push $IMAGE_NAME

echo
echo "[4/4] Desplegando a Cloud Run..."
gcloud run deploy $SERVICE_NAME \
    --image $IMAGE_NAME \
    --platform managed \
    --region $REGION \
    --allow-unauthenticated \
    --port 8080 \
    --memory 512Mi \
    --cpu 1 \
    --min-instances 0 \
    --max-instances 10 \
    --project $PROJECT_ID

echo
echo "========================================"
echo " Despliegue exitoso!"
echo "========================================"
echo
gcloud run services describe $SERVICE_NAME --region $REGION --project $PROJECT_ID --format="value(status.url)"
