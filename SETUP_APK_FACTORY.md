# Vexor · Fábrica de APKs por cliente (GitHub Actions)

Este flujo hace lo siguiente automáticamente por cada cliente nuevo:

1. El cliente llena el formulario Vexor (nombre de app, link de AppSheet, icono, email).
2. Apps Script guarda la fila en el Sheet y **dispara un workflow de GitHub Actions** vía API (token).
3. GitHub Actions:
   - Inyecta el nombre de la app y el link de AppSheet en el código.
   - Descarga el icono y genera todos los tamaños/formatos que pide Android.
   - Compila y **firma** el APK.
   - Sube el APK a una carpeta de Google Drive con acceso **"cualquiera con el link"**, para que cualquier cliente lo descargue sin necesitar cuenta de GitHub ni de Google.
   - (Además deja una copia en un GitHub Release, como respaldo interno.)
   - Le avisa a Apps Script que ya está listo.
4. Apps Script actualiza la columna `apk_link` / `estado` del Sheet y **le manda el link por correo al cliente**.

Archivos añadidos/modificados en este proyecto:

```
app/build.gradle.kts                         -> firma configurable + applicationId por cliente
app/src/main/AndroidManifest.xml             -> el label ahora usa @string/app_name
scripts/apply_client_config.py               -> inyecta nombre de app y URL de AppSheet
scripts/generate_icons.py                    -> genera todos los iconos desde 1 imagen
scripts/upload_to_drive.py                   -> sube el APK final a Drive con link público
.github/workflows/build-apk.yml              -> el pipeline completo de compilación
apps-script/Code_GitHub_Integration.gs       -> código para pegar en tu Apps Script
```

---

## 1. Sube este proyecto a GitHub

```bash
cd vexor
git init
git add .
git commit -m "Vexor APK factory"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/TU_REPO.git
git push -u origin main
```

> Si tu repo ya existe, simplemente copia estas carpetas/archivos nuevos dentro y haz commit + push.

## 2. Crea el keystore de firma (una sola vez, para todos los clientes)

```bash
keytool -genkeypair -v -keystore release.keystore \
  -alias vexor -keyalg RSA -keysize 2048 -validity 10000
```

Te pedirá una contraseña del keystore y una de la clave (puedes usar la misma). **Guarda ese `release.keystore` en un lugar seguro**, es el mismo con el que firmarás todas las apps de tus clientes desde ahora.

Conviértelo a base64 para poder guardarlo como secreto de GitHub:

```bash
base64 -i release.keystore -o release.keystore.b64   # macOS/Linux
# en Windows: certutil -encode release.keystore release.keystore.b64
```

## 3. Configura los "Secrets" del repositorio en GitHub

`Settings > Secrets and variables > Actions > New repository secret`

| Secret | Valor |
|---|---|
| `VEXOR_KEYSTORE_BASE64` | contenido del archivo `release.keystore.b64` |
| `VEXOR_KEYSTORE_PASSWORD` | la contraseña del keystore |
| `VEXOR_KEY_ALIAS` | `vexor` (o el alias que usaste) |
| `VEXOR_KEY_PASSWORD` | la contraseña de la clave |
| `VEXOR_CALLBACK_URL` | la URL de tu Web App de Apps Script (`.../exec`) |
| `VEXOR_GDRIVE_SA_KEY` | ver paso 4 |
| `VEXOR_GDRIVE_FOLDER_ID` | ver paso 4 (opcional) |

## 4. Crea la cuenta de servicio de Google (para que el APK quede público en Drive)

