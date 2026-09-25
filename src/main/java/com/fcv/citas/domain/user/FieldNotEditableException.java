package com.fcv.citas.domain.user;

import com.fcv.citas.domain.shared.InvalidRequestException;

/**
 * HU-008 · D25: el cuerpo de la actualizacion del perfil trae un campo que no es editable (HTTP
 * 400, {@code code=FIELD_NOT_EDITABLE}, con {@code field}). La operacion no cambia nada.
 */
public class FieldNotEditableException extends InvalidRequestException {

    public FieldNotEditableException(String field) {
        super("FIELD_NOT_EDITABLE", field, "El campo «" + field + "» no se puede modificar desde el perfil");
    }
}
