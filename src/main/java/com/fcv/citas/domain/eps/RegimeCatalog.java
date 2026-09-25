package com.fcv.citas.domain.eps;

/** Puerto de lectura del catalogo FIJO de regimenes (V4), que la API no modifica (HU-012 CA-02). */
public interface RegimeCatalog {

    boolean exists(String code);
}
