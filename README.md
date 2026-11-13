# L200 Dash v0.3

Launcher propio para la pantalla Android (TS18 · Android 10) de la L200.
Paquete: com.fvision.camera (necesario para controlar la dashcam USB).

- Inicio editable estilo Nova: páginas, cuadrícula, mover / cambiar tamaño / quitar,
  widgets propios (mapa, cámara, música, medidores, velocímetro, reloj, clima, Waze),
  apps, carpetas, widgets de Android, dock editable, cajón con búsqueda y apps ocultas
- Dashcam integrada: video en vivo, grabar, guardar clip, foto, micrófono, hora automática
- Mapa: Google Maps con tráfico (secreto MAPS_API_KEY) o CARTO / relieve sin key
- Waze en pantalla dividida (servicio de accesibilidad) + tablero compacto
- Personalizar: colores, borde, brillo, texto, imagen central, día/noche, temas, cuadrícula

Build: GitHub Actions firma con los secretos KEYSTORE_BASE64, KEYSTORE_PASSWORD,
KEY_ALIAS, KEY_PASSWORD y agrega MAPS_API_KEY. El APK queda en Releases (L200Dash.apk).

Nota: incluye la librería nativa de la dashcam (libuvccamera-lib.so). Uso personal:
mantener este repositorio privado.
