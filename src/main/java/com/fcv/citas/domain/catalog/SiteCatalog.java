package com.fcv.citas.domain.catalog;

import java.util.Set;

/** Puerto de lectura del catalogo fijo de sedes (RF-05): HIC e ICV. */
public interface SiteCatalog {

    /** Cierto si todas las sedes existen y estan activas. */
    boolean allActive(Set<Integer> siteIds);
}
