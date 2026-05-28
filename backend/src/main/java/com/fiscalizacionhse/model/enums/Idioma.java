package com.fiscalizacionhse.model.enums;

public enum Idioma {
    es("Español"),
    en("Inglés"),
    pt("Portugués"),
    fr("Francés"),
    de("Alemán"),
    it("Italiano"),
    otros("Otros");

    private final String nombre;

    Idioma(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }

    public static boolean requiereTraduccion(String codigo) {
        return codigo != null && !codigo.equals("es");
    }
}
