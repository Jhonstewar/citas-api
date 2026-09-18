package com.fcv.citas.infrastructure.rest.admin;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de activar/desactivar: {@code {"active": true|false}}. */
record ActiveRequest(@NotNull(message = "Indique si queda activo o inactivo") Boolean active) {
}
