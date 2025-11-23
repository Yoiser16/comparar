# 🚀 Guía de Despliegue a Google Cloud Run

Esta guía te ayudará a desplegar tu aplicación Spring Boot en Google Cloud Run para que esté disponible públicamente.

## 📋 Requisitos previos

1. **Cuenta de Google Cloud**
   - Crear cuenta gratuita: https://cloud.google.com/free
   - Tienes $300 de crédito gratis por 90 días
   - Cloud Run tiene capa gratuita permanente (2M peticiones/mes)

2. **Instalar Google Cloud CLI**
   - Windows: https://dl.google.com/dl/cloudsdk/channels/rapid/GoogleCloudSDKInstaller.exe
   - Mac: `brew install --cask google-cloud-sdk`
   - Linux: `curl https://sdk.cloud.google.com | bash`

3. **Instalar Docker Desktop**
   - Windows/Mac: https://www.docker.com/products/docker-desktop
   - Linux: `sudo apt install docker.io` (Ubuntu/Debian)

## 🔧 Configuración inicial (solo una vez)

### 1. Inicializar gcloud CLI

```cmd
gcloud init
```

Esto te pedirá:
- Iniciar sesión con tu cuenta de Google
- Seleccionar o crear un proyecto de Google Cloud
- Configurar región predeterminada (elige `us-central1` o la más cercana)

### 2. Habilitar APIs necesarias

```cmd
gcloud services enable run.googleapis.com
gcloud services enable containerregistry.googleapis.com
```

### 3. Configurar autenticación de Docker

```cmd
gcloud auth configure-docker
```

### 4. Obtener ID de tu proyecto

```cmd
gcloud config get-value project
```

Copia el ID del proyecto (ej: `mi-proyecto-12345`)

## 📝 Configurar el script de despliegue

1. Abre `deploy-cloudrun.cmd` (Windows) o `deploy-cloudrun.sh` (Linux/Mac)
2. Edita la línea:
   ```cmd
   set PROJECT_ID=tu-proyecto-id
   ```
   Reemplaza `tu-proyecto-id` con el ID que copiaste en el paso anterior.

3. (Opcional) Personaliza el nombre del servicio:
   ```cmd
   set SERVICE_NAME=comparador-archivos
   ```

4. (Opcional) Cambia la región si lo deseas:
   ```cmd
   set REGION=us-central1
   ```
   Regiones disponibles: https://cloud.google.com/run/docs/locations

## 🚀 Desplegar

### Windows

```cmd
deploy-cloudrun.cmd
```

### Linux/Mac

```bash
chmod +x deploy-cloudrun.sh
./deploy-cloudrun.sh
```

El script hará:
1. ✅ Verificar autenticación
2. 🐳 Construir imagen Docker
3. ⬆️ Subir imagen a Google Container Registry
4. 🌐 Desplegar a Cloud Run

Al finalizar, verás la URL pública de tu app:
```
https://comparador-archivos-xxxxx-uc.a.run.app
```

## 🔄 Actualizaciones

Cada vez que hagas cambios en el código:

1. Guarda los cambios
2. Ejecuta de nuevo el script de despliegue
3. Cloud Run creará una nueva versión automáticamente

## 💰 Costos

Con la capa gratuita de Cloud Run:
- **2 millones** de peticiones/mes gratis
- **360,000** GB-segundos de memoria gratis
- **180,000** vCPU-segundos gratis

Tu app consume ~0.5 GB RAM y solo paga mientras procesa peticiones (no cuando está inactiva).

**Estimación**: Para uso típico empresarial (<10,000 comparaciones/mes), probablemente **100% gratis**.

## 🔒 Seguridad

Por defecto, la app es pública (`--allow-unauthenticated`).

Para requerir autenticación:
1. En `deploy-cloudrun.cmd`, cambia:
   ```cmd
   --allow-unauthenticated
   ```
   por:
   ```cmd
   --no-allow-unauthenticated
   ```

2. Configura Identity-Aware Proxy: https://cloud.google.com/iap/docs/enabling-cloud-run

## 📊 Monitoreo

Ver logs en tiempo real:
```cmd
gcloud run services logs read comparador-archivos --region=us-central1
```

Ver métricas (tráfico, latencia, errores):
```cmd
gcloud run services describe comparador-archivos --region=us-central1
```

O visita la consola web:
https://console.cloud.google.com/run

## 🛠️ Solución de problemas

### Error: "Project not found"
```cmd
gcloud config set project TU-PROJECT-ID
```

### Error: "permission denied" en Docker
Windows: Ejecuta PowerShell/CMD como Administrador
Linux/Mac: `sudo usermod -aG docker $USER` y reinicia sesión

### Error: "API not enabled"
```cmd
gcloud services enable run.googleapis.com containerregistry.googleapis.com
```

### Build falla por falta de memoria
Edita `deploy-cloudrun.cmd` y aumenta memoria:
```cmd
--memory 1Gi
```

## 📞 Soporte

- Documentación oficial: https://cloud.google.com/run/docs
- Precios: https://cloud.google.com/run/pricing
- Foro: https://stackoverflow.com/questions/tagged/google-cloud-run

---

¿Listo para desplegar? Ejecuta `deploy-cloudrun.cmd` y en ~3 minutos tendrás tu app en producción 🎉
