# Comparador de Archivos CSV y Excel

Una aplicación web para comparar IDs entre archivos CSV y Excel.

## 🚀 Características

- ✅ Comparación de IDs entre archivos CSV y Excel
- 📊 Visualización clara de resultados
- 🎨 Interfaz moderna y responsive
- ☁️ Listo para desplegar en la nube

## 📋 Requisitos

- Java 17 o superior
- Maven 3.6 o superior

## 🛠️ Instalación y Ejecución Local

1. Clonar el repositorio
2. Ejecutar la aplicación:

```bash
./mvnw spring-boot:run
```

3. Abrir en el navegador: `http://localhost:8080`

## 🌐 Despliegue en Google Cloud Run

### Opción 1: Usando gcloud CLI

1. Instalar Google Cloud SDK: https://cloud.google.com/sdk/docs/install

2. Autenticarse:
```bash
gcloud auth login
gcloud config set project TU_PROJECT_ID
```

3. Construir y desplegar:
```bash
gcloud run deploy comparador-archivos --source . --region us-central1 --allow-unauthenticated
```

### Opción 2: Usando Docker

1. Construir la imagen:
```bash
docker build -t comparador-archivos .
```

2. Probar localmente:
```bash
docker run -p 8080:8080 comparador-archivos
```

3. Subir a Google Cloud:
```bash
# Etiquetar la imagen
docker tag comparador-archivos gcr.io/TU_PROJECT_ID/comparador-archivos

# Subir a Google Container Registry
docker push gcr.io/TU_PROJECT_ID/comparador-archivos

# Desplegar en Cloud Run
gcloud run deploy comparador-archivos \
  --image gcr.io/TU_PROJECT_ID/comparador-archivos \
  --region us-central1 \
  --allow-unauthenticated
```

## 📝 Uso

1. Acceder a la aplicación
2. Cargar un archivo CSV (debe tener una columna "ID" o "id")
3. Cargar un archivo Excel (.xlsx o .xls)
4. Hacer clic en "Comparar Archivos"
5. Ver los resultados:
   - IDs que coinciden en ambos archivos
   - IDs solo en CSV
   - IDs solo en Excel

## 🔧 Configuración

El archivo `application.properties` contiene la configuración básica:
- Tamaño máximo de archivo: 10MB
- Puerto: 8080

## 📦 Estructura del Proyecto

```
src/
├── main/
│   ├── java/com/link/comparar/
│   │   ├── CompararApplication.java
│   │   ├── controller/
│   │   │   └── FileComparisonController.java
│   │   ├── service/
│   │   │   └── FileComparisonService.java
│   │   └── model/
│   │       └── ComparisonResult.java
│   └── resources/
│       ├── templates/
│       │   ├── index.html
│       │   └── result.html
│       └── application.properties
```

## 🤝 Contribuciones

Las contribuciones son bienvenidas. Por favor, abre un issue primero para discutir los cambios propuestos.

## 📄 Licencia

Este proyecto es de código abierto y está disponible bajo la licencia MIT.
