# 🚀 Guía de Despliegue en Google Cloud Run

Esta guía te ayudará a desplegar tu aplicación de comparación de archivos en Google Cloud Run, para que esté disponible públicamente en Internet.

## 📋 Requisitos Previos

1. **Cuenta de Google Cloud**: Crear una cuenta en [Google Cloud](https://cloud.google.com/)
   - Los nuevos usuarios obtienen $300 de créditos gratuitos
   - Cloud Run tiene un nivel gratuito generoso

2. **Google Cloud SDK**: Instalar desde [aquí](https://cloud.google.com/sdk/docs/install)

## 🎯 Opción 1: Despliegue Rápido (Recomendado)

### Paso 1: Configuración Inicial

1. Abre una terminal en la carpeta del proyecto
2. Autentícate en Google Cloud:
```bash
gcloud auth login
```

3. Crea un nuevo proyecto o selecciona uno existente:
```bash
gcloud projects create MI-PROYECTO-COMPARADOR --name="Comparador de Archivos"
gcloud config set project MI-PROYECTO-COMPARADOR
```

4. Habilita las APIs necesarias:
```bash
gcloud services enable run.googleapis.com
gcloud services enable cloudbuild.googleapis.com
```

### Paso 2: Desplegar

**En Windows**, ejecuta:
```bash
deploy.cmd
```

**En Linux/Mac**, ejecuta:
```bash
chmod +x deploy.sh
./deploy.sh
```

Sigue las instrucciones en pantalla. El script te pedirá:
- Tu Project ID de Google Cloud
- La región donde quieres desplegar

### Paso 3: Acceder a tu Aplicación

Una vez completado el despliegue, verás una URL como:
```
https://comparador-archivos-XXXXXXXXX-uc.a.run.app
```

¡Esa es la URL pública de tu aplicación! 🎉

## 🎯 Opción 2: Despliegue Manual

Si prefieres hacerlo manualmente, sigue estos pasos:

```bash
# 1. Autenticarse
gcloud auth login

# 2. Configurar proyecto
gcloud config set project TU_PROJECT_ID

# 3. Habilitar APIs
gcloud services enable run.googleapis.com cloudbuild.googleapis.com

# 4. Desplegar
gcloud run deploy comparador-archivos \
  --source . \
  --region us-central1 \
  --allow-unauthenticated \
  --memory 512Mi
```

## 🌍 Regiones Disponibles

Elige la región más cercana a tus usuarios:

| Región | Ubicación | Código |
|--------|-----------|--------|
| 🇺🇸 Estados Unidos (Centro) | Iowa | `us-central1` |
| 🇺🇸 Estados Unidos (Este) | South Carolina | `us-east1` |
| 🇪🇺 Europa (Oeste) | Bélgica | `europe-west1` |
| 🇯🇵 Asia (Noreste) | Tokio | `asia-northeast1` |
| 🇧🇷 Sudamérica | São Paulo | `southamerica-east1` |

## 💰 Costos

Cloud Run tiene un nivel gratuito que incluye:
- 2 millones de solicitudes por mes
- 360,000 GB-segundos de memoria
- 180,000 vCPU-segundos

Para esta aplicación, el uso normal estará **completamente dentro del nivel gratuito**.

## 🔧 Configuraciones Adicionales

### Aumentar Memoria o CPU

Si necesitas más recursos, modifica el comando de despliegue:

```bash
gcloud run deploy comparador-archivos \
  --source . \
  --region us-central1 \
  --allow-unauthenticated \
  --memory 1Gi \
  --cpu 2
```

### Configurar Dominio Personalizado

1. Ve a la consola de Cloud Run
2. Selecciona tu servicio
3. Haz clic en "Manage Custom Domains"
4. Sigue las instrucciones para agregar tu dominio

### Agregar Autenticación

Para requerir autenticación de Google:

```bash
gcloud run deploy comparador-archivos \
  --source . \
  --region us-central1 \
  --no-allow-unauthenticated
```

Luego agrega permisos a usuarios específicos:

```bash
gcloud run services add-iam-policy-binding comparador-archivos \
  --region us-central1 \
  --member="user:email@example.com" \
  --role="roles/run.invoker"
```

## 📊 Monitoreo

Para ver logs y métricas:

1. **Logs en tiempo real**:
```bash
gcloud run services logs read comparador-archivos --region us-central1
```

2. **Consola web**: Ve a [Cloud Console](https://console.cloud.google.com/run)

## 🔄 Actualizar la Aplicación

Simplemente vuelve a ejecutar el script de despliegue:

```bash
deploy.cmd    # En Windows
./deploy.sh   # En Linux/Mac
```

O manualmente:
```bash
gcloud run deploy comparador-archivos --source . --region us-central1
```

## ❌ Eliminar la Aplicación

Para eliminar el servicio y dejar de incurrir en costos:

```bash
gcloud run services delete comparador-archivos --region us-central1
```

## 🆘 Solución de Problemas

### Error: "API not enabled"
```bash
gcloud services enable run.googleapis.com cloudbuild.googleapis.com
```

### Error: "Permission denied"
Asegúrate de tener los permisos necesarios en el proyecto:
```bash
gcloud projects add-iam-policy-binding TU_PROJECT_ID \
  --member="user:tu-email@gmail.com" \
  --role="roles/run.admin"
```

### La aplicación no carga
Verifica los logs:
```bash
gcloud run services logs read comparador-archivos --region us-central1 --limit 50
```

## 🔗 Enlaces Útiles

- [Documentación de Cloud Run](https://cloud.google.com/run/docs)
- [Precios de Cloud Run](https://cloud.google.com/run/pricing)
- [Consola de Cloud Run](https://console.cloud.google.com/run)
- [Cloud SDK](https://cloud.google.com/sdk/docs)

## 📞 Soporte

Si tienes problemas:
1. Revisa los logs con el comando indicado arriba
2. Consulta la [documentación oficial](https://cloud.google.com/run/docs)
3. Busca en [Stack Overflow](https://stackoverflow.com/questions/tagged/google-cloud-run)

---

¡Disfruta de tu aplicación desplegada en la nube! 🎉
