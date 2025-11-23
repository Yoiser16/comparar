# 🚀 Guía de Inicio Rápido

## ⚡ Ejecución Local (Pruebas)

### Windows
1. Haz doble clic en `run-local.cmd`
2. Espera a que compile (primera vez puede tardar unos minutos)
3. Abre tu navegador en: http://localhost:8080
4. Para detener: presiona `Ctrl+C` en la terminal

### Linux/Mac
```bash
chmod +x run-local.sh
./run-local.sh
```

## 🌐 Despliegue en la Nube (Acceso Público)

### Pasos Rápidos:

1. **Instala Google Cloud SDK**
   - Windows: https://cloud.google.com/sdk/docs/install
   - Descarga e instala siguiendo el asistente

2. **Crea un proyecto en Google Cloud**
   - Ve a: https://console.cloud.google.com
   - Crea una cuenta (obtienes $300 de crédito gratis)
   - Crea un nuevo proyecto

3. **Despliega la aplicación**
   
   **Windows:**
   ```cmd
   deploy.cmd
   ```
   
   **Linux/Mac:**
   ```bash
   chmod +x deploy.sh
   ./deploy.sh
   ```

4. **¡Listo!**
   - El script te dará una URL pública como:
   - `https://comparador-archivos-xxx.a.run.app`
   - Comparte esta URL con quien quieras

### 💡 Ventajas de Google Cloud Run:

✅ **GRATIS** para uso normal (2 millones de peticiones/mes)
✅ **SIN servidor** - No tienes que mantener nada
✅ **Escalable** - Soporta muchos usuarios automáticamente
✅ **HTTPS incluido** - Seguro por defecto
✅ **URL pública** - Accesible desde cualquier lugar

## 📝 Cómo Usar la Aplicación

1. Abre la URL (local o en la nube)
2. Haz clic en "Seleccionar archivo CSV"
3. Haz clic en "Seleccionar archivo Excel"
4. Presiona "Comparar Archivos"
5. ¡Ve los resultados!

### Requisitos de los Archivos:

- **CSV**: Debe tener una columna llamada "id" o "ID"
- **Excel**: Debe tener una columna llamada "id" o "ID"
- Si no tienen esa columna, se usa la primera columna
- Tamaño máximo: 10MB por archivo

## 🧪 Archivos de Prueba

Usa `ejemplo-csv.csv` incluido en el proyecto para probar.
Crea un archivo Excel con algunos de los mismos IDs y otros diferentes.

## 📚 Más Información

- **README.md**: Información completa del proyecto
- **DEPLOYMENT_GUIDE.md**: Guía detallada de despliegue
- **EJEMPLOS.md**: Ejemplos de archivos para probar

## 🆘 Problemas Comunes

### "No se reconoce mvnw como comando"
- Asegúrate de estar en la carpeta del proyecto
- Usa `run-local.cmd` (Windows) o `./run-local.sh` (Linux/Mac)

### "Port 8080 already in use"
- Cierra otras aplicaciones que usen el puerto 8080
- O cambia el puerto en `application.properties`

### Error al desplegar en Cloud
- Verifica que instalaste Google Cloud SDK
- Ejecuta `gcloud auth login` primero
- Lee `DEPLOYMENT_GUIDE.md` para más detalles

## 💰 Costos

- **Local**: Completamente GRATIS
- **Google Cloud Run**: 
  - Nivel gratuito: 2M peticiones/mes
  - Esta aplicación: **GRATIS** en uso normal
  - Solo pagas si tienes MUCHO tráfico

## 📞 Contacto

Si tienes dudas, revisa la documentación completa en los archivos `.md` incluidos.

---

¡Disfruta comparando archivos! 🎉
