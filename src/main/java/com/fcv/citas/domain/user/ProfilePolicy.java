package com.fcv.citas.domain.user;

import java.util.Collection;
import java.util.List;

/**
 * HU-008 · D25 (INC-006): el conjunto de campos editables del perfil, declarado en el servidor.
 *
 * <ul>
 *   <li>Editables: nombres, apellidos y telefono ({@link User#withContact}).</li>
 *   <li>Fijos: el email (es la credencial de login), el tipo y numero de documento (identidad unica
 *       de {@code users}, V1), la contraseña (tiene su propio flujo, HU-007) y los roles.</li>
 * </ul>
 *
 * <p>Un campo fijo en el cuerpo es un error explicito (400 {@code FIELD_NOT_EDITABLE}), no se ignora
 * en silencio: el cliente sabria que intento algo que no ocurrio (HU-008 CA-03). Cualquier otro
 * campo desconocido se ignora, como en el resto de la API.</p>
 */
public final class ProfilePolicy {

    /** Nombres de los campos en el cuerpo JSON (contrato S4 de identidad). */
    public static final List<String> EDITABLE = List.of("firstNames", "lastNames", "phone");
    public static final List<String> FIXED = List.of("email", "documentType", "documentNumber", "password", "roles");

    private ProfilePolicy() {
    }

    /**
     * @param sentFields los campos presentes en el cuerpo, aunque su valor sea nulo
     * @throws FieldNotEditableException con el primer campo fijo presente, en el orden de {@link #FIXED}
     */
    public static void requireOnlyEditable(Collection<String> sentFields) {
        for (String fixed : FIXED) {
            if (sentFields.contains(fixed)) {
                throw new FieldNotEditableException(fixed);
            }
        }
    }
}
