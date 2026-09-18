package com.fcv.citas.domain.catalog;

import java.util.List;
import java.util.Optional;

/** Puerto de salida del catalogo configurable de especialidades (RF-06, HU-011). */
public interface SpecialtyRepository {

    List<Specialty> findAll();

    List<Specialty> findAllActive();

    Optional<Specialty> findById(int id);

    boolean existsByCode(String code);

    /** Nombre ya usado por otra especialidad (sin distinguir mayusculas); {@code excludingId} opcional. */
    boolean existsByName(String name, Integer excludingId);

    /** Inserta si {@code id} es nulo; si no, actualiza nombre, tipo, duracion y estado. */
    Specialty save(Specialty specialty);

    /** Cierto si la usa algun profesional o alguna cita: entonces solo se puede desactivar. */
    boolean isReferenced(int id);

    void delete(int id);
}
