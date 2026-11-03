# IptvUp

Reproductor IPTV privado para **Android** y **Android TV**, con perfiles sincronizados en la nube, login, panel de administración web y actualizaciones in-app sin Google Play.

> Estado: primera base funcional. La app compila, el panel web compila y los parsers M3U/Xtream/XMLTV tienen tests. Falta configurar tu proyecto de Firebase y probar con tu lista real.

---

## Qué incluye

### App Android / Android TV

- Login con email y contraseña (Firebase Auth).
- Perfiles IPTV en la nube: **M3U** o **Xtream Codes**.
- Lista de canales por grupos, búsqueda, favoritos y ocultación de grupos.
- **Actualización manual**: la lista y la EPG se guardan en caché y solo se actualizan al pulsar el botón de actualizar o la primera vez que no hay canales descargados.
- **Botón de actualización en Inicio y TV**: descarga todos los canales y la EPG cuando lo pulsas.
- **Orden de grupos personalizado**: mantén pulsado un grupo o usa el icono de ordenar para subirlo/bajarlo; el orden se guarda en tu cuenta y se aplica al entrar en el perfil.
- **Cambio automático de calidad**: si un canal no empieza a reproducirse en 15 segundos, avisa a los 10 con cuenta atrás y prueba automáticamente la siguiente variante visible del mismo canal (4K → 1080 → 720 → SD).
- **Tarjeta de eventos deportivos** en Inicio: partidos de fútbol del día con hora, escudos, competición y acceso directo al canal visible de mejor calidad (4K → 1080/FHD → 720/HD → SD).
- **Arranque en el último perfil usado**.
- **Iconos de perfil personalizables**: toca el avatar de cada lista para elegir entre varios iconos.
- En **móvil y Android TV**, la pestaña TV muestra primero los grupos como tarjetas con icono; al entrar en un grupo aparece su lista de canales con botón de volver.
- **Sin actualización automática diaria**: al entrar en un perfil se usa siempre la caché; solo se descarga todo si no hay canales guardados o si pulsas actualizar manualmente.
- **EPG solo de grupos visibles**: solo se descarga la EPG del proveedor para los canales de grupos visibles; no hay fuentes de respaldo.
- Reproductor **Media3/ExoPlayer** para HLS y TS.
- **Guía EPG** (XMLTV) con ahora/después, parrilla temporal en móvil, lista adaptada a TV, buscador de canales y el mismo gestor multi-grupo que TV.
- Pantalla de inicio con **historial de los últimos 10 canales abiertos**.
- Edición completa de perfiles (nombre, tipo, credenciales y EPG).
- **Ajustes globales** desde la selección de perfiles: actualizaciones y acerca de.
- **Picture-in-Picture** y **Chromecast** en móvil (el botón de Cast usa el receptor multimedia por defecto de Google).
- **Gestos en el reproductor**: desliza en horizontal para cambiar de canal, en vertical derecho para el volumen y en vertical izquierdo para el brillo. En TV, D-pad izquierda/derecha cambia de canal.
- **Guía EPG ampliable** dentro del reproductor, sin salir de la reproducción.
- Ajustes y gestor masivo de grupos visibles.
- UI oscura, adaptada a móvil y Android TV (D-pad, rail lateral, foco visible).
- Actualizador in-app: descarga el APK desde GitHub Releases, verifica SHA-256 y firma, y abre el instalador.
- Caché local con Room; la playlist y el EPG no se suben a la nube.

### Panel web

- Login solo para administradores.
- Dashboard con usuarios, activos, desactivados y perfiles.
- Alta de usuarios, activar/desactivar, borrar, resetear contraseña y dar/quitar admin.
- Detalle de usuario con sus perfiles IPTV (sin credenciales).
- API que cifra las credenciales con AES-256-GCM antes de guardarlas en Firestore.

### Infraestructura

- Firebase Spark: Auth + Firestore, sin pausas por inactividad.
- Vercel Hobby para el panel y la API.
- GitHub Releases para distribuir el APK.
- Coste obligatorio: **0 €** (dominio opcional).

---

## Estructura

```
android/                 App Kotlin + Compose + Media3
  app/
  build.gradle.kts
web/                     Panel Next.js + API de credenciales
  app/
  components/
  lib/
  scripts/set-admin.mjs
firebase/                Reglas y configuración de Firestore
updates/latest.json      Manifiesto del actualizador in-app
.github/workflows/       Build de APK y releases
PLAN.md                  Plan técnico completo
```

---

## Requisitos

- Android Studio con JDK 17 y SDK 35.
- Node.js 22+ y npm.
- Cuenta de Google con Firebase.
- Cuenta de GitHub y Vercel.
- Un móvil Android y, recomendable, un Android TV / Google TV stick.
- Una lista M3U o credenciales Xtream **legales** para probar.

---

## 1. Configurar Firebase

