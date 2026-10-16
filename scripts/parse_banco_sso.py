# -*- coding: utf-8 -*-
import json
import re
import sys
from pathlib import Path

SRC = Path(r"D:\PROGRAMAS 2025 CONTRUCCION\Fiscalizacion HSE\banco_sso_extract.txt")
OUT = Path(r"D:\PROGRAMAS 2025 CONTRUCCION\Fiscalizacion HSE\scripts\banco_sso.json")

QID = re.compile(r"^\s*(\d+[a-z]?)\s*$")
OPT = re.compile(r"^\s*([A-E])\s*$")
CORRECT = re.compile(r"^[✓✔]\s*Correcta\.?\s*(.*)$", re.I)
INCORRECT = re.compile(r"^[✗✘]\s*Incorrecta\.?\s*(.*)$", re.I)
NOTA = re.compile(r"^Nota\s*:", re.I)
PAGE_NOISE = re.compile(
    r"^(Banco de preguntas SSO|Página \d+|===== PAGE =====|Cómo leer|Preguntas por tema|"
    r"Preguntas con nota|Seguridad y salud|Contiene \d+|Están todos|Las listas|"
    r"La posición|Tapa las|✓ Correcta ·|✗ Incorrecta ·|Nota ·|"
    r"No son preguntas|Se enfocan|Completa los|Cubre los temas|"
    r"oficiales de ENAP|práctica con datos)",
    re.I,
)

TEMA_MAP = {
    "ERGONOMÍA": "Ergonomía",
    "ERGONOMIA": "Ergonomía",
    "INCENDIOS Y EXTINTORES": "Incendios y extintores",
    "DETECCIÓN Y ALARMAS": "Detección y alarmas",
    "DETECCION Y ALARMAS": "Detección y alarmas",
    "IZAJE (ASME B30)": "Izaje (ASME B30)",
    "ÁREAS CLASIFICADAS": "Áreas clasificadas",
    "AREAS CLASIFICADAS": "Áreas clasificadas",
    "RIESGO ELÉCTRICO": "Riesgo eléctrico",
    "RIESGO ELECTRICO": "Riesgo eléctrico",
    "HIGIENE INDUSTRIAL – RUIDO": "Higiene industrial (ruido, químicos)",
    "HIGIENE INDUSTRIAL - RUIDO": "Higiene industrial (ruido, químicos)",
    "HIGIENE INDUSTRIAL – QUÍMICOS": "Higiene industrial (ruido, químicos)",
    "HIGIENE INDUSTRIAL - QUÍMICOS": "Higiene industrial (ruido, químicos)",
    "HIGIENE INDUSTRIAL – QUIMICOS": "Higiene industrial (ruido, químicos)",
    "MATERIALES PELIGROSOS": "Materiales peligrosos",
    "LEGISLACIÓN ECUATORIANA": "Legislación ecuatoriana",
    "LEGISLACION ECUATORIANA": "Legislación ecuatoriana",
    "EPP": "EPP",
    "TRABAJOS EN ALTURA": "Trabajos en altura (ANSI Z359)",
    "TRABAJOS EN ALTURA (ANSI Z359)": "Trabajos en altura (ANSI Z359)",
    "SEÑALIZACIÓN": "Señalización",
    "SENALIZACION": "Señalización",
    "PSICOSOCIAL": "Psicosocial",
    "INDICADORES DE ACCIDENTABILIDAD": "Indicadores de accidentabilidad",
    "SALUD OCUPACIONAL": "Salud ocupacional",
    "GESTIÓN DE RIESGOS": "Gestión de riesgos",
    "GESTION DE RIESGOS": "Gestión de riesgos",
    "EXCAVACIONES": "Excavaciones",
    "EMERGENCIAS": "Emergencias",
    "MAQUINARIA PESADA": "Maquinaria pesada",
    "TRABAJOS EN CALIENTE": "Trabajos en caliente",
    "PERMISOS DE TRABAJO": "Permisos de trabajo",
    "ESPACIOS CONFINADOS": "Espacios confinados",
    "H2S Y GASES": "H2S y gases",
    "BLOQUEO Y ETIQUETADO (LOTO)": "Bloqueo y etiquetado (LOTO)",
    "BLOQUEO Y ETIQUETADO": "Bloqueo y etiquetado (LOTO)",
    "ISO 45001": "ISO 45001",
    "PRIMEROS AUXILIOS": "Primeros auxilios",
    "INVESTIGACIÓN DE ACCIDENTES": "Investigación de accidentes",
    "INVESTIGACION DE ACCIDENTES": "Investigación de accidentes",
    "INCENDIOS": "Incendios y extintores",
    "HIGIENE INDUSTRIAL": "Higiene industrial (ruido, químicos)",
    "SEGURIDAD DE PROCESOS": "Seguridad de procesos",
    "TOXICOLOGÍA": "Toxicología",
    "TOXICOLOGIA": "Toxicología",
    "CONCEPTOS BÁSICOS": "Conceptos básicos",
    "CONCEPTOS BASICOS": "Conceptos básicos",
    "CULTURA DE SEGURIDAD": "Cultura de seguridad",
    "ANDAMIOS Y ESCALERAS": "Andamios y escaleras",
    "MONTACARGAS": "Montacargas",
    "RADIACIONES": "Radiaciones",
    "RIESGO BIOLÓGICO": "Riesgo biológico",
    "RIESGO BIOLOGICO": "Riesgo biológico",
    "GESTIÓN DE CONTRATISTAS": "Gestión de contratistas",
    "GESTION DE CONTRATISTAS": "Gestión de contratistas",
    "HERRAMIENTAS": "Herramientas",
    "CILINDROS DE GAS": "Cilindros de gas",
    "SEGURIDAD VIAL": "Seguridad vial",
    "PLATAFORMAS ELEVADORAS": "Plataformas elevadoras",
    "CORTE Y PODA DE ÁRBOLES": "Corte y poda de árboles",
    "CORTE Y PODA DE ARBOLES": "Corte y poda de árboles",
    "FACILIDADES DE PRODUCCIÓN": "Facilidades de producción",
    "FACILIDADES DE PRODUCCION": "Facilidades de producción",
    "INSPECCIONES Y HALLAZGOS": "Inspecciones y hallazgos",
    "POZOS Y WORKOVER": "Pozos y workover",
    "DERECHO DE VÍA Y DUCTOS": "Derecho de vía y ductos",
    "DERECHO DE VIA Y DUCTOS": "Derecho de vía y ductos",
    "VACUUM Y TANQUEROS": "Vacuum y tanqueros",
    "NORM": "NORM",
    "FATIGA": "Fatiga",
    "RESIDUOS": "Residuos",
    "ALCOHOL Y DROGAS": "Alcohol y drogas",
    "TRANSPORTE FLUVIAL": "Transporte fluvial",
    "ORDEN Y LIMPIEZA": "Orden y limpieza",
}


