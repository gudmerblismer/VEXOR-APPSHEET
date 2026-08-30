/**
 * =====================================================================
 *  INTEGRACIÓN VEXOR <-> GITHUB ACTIONS
 * =====================================================================
 * Agrega estas funciones a tu proyecto de Apps Script (Code.gs) YA
 * EXISTENTE. No reemplaza tu formulario, solo lo conecta con GitHub
 * para que cada fila nueva dispare la generación del APK.
 *
 * CONFIGURACIÓN REQUERIDA (una sola vez):
 *   Extensiones > Apps Script > ⚙️ Configuración del proyecto >
 *   "Propiedades del script" > agregar:
 *     GITHUB_TOKEN   -> tu Personal Access Token (scope: "workflow" + "repo" si el repo es privado)
 *     GITHUB_OWNER   -> tu usuario u organización de GitHub
 *     GITHUB_REPO    -> nombre del repositorio (ej: "vexor-android")
 * =====================================================================
 */

const SHEET_NAME_BUILDS = "builds"; // pestaña que ya tienes con: fecha | app_name | appsheet_url | estado | email_cliente | icon_link | apk_link

/**
 * Dispara el workflow de GitHub Actions para compilar el APK de un cliente.
 * Llama a esta función justo después de escribir la fila en el Sheet
 * (donde ya generas el registro que se ve en tu captura).
 *
 * @param {number} rowIndex   Número de fila en la hoja "builds" (1-indexed, incluyendo encabezado)
 * @param {string} appName    Nombre de la app
 * @param {string} appsheetUrl Link de AppSheet
 * @param {string} iconDriveLink Link de Drive del icono (tal como lo guardas en icon_link)
 * @param {string} emailCliente Email del cliente
 */
function dispatchGithubBuild(rowIndex, appName, appsheetUrl, iconDriveLink, emailCliente) {
  const props = PropertiesService.getScriptProperties();
  const token = props.getProperty("GITHUB_TOKEN");
  const owner = props.getProperty("GITHUB_OWNER");
  const repo = props.getProperty("GITHUB_REPO");

  if (!token || !owner || !repo) {
    throw new Error("Faltan GITHUB_TOKEN / GITHUB_OWNER / GITHUB_REPO en las Propiedades del script.");
  }

  const iconDirectUrl = buildDriveDirectDownloadUrl_(iconDriveLink);

  const url = `https://api.github.com/repos/${owner}/${repo}/actions/workflows/build-apk.yml/dispatches`;
  const payload = {
    ref: "main", // cambia esto si tu rama por defecto se llama distinto
    inputs: {
      app_name: appName,
      appsheet_url: appsheetUrl,
      icon_url: iconDirectUrl,
      email_cliente: emailCliente || "",
      row: String(rowIndex),
    },
  };

  const response = UrlFetchApp.fetch(url, {
    method: "post",
    contentType: "application/json",
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "application/vnd.github+json",
    },
    payload: JSON.stringify(payload),
    muteHttpExceptions: true,
  });

  const code = response.getResponseCode();
  if (code !== 204) {
    Logger.log("Error disparando GitHub Actions: " + code + " " + response.getContentText());
    marcarEstadoFila_(rowIndex, "ERROR_GITHUB");
  } else {
    marcarEstadoFila_(rowIndex, "COMPILANDO");
  }
}

/**
 * Convierte un link de Google Drive (de "ver") en un link de descarga directa.
 * IMPORTANTE: el archivo debe estar compartido como "Cualquier usuario con el enlace".
 */
function buildDriveDirectDownloadUrl_(driveLink) {
  const match = driveLink.match(/[-\w]{25,}/); // extrae el FILE_ID
  if (!match) return driveLink; // ya venía como URL directa
  const fileId = match[0];
  return `https://drive.google.com/uc?export=download&id=${fileId}`;
}

/**
 * Punto de entrada que llama GitHub Actions al terminar de compilar.
 * Debe coincidir con la URL publicada de este Web App (Implementar > Nueva implementación),
 * guardada como secret VEXOR_CALLBACK_URL en GitHub.
 *
 * Si ya tienes un doPost(e) en tu proyecto, FUSIONA esta lógica dentro del tuyo
 * (revisando primero si viene el campo "action") en vez de tener dos doPost.
 */
function doPost(e) {
  const data = JSON.parse(e.postData.contents);

  if (data.action === "apk_listo") {
    onApkListo_(data);
    return ContentService.createTextOutput(JSON.stringify({ ok: true }))
      .setMimeType(ContentService.MimeType.JSON);
  }

  // ... aquí sigue la lógica normal de tu formulario (crear fila nueva), si la tenías en doPost.
  return ContentService.createTextOutput(JSON.stringify({ ok: true }))
    .setMimeType(ContentService.MimeType.JSON);
}

/**
 * Actualiza la fila con el link del APK, cambia el estado y avisa al cliente por correo.
 */
function onApkListo_(data) {
  const sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEET_NAME_BUILDS);
  const headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
  const colEstado = headers.indexOf("estado") + 1;
  const colApkLink = headers.indexOf("apk_link") + 1;

  const rowIndex = parseInt(data.row, 10);
  if (rowIndex && colEstado > 0 && colApkLink > 0) {
    sheet.getRange(rowIndex, colEstado).setValue("LISTO");
    // apk_link guarda el link de descarga directa (para automatizaciones);
    // apk_view_link es la página de Drive con botón de descarga (mejor para el cliente final).
    sheet.getRange(rowIndex, colApkLink).setValue(data.apk_link);
  }

  if (data.email_cliente) {
    const linkParaCliente = data.apk_view_link || data.apk_link;
    MailApp.sendEmail({
      to: data.email_cliente,
      subject: `Tu APK de "${data.app_name}" ya está lista`,
      htmlBody: `
        <p>Hola,</p>
        <p>Tu aplicación <b>${data.app_name}</b> ya fue generada.</p>
        <p><a href="${linkParaCliente}">Descargar APK</a></p>
        <p>Cualquier persona puede abrir este link y descargar el APK directamente, sin necesitar cuenta de Google ni de GitHub.</p>
        <p>— Vexor</p>
      `,
    });
  }
}

function marcarEstadoFila_(rowIndex, estado) {
  const sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEET_NAME_BUILDS);
  const headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
  const colEstado = headers.indexOf("estado") + 1;
  if (colEstado > 0) sheet.getRange(rowIndex, colEstado).setValue(estado);
}
