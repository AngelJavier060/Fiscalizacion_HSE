"""Genera los fondos ambientales en BUCLE PERFECTO para la lectura.

- ambiente.wav : pad calmado (acorde grave con leve movimiento).
- lluvia.wav   : lluvia suave (ruido filtrado, con crossfade en el empalme).

Ambos hacen loop sin clic. Salida: assets/audio/*.wav (mono, 16-bit).
"""
import math
import os
import random
import struct
import wave

SR = 44100

AUDIO_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "assets", "audio",
)


def escribir_wav(nombre, muestras, ganancia):
    pico = max(1e-6, max(abs(x) for x in muestras))
    escala = (ganancia / pico) * 32767.0
    ruta = os.path.join(AUDIO_DIR, nombre)
    os.makedirs(AUDIO_DIR, exist_ok=True)
    with wave.open(ruta, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        datos = bytearray()
        for x in muestras:
            v = int(max(-32768, min(32767, round(x * escala))))
            datos += struct.pack("<h", v)
        w.writeframes(bytes(datos))
    print("OK ->", ruta, os.path.getsize(ruta), "bytes")


def gen_pad():
    dur = 12.0
    n = int(SR * dur)
    # f*dur entero -> loop perfecto
    parciales = [
        (55.0, 0.22, 0.2500),
        (110.0, 0.30, 0.1666),
        (165.0, 0.20, 0.3333),
        (220.0, 0.16, 0.4166),
        (330.0, 0.07, 0.0833),
    ]
    out = [0.0] * n
    for i in range(n):
        t = i / SR
        s = 0.0
        for f, g, lfo in parciales:
            amp = g * (0.85 + 0.15 * math.sin(2 * math.pi * lfo * t))
            s += amp * math.sin(2 * math.pi * f * t)
        out[i] = s
    escribir_wav("ambiente.wav", out, 0.55)


def gen_lluvia():
    dur = 10.0
    n = int(SR * dur)
    x = int(SR * 0.5)  # crossfade de 0.5 s para empalmar el loop
    random.seed(7)

    # Ruido blanco -> filtro pasa-bajos suave (dos polos) = "lluvia" suave.
    bruto = [random.uniform(-1.0, 1.0) for _ in range(n + x)]
    y1 = 0.0
    y2 = 0.0
    a = 0.25
    filtrado = [0.0] * (n + x)
    for i in range(n + x):
        y1 += a * (bruto[i] - y1)
        y2 += a * (y1 - y2)
        filtrado[i] = y2

    # Crossfade del inicio con la cola para que el loop sea continuo.
    out = [0.0] * n
    for i in range(n):
        if i < x:
            w = i / x
            out[i] = filtrado[i] * w + filtrado[i + n] * (1.0 - w)
        else:
            out[i] = filtrado[i]

    # Leve ondulación de intensidad (loop-safe: 0.2 Hz * 10 s = 2 ciclos).
    for i in range(n):
        t = i / SR
        out[i] *= 0.8 + 0.2 * math.sin(2 * math.pi * 0.2 * t)

    escribir_wav("lluvia.wav", out, 0.5)


if __name__ == "__main__":
    gen_pad()
    gen_lluvia()