def is_theme(line: str) -> bool:
    s = line.strip()
    if len(s) < 3 or len(s) > 60:
        return False
    letters = [c for c in s if c.isalpha()]
    if not letters:
        return False
    upper = sum(1 for c in letters if c.upper() == c)
    return upper / len(letters) > 0.85


def norm_tema(raw: str) -> str:
    key = re.sub(r"\s+", " ", raw.strip().upper())
    key = key.replace("–", "-").replace("—", "-")
    if key in TEMA_MAP:
        return TEMA_MAP[key]
    key2 = key.replace("-", "–")
    if key2 in TEMA_MAP:
        return TEMA_MAP[key2]
    small = {"de", "y", "en", "la", "el", "del", "los", "las", "a"}
    keep = {"h2s", "iso", "loto", "epp", "norm", "medevac", "ats", "osha", "nfpa", "ansi"}
    words = re.sub(r"\s+", " ", raw.strip()).split(" ")
    out = []
    for i, w in enumerate(words):
        low = w.lower()
        if low in keep or re.fullmatch(r"h2s|iso|loto|a92", low):
            out.append(w.upper() if low != "a92" else "A92")
        elif i > 0 and low in small:
            out.append(low)
        else:
            out.append(w[:1].upper() + w[1:].lower() if w else w)
    return " ".join(out)


def flush_option(q, letter, text_parts, kind, arg_parts):
    texto = " ".join(p.strip() for p in text_parts if p.strip())
    texto = re.sub(r"\s+", " ", texto).strip()
    arg = " ".join(p.strip() for p in arg_parts if p.strip())
    arg = re.sub(r"\s+", " ", arg).strip()
    if not texto:
        return
    q["opciones"].append({
        "letra": letter,
        "texto": texto,
        "esCorrecta": kind == "ok",
        "argumento": None if kind == "ok" else arg,
        "explicacionCorrecta": arg if kind == "ok" else None,
    })


def peek_next(lines, i):
    while i < len(lines):
        s = lines[i].strip()
        if s and not PAGE_NOISE.match(s) and not s.startswith("===== PAGE"):
            return s
        i += 1
    return ""


