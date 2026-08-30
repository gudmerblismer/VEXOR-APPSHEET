#!/usr/bin/env python3
"""
Sube el APK ya compilado y firmado a Google Drive usando una cuenta de
servicio, lo deja como "cualquiera con el link puede ver/descargar" y
devuelve el link de descarga directa.

Uso:
    python3 scripts/upload_to_drive.py <ruta_apk> <nombre_archivo_en_drive> [carpeta_id]

Requiere la variable de entorno:
    GOOGLE_SERVICE_ACCOUNT_JSON  -> contenido completo del JSON de la cuenta de servicio
"""
import sys
import os
import json
import io

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

SCOPES = ["https://www.googleapis.com/auth/drive"]


def get_drive_service():
    raw = os.environ.get("GOOGLE_SERVICE_ACCOUNT_JSON")
    if not raw:
        raise RuntimeError("Falta la variable de entorno GOOGLE_SERVICE_ACCOUNT_JSON")
    info = json.loads(raw)
    creds = service_account.Credentials.from_service_account_info(info, scopes=SCOPES)
    return build("drive", "v3", credentials=creds)


def upload_apk(apk_path: str, drive_filename: str, folder_id: str | None):
    service = get_drive_service()

    metadata = {"name": drive_filename}
    if folder_id:
        metadata["parents"] = [folder_id]

    media = MediaFileUpload(apk_path, mimetype="application/vnd.android.package-archive", resumable=True)
    file = service.files().create(body=metadata, media_body=media, fields="id").execute()
    file_id = file["id"]

    # Lo dejamos accesible para cualquiera con el link (sin necesidad de cuenta de Google)
    service.permissions().create(
        fileId=file_id,
        body={"role": "reader", "type": "anyone"},
    ).execute()

    direct_link = f"https://drive.google.com/uc?export=download&id={file_id}"
    view_link = f"https://drive.google.com/file/d/{file_id}/view"
    return file_id, direct_link, view_link


def main():
    if len(sys.argv) < 3:
        print("Uso: upload_to_drive.py <ruta_apk> <nombre_archivo_en_drive> [carpeta_id]")
        sys.exit(1)

    apk_path = sys.argv[1]
    drive_filename = sys.argv[2]
    folder_id = sys.argv[3] if len(sys.argv) > 3 and sys.argv[3] else None

    file_id, direct_link, view_link = upload_apk(apk_path, drive_filename, folder_id)

    # GitHub Actions: exponer como outputs del step
    gh_output = os.environ.get("GITHUB_OUTPUT")
    if gh_output:
        with open(gh_output, "a", encoding="utf-8") as f:
            f.write(f"file_id={file_id}\n")
            f.write(f"direct_link={direct_link}\n")
            f.write(f"view_link={view_link}\n")

    print(f"Subido a Drive: {view_link}")
    print(f"Link de descarga directa: {direct_link}")


if __name__ == "__main__":
    main()
