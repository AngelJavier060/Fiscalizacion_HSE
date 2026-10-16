# -*- coding: utf-8 -*-
import json
import sys
from pathlib import Path
import urllib.request
import urllib.error

import os

BASE_AUTH = os.environ.get("SSO_AUTH", "http://localhost:8080/api/v1/auth")
BASE_API = os.environ.get("SSO_API", "http://localhost:8080/api/v1/api/simulador/admin")
JSON_PATH = Path(r"D:\PROGRAMAS 2025 CONTRUCCION\Fiscalizacion HSE\scripts\banco_sso.json")
EMAIL = os.environ.get("SSO_EMAIL", "admin@fiscalizacionhse.com")
PASSWORD = os.environ.get("SSO_PASSWORD", "AdminHSE2024!")


def req(method, url, token=None, body=None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        err = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} -> {e.code}: {err}") from e


def main():
    json_path = Path(sys.argv[1]) if len(sys.argv) > 1 else JSON_PATH
    login = req("POST", f"{BASE_AUTH}/login", body={"email": EMAIL, "password": PASSWORD})
    token = login["token"]
    qs = json.loads(json_path.read_text(encoding="utf-8"))

    temas = req("GET", f"{BASE_API}/temas", token) or []
    by_name = {t["nombre"].strip().lower(): t for t in temas}

    created_temas = 0
    for nombre in sorted({q["tema"] for q in qs}):
        if nombre.strip().lower() not in by_name:
            t = req("POST", f"{BASE_API}/temas", token, {"nombre": nombre, "activo": True})
            by_name[nombre.strip().lower()] = t
            created_temas += 1

    existentes = req("GET", f"{BASE_API}/preguntas", token) or []
    vistos = {p["enunciado"].strip().lower() for p in existentes}

    ok = skip = fail = 0
    errores = []
    for q in qs:
        enun = q["enunciado"].strip()
        if enun.lower() in vistos:
            skip += 1
            continue
        tema = by_name[q["tema"].strip().lower()]
        expl = q.get("explicacion") or ""
        if q.get("nota"):
            expl = (expl + " " + q["nota"]).strip()
        body = {
            "temaId": tema["id"],
            "enunciado": enun,
            "explicacion": expl or None,
            "fuente": f"Banco SSO ítem {q['codigo']}",
            "activo": True,
            "opciones": [
                {
                    "texto": o["texto"],
                    "esCorrecta": o["esCorrecta"],
                    "argumento": None if o["esCorrecta"] else o.get("argumento"),
                }
                for o in q["opciones"]
            ],
        }
        try:
            req("POST", f"{BASE_API}/preguntas", token, body)
            vistos.add(enun.lower())
            ok += 1
        except Exception as e:
            fail += 1
            errores.append(f"{q['codigo']}: {e}")

    print(f"temas_nuevos={created_temas} cargadas={ok} omitidas={skip} fallidas={fail}")
    for e in errores[:15]:
        print(e)


if __name__ == "__main__":
    main()
