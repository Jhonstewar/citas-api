package com.fcv.citas.domain.user;

/** Puerto de lectura del catalogo fijo de tipos de documento. */
public interface DocumentTypeCatalog {

    boolean isActiveCode(String code);
}