1. Crea un proyecto en [Firebase Console](https://console.firebase.google.com).
2. Añade una app Android con el paquete `com.iptvup.app`.
3. Descarga `google-services.json` y sustitúyelo por el de demo:
   `android/app/google-services.json`.
4. En **Authentication → Sign-in method**, activa **Email/Password**.
   No hay registro público en la app: los usuarios se crean desde el panel. La app
   rechaza cualquier cuenta de Auth que no tenga su documento autorizado en
   Firestore, aunque alguien llame al registro de Firebase por API.
5. Crea **Firestore Database** en modo producción.
6. Despliega las reglas:

   ```bash
   npm install -g firebase-tools
   firebase login
   cd firebase
   # Edita .firebaserc y pon tu project id
   firebase deploy --only firestore:rules
   ```

7. En **Project settings → Service accounts**, genera una clave privada.
   La usarás en el panel web.

> El `google-services.json` de demo solo sirve para compilar. La app no podrá autenticar hasta que pongas el tuyo.

---

## 2. Configurar el panel web

```bash
cd web
cp .env.example .env.local
npm install
```

Rellena `web/.env.local`:

```env
# Web API Key: Firebase Console → Project settings → General → Web API Key
NEXT_PUBLIC_FIREBASE_API_KEY=...
NEXT_PUBLIC_FIREBASE_PROJECT_ID=tu-proyecto

FIREBASE_PROJECT_ID=tu-proyecto
FIREBASE_CLIENT_EMAIL=firebase-adminsdk-...@tu-proyecto.iam.gserviceaccount.com
FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"

CREDENTIALS_KEY=...
```

El panel usa la API REST de Identity Toolkit para el login, así que no necesita el SDK web de Firebase ni un dominio de Auth. Solo la **Web API Key**.

Para desarrollo local puedes usar `GOOGLE_APPLICATION_CREDENTIALS` apuntando al JSON de la cuenta de servicio, en lugar de rellenar `FIREBASE_CLIENT_EMAIL` y `FIREBASE_PRIVATE_KEY`. En Vercel no hay ficheros: usa las dos variables `FIREBASE_CLIENT_EMAIL` y `FIREBASE_PRIVATE_KEY`.

Genera `CREDENTIALS_KEY`:

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
```

> `CREDENTIALS_KEY` debe ser estable: si la cambias, las credenciales ya guardadas no se podrán descifrar.

### Primer administrador

1. Crea el usuario en **Firebase Console → Authentication**.
2. Actívale el claim de admin:

   ```bash
   cd web
   npm run set-admin -- admin@ejemplo.com
   ```

3. Arranca el panel:

   ```bash
   npm run dev
   ```

4. Entra en `http://localhost:3000` con ese email y contraseña.
5. Desde **Usuarios** crea las cuentas de la app.

### Desplegar en Vercel

- Importa el repositorio.
- **Root Directory:** `web`.
- Añade las variables de `web/.env.local` en Vercel. En Vercel no existe
  `GOOGLE_APPLICATION_CREDENTIALS`: usa `FIREBASE_CLIENT_EMAIL` y
  `FIREBASE_PRIVATE_KEY`.
- Node.js: 22.x.
- Deploy.
- Actualiza en Android la URL de la API (`API_BASE_URL`) a tu dominio de Vercel.

> No actualices `firebase-admin` a la versión 14: usa `13.6.0`. La 14 trae
> `jose@6` (solo ESM) y el login falla en Vercel con
> `require() of ES Module ... jose ... not supported`.

> Vercel Hobby es para uso personal/no comercial. Si esto crece, necesitarás un plan de pago.

---

## 3. Configurar y compilar la app

### URL de la API

En `android/app/build.gradle.kts`:

- Emulador Android: `http://10.0.2.2:3000` (ya configurado en debug).
- Móvil/TV físico en la misma red: `http://IP_DE_TU_PC:3000`.
- Producción: tu URL de Vercel con HTTPS.

La app permite HTTP en claro porque muchos proveedores IPTV sirven listas y streams por HTTP. La API del panel sí debe usar HTTPS en producción. Si todas tus fuentes son HTTPS, puedes quitar `android:usesCleartextTraffic="true"` del manifiesto principal.

### Compilar

```bash
cd android
./gradlew assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

Instalar:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Para Android TV también puedes copiar el APK con la app **Downloader** o por ADB de red.

### Primer uso

1. Abre la app e inicia sesión con un usuario creado en el panel.
2. Pulsa **Añadir perfil**.
3. Elige M3U o Xtream Codes, rellena los datos y guarda.
4. Abre el perfil: entra al instante con los datos en caché y, si ha pasado más de un día, la lista/EPG se actualiza en segundo plano.
5. En **Ajustes** puedes forzar la actualización de la lista/EPG en cualquier momento.

> La creación del perfil pasa por la API del panel (`/api/profiles`), porque es la que cifra las credenciales. Si la API no está accesible, el alta fallará.

---

## 4. Firma y actualizaciones in-app

### Generar el keystore (una sola vez)

```bash
keytool -genkeypair -v -keystore release.keystore -alias iptvup \
  -keyalg RSA -keysize 2048 -validity 10000
```

Guárdalo en un gestor de contraseñas y en GitHub Secrets. **Si lo pierdes, no podrás instalar actualizaciones encima de la app**; habría que desinstalar.

Secrets del repositorio:

- `ANDROID_KEYSTORE_BASE64`: el keystore en base64.
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

En PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Set-Clipboard
```

### Publicar una versión

1. Sube `versionCode` y `versionName` en `android/app/build.gradle.kts`.
2. Crea un tag `v0.2.0` y súbelo. El workflow compila, firma y publica el APK en GitHub Releases.
3. Calcula el SHA-256:

   ```bash
   sha256sum IptvUp-v0.2.0.apk
   ```

4. Edita `updates/latest.json`:

   ```json
   {
     "versionCode": 2,
     "versionName": "0.2.0",
     "apkUrl": "https://github.com/TU_USUARIO/IptvUp/releases/download/v0.2.0/IptvUp-v0.2.0.apk",
     "sha256": "...",
     "minSupportedVersionCode": 1,
     "notes": "Cambios de esta versión"
   }
   ```

5. Haz commit del manifiesto.
6. En Android, pon en `UPDATE_MANIFEST_URL` la URL raw del manifiesto:
   `https://raw.githubusercontent.com/TU_USUARIO/IptvUp/main/updates/latest.json`.

La app comprueba al arrancar (máximo una vez al día) y desde **Ajustes → Buscar actualizaciones**. La primera instalación se comparte como APK; después todo se actualiza desde la app.

> Android siempre muestra el instalador del sistema. No existe instalación silenciosa sin permisos especiales de device-owner.

---

## 5. Emuladores de Firebase (opcional)

Para desarrollar sin gastar cuota ni usar datos reales:

```bash
cd firebase
firebase emulators:start --only auth,firestore
```

> Firebase CLI requiere **Java 21+** para los emuladores. Si tu `java` por defecto es 17, arranca con un JDK 21 en `PATH` o `JAVA_HOME`.

En `web/.env.local`:

```env
NEXT_PUBLIC_USE_FIREBASE_EMULATOR=true
NEXT_PUBLIC_FIREBASE_API_KEY=demo-key
NEXT_PUBLIC_FIREBASE_PROJECT_ID=iptvup-demo
FIREBASE_PROJECT_ID=iptvup-demo
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080
FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099
CREDENTIALS_KEY=...
```

Compila la app apuntando a los emuladores:

```bash
cd android
./gradlew assembleDebug -PuseFirebaseEmulator=true
```

El `google-services.json` de demo ya usa `iptvup-demo`. Crea usuarios de prueba en la UI del emulador (`http://localhost:4000`) y activa el primer admin con:

```bash
cd web
npm run set-admin -- admin@test.com
```

> En un móvil físico, `10.0.2.2` no funciona; usa la IP de tu PC. La app está preparada para leer el host desde `FIREBASE_EMULATOR_HOST`.

---

## 6. Seguridad

- Las credenciales M3U/Xtream se cifran en la API con AES-256-GCM.
- Firestore solo guarda metadatos y blobs cifrados.
- Las reglas impiden que un usuario vea perfiles de otro.
- Las cuentas solo se crean con el Admin SDK desde el panel; la app no puede
  crear documentos de usuario.
- El panel usa el Admin SDK solo en servidor; la service account nunca llega al navegador.
- La app cachea credenciales cifradas con Android Keystore.
- No subas `web/.env.local`, el keystore ni la service account al repositorio.
- El APK se verifica por SHA-256 y por firma antes de instalar una actualización.

---

## 7. Limitaciones actuales / siguientes pasos

- Los eventos deportivos usan la API pública de ESPN: LaLiga, Copa, Supercopa y Champions/Europa con equipos españoles. Solo se enlazan canales de grupos visibles, se excluyen los que empiezan por `##` y las variantes 8K, y se prioriza 4K, 1080/FHD, 720/HD y por último SD. Si el canal elegido no arranca, el reproductor prueba automáticamente otra variante visible del mismo canal.
- En listas **M3U** el proveedor obliga a descargar el archivo completo; se omiten los grupos ocultos al parsear y guardar, pero la descarga inicial sigue siendo el archivo entero. En **Xtream** sí se descargan solo las categorías visibles.
- EPG en parrilla temporal también en TV (ahora TV usa la lista de grupos → canales con ahora/después).
- Chromecast con receptor propio para streams TS; el receptor por defecto funciona mejor con HLS/DASH.
- Previsualización en vídeo (mini-player) dentro de la lista de canales de TV.
- Ya no se eliminan canales duplicados automáticamente: si tu proveedor repite el mismo canal con la misma URL, pueden aparecer entradas repetidas.
- Catch-up, grabación, PiP y Chromecast.
- Login por QR/código en TV.
- Tests instrumentados y pruebas con listas reales grandes.
- Publicación en Play Store (no recomendada para este caso por las políticas de IPTV).

---

## 8. Aviso legal

IptvUp es solo un reproductor. No incluye listas, canales ni contenido. Tú aportas tu propia fuente y eres responsable de que sea legal en tu país. No compartas listas ni contenido con derechos de autor.
