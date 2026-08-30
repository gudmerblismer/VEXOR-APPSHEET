#!/usr/bin/env python3
"""
Inyecta los datos del cliente (nombre de app y link de AppSheet) en el
proyecto ANTES de compilar. Se ejecuta una vez por cada build en GitHub Actions.

Uso:
    python3 scripts/apply_client_config.py "<APP_NAME>" "<APPSHEET_URL>"
"""
import sys
import re

STRINGS_XML = "app/src/main/res/values/strings.xml"
MAIN_ACTIVITY = "app/src/main/java/com/example/appsheetvexor/MainActivity.java"


def xml_escape(s: str) -> str:
    return (
        s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )


def set_app_name(app_name: str):
    with open(STRINGS_XML, "r", encoding="utf-8") as f:
        content = f.read()

    new_content = re.sub(
        r'(<string name="app_name">)(.*?)(</string>)',
        lambda m: m.group(1) + xml_escape(app_name) + m.group(3),
        content,
        count=1,
    )

    if new_content == content:
        raise RuntimeError("No se encontró <string name=\"app_name\"> en strings.xml")

    with open(STRINGS_XML, "w", encoding="utf-8") as f:
        f.write(new_content)
    print(f"strings.xml -> app_name = {app_name!r}")


def set_appsheet_url(appsheet_url: str):
    with open(MAIN_ACTIVITY, "r", encoding="utf-8") as f:
        content = f.read()

    # Reemplaza el valor por defecto de: private String APPSHEET_URL = "...";
    pattern = r'(private String APPSHEET_URL\s*=\s*")([^"]*)(")'
    new_content, n = re.subn(
        pattern,
        lambda m: m.group(1) + appsheet_url.replace('"', '\\"') + m.group(3),
        content,
        count=1,
    )

    if n == 0:
        raise RuntimeError("No se encontró la declaración de APPSHEET_URL en MainActivity.java")

    with open(MAIN_ACTIVITY, "w", encoding="utf-8") as f:
        f.write(new_content)
    print(f"MainActivity.java -> APPSHEET_URL = {appsheet_url!r}")


def main():
    if len(sys.argv) < 3:
        print('Uso: apply_client_config.py "<APP_NAME>" "<APPSHEET_URL>"')
        sys.exit(1)

    app_name, appsheet_url = sys.argv[1], sys.argv[2]
    set_app_name(app_name)
    set_appsheet_url(appsheet_url)


if __name__ == "__main__":
    main()
