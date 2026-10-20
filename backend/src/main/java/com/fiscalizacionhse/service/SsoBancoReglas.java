package com.fiscalizacionhse.service;

import java.util.List;

/** Tamaños de banco del simulacro y el tiempo que les corresponde. */
public final class SsoBancoReglas {

    public static final int TODAS = 0;
    public static final List<Integer> BANCOS = List.of(20, 40, 60, TODAS);
    public static final List<Integer> TIEMPOS = List.of(10, 15, 30, 50, 90);

    private SsoBancoReglas() {}

    public static int normalizarBanco(int n) {
        if (n == TODAS || n == 20 || n == 40 || n == 60) {
            return n;
        }
        if (n == 10) {
            return 20;
        }
        if (n == 30) {
            return 40;
        }
        return 20;
    }

    /** 20 → 15 min, 40 → 30 min, 60 → 50 min, todas → 90 min. */
    public static int tiempoMin(int banco) {
        int n = normalizarBanco(banco);
        if (n == 40) {
            return 30;
        }
        if (n == 60) {
            return 50;
        }
        if (n == TODAS) {
            return 90;
        }
        return 15;
    }

    public static int tope(int banco, int disponibles) {
        int n = normalizarBanco(banco);
        if (n == TODAS || n <= 0) {
            return Math.max(0, disponibles);
        }
        return Math.min(n, Math.max(0, disponibles));
    }

    /**
     * Tiempo según cuántas preguntas salen de verdad:
     * &lt;10 → 10 min, 10-20 → 15, 21-40 → 30, 41-60 → 50, más → 90.
     */
    public static int tiempoSegunCantidad(int cantidad) {
        if (cantidad < 10) {
            return 10;
        }
        if (cantidad <= 20) {
            return 15;
        }
        if (cantidad <= 40) {
            return 30;
        }
        if (cantidad <= 60) {
            return 50;
        }
        return 90;
    }
}