def parse(src: Path | None = None, codigo_prefijo: str = ""):
    lines = (src or SRC).read_text(encoding="utf-8").splitlines()
    questions = []
    i = 0
    # skip until first real question id 1a
    started = False
    q = None
    state = "idle"  # idle, tema, enun, opt_text, opt_arg
    letter = None
    text_parts = []
    arg_parts = []
    kind = None

    def close_option():
        nonlocal letter, text_parts, arg_parts, kind
        if q is not None and letter:
            flush_option(q, letter, text_parts, kind, arg_parts)
        letter, text_parts, arg_parts, kind = None, [], [], None

    def close_question():
        nonlocal q
        close_option()
        if q and q.get("enunciado") and q.get("opciones"):
            ok = next((o for o in q["opciones"] if o["esCorrecta"]), None)
            q["explicacion"] = (ok or {}).get("explicacionCorrecta")
            questions.append(q)
        q = None

    while i < len(lines):
        raw = lines[i]
        line = raw.strip()
        i += 1
        if not line:
            continue
        if PAGE_NOISE.match(line) or line.startswith("===== PAGE"):
            continue

        m_id = QID.match(line)
        if m_id:
            if not started:
                nxt = peek_next(lines, i)
                if not is_theme(nxt) and m_id.group(1) not in ("1a",):
                    continue
                started = True
            close_question()
            codigo = m_id.group(1)
            if codigo_prefijo:
                codigo = f"{codigo_prefijo}-{codigo}"
            q = {"codigo": codigo, "tema": "", "enunciado": "", "opciones": [], "nota": None}
            state = "tema"
            continue

        if not started or q is None:
            continue

        if NOTA.match(line):
            close_option()
            nota = line
            extras = []
            while i < len(lines):
                nxt = lines[i].strip()
                if not nxt:
                    i += 1
                    continue
                if QID.match(nxt) or OPT.match(nxt) or PAGE_NOISE.match(nxt):
                    break
                extras.append(nxt)
                i += 1
            q["nota"] = re.sub(r"\s+", " ", " ".join([nota] + extras))
            state = "idle"
            continue

        m_opt = OPT.match(line)
        if m_opt:
            close_option()
            letter = m_opt.group(1)
            text_parts, arg_parts, kind = [], [], None
            state = "opt_text"
            continue

        m_ok = CORRECT.match(line)
        m_bad = INCORRECT.match(line)
        if m_ok or m_bad:
            kind = "ok" if m_ok else "bad"
            rest = (m_ok or m_bad).group(1).strip()
            arg_parts = [rest] if rest else []
            state = "opt_arg"
            continue

        if state == "tema":
            if is_theme(line):
                q["tema"] = norm_tema(line)
                state = "enun"
            else:
                q["enunciado"] = line
                state = "enun"
            continue

        if state == "enun":
            if is_theme(line) and not q["tema"]:
                q["tema"] = norm_tema(line)
                continue
            q["enunciado"] = (q["enunciado"] + " " + line).strip()
            continue

        if state == "opt_text":
            text_parts.append(line)
            continue

        if state == "opt_arg":
            arg_parts.append(line)
            continue

    close_question()
    return questions


def main():
    args = sys.argv[1:]
    if args:
        files = [Path(a) for a in args[:-1]]
        out = Path(args[-1])
        qs = []
        for f in files:
            m = re.search(r"Parte(\d+)", f.name, re.I)
            pref = f"p{m.group(1)}" if m else f.stem
            qs.extend(parse(f, codigo_prefijo=pref))
        OUT_USE = out
    else:
        qs = parse()
        OUT_USE = OUT
    problems = []
    for q in qs:
        n = len(q["opciones"])
        n_ok = sum(1 for o in q["opciones"] if o["esCorrecta"])
        n_bad_sin = sum(1 for o in q["opciones"] if not o["esCorrecta"] and not o["argumento"])
        if n < 2 or n_ok != 1 or n_bad_sin:
            problems.append({
                "codigo": q["codigo"],
                "n": n,
                "ok": n_ok,
                "bad_sin_arg": n_bad_sin,
                "enunciado": q["enunciado"][:80],
            })
    OUT_USE.write_text(json.dumps(qs, ensure_ascii=False, indent=2), encoding="utf-8")
    print("archivo:", OUT_USE)
    temas = sorted({q["tema"] for q in qs})
    print(f"preguntas={len(qs)} temas={len(temas)}")
    print("temas:", ", ".join(temas))
    print("problemas:", len(problems))
    for p in problems[:20]:
        print(p)


if __name__ == "__main__":
    main()