1. Ve a [console.cloud.google.com](https://console.cloud.google.com) > crea o selecciona un proyecto.
2. `APIs y servicios > Biblioteca` > busca **Google Drive API** > **Habilitar**.
3. `APIs y servicios > Credenciales > Crear credenciales > Cuenta de servicio`. Dale cualquier nombre (ej: `vexor-apk-uploader`).
4. Entra a la cuenta de servicio creada > pestaña **Claves** > `Agregar clave > Crear clave nueva > JSON`. Se descarga un archivo `.json`.
5. (Opcional pero recomendado) Crea una carpeta en tu Google Drive llamada, por ejemplo, "APKs Vexor" y **compártela** con el email de la cuenta de servicio (algo como `vexor-apk-uploader@tu-proyecto.iam.gserviceaccount.com`) con permiso de **Editor**. Copia el ID de esa carpeta (está en la URL: `drive.google.com/drive/folders/ESTE_ID`).

Guarda como secrets del repo:

| Secret | Valor |
|---|---|
| `VEXOR_GDRIVE_SA_KEY` | el contenido completo del archivo `.json` de la cuenta de servicio (pégalo tal cual) |
| `VEXOR_GDRIVE_FOLDER_ID` | el ID de la carpeta del paso 5 (opcional; si lo omites, sube a la raíz del Drive de la cuenta de servicio) |

## 5. Crea el Personal Access Token (PAT) para que Apps Script dispare el build

GitHub > tu foto de perfil > `Settings > Developer settings > Personal access tokens > Fine-grained tokens`

- Repository access: solo el repo de Vexor.
- Permissions: **Actions: Read and write**.
- Copia el token (empieza con `github_pat_...`).

## 6. Guarda el token en Apps Script

En tu proyecto de Apps Script: `Extensiones > Apps Script > ⚙️ Configuración del proyecto > Propiedades del script`, agrega:

| Propiedad | Valor |
|---|---|
| `GITHUB_TOKEN` | el token del paso 4 |
| `GITHUB_OWNER` | tu usuario/organización de GitHub |
| `GITHUB_REPO` | el nombre del repo |

## 7. Pega el código de integración en tu Apps Script

Copia el contenido de `apps-script/Code_GitHub_Integration.gs` en tu proyecto existente.

- Si ya tienes un `doPost(e)`, **fusiona** la lógica (el `if (data.action === "apk_listo")`) con el tuyo en vez de tener dos funciones `doPost`.
- Justo después de donde hoy escribes la fila nueva en el Sheet (la que se ve en tu captura, columnas `fecha | app_name | appsheet_url | estado | email_cliente | icon_link`), agrega la llamada:

```javascript
dispatchGithubBuild(numeroDeFila, appName, appsheetUrl, iconLink, emailCliente);
```

- **Vuelve a implementar** el Web App (`Implementar > Administrar implementaciones > Editar > Nueva versión`) para que los cambios tomen efecto, y confirma que la URL sigue siendo la misma que pusiste en `VEXOR_CALLBACK_URL`.

## 8. Icono: un detalle importante

El icono que sube el cliente debe quedar compartido como **"Cualquier usuario con el enlace"** en Drive (si tu Apps Script no lo hace ya al crear el archivo, agrégalo con `file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW)`), porque GitHub Actions necesita poder descargarlo sin iniciar sesión.

## 9. Prueba todo

1. Llena el formulario de Vexor con datos de prueba.
2. En GitHub, pestaña **Actions**, deberías ver el workflow "Build APK por cliente (Vexor)" ejecutándose.
3. Al terminar (~3-5 min), revisa:
   - La carpeta de Drive que compartiste: debe aparecer el APK subido.
   - El Sheet: la columna `estado` debe pasar a `LISTO` y `apk_link` debe tener el link de descarga directa.
   - El correo del cliente: debe llegar el aviso con el link de Drive (funciona en cualquier navegador, sin cuenta de Google ni de GitHub).
   - Adicionalmente queda una copia de respaldo en **Releases** del repo (esa sí requiere acceso al repo si es privado).

---

### Notas

- Cada cliente obtiene su propio `applicationId` (sufijo automático basado en el nombre de la app), así puedes instalar varias apps de distintos clientes en el mismo celular sin que se pisen.
- El link `https://drive.google.com/uc?export=download&id=...` descarga el archivo directamente. Si Drive muestra una advertencia de "no se puede escanear en busca de virus" (puede pasar con archivos grandes), el cliente igual puede darle clic a "Descargar de todas formas".
- Si en algún momento quieres cambiar el destino de entrega (por ejemplo, subirlo a tu propio servidor o a un bucket), solo hay que tocar el paso "Subir APK a Google Drive" del workflow — el resto del pipeline no cambia.
