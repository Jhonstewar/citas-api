package com.fcv.citas.application.shared;

import java.util.Optional;
import java.util.function.ToLongFunction;

import com.fcv.citas.domain.shared.NotFoundException;

/**
 * Politica unica de ownership (HU-005 CA-05, R3 de S4): un recurso se entrega solo a su titular. El
 * titular sale SIEMPRE del token (lo resuelve el adaptador REST con {@code CurrentUser}), nunca del
 * cuerpo ni de la ruta.
 *
 * <p>Un recurso ajeno responde exactamente igual que uno inexistente —misma excepcion, mismo
 * {@code code} {@code NOT_FOUND} y mismo mensaje—, asi que la API nunca revela que existe un recurso
 * de otra persona (404, no 403). La usan el detalle y la cancelacion de citas del paciente y la
 * agenda del profesional; la reutilizaran reprogramacion, cierre de atencion y perfil.</p>
 */
public final class Ownership {

    private Ownership() {
    }

    /**
     * @param resource        el recurso buscado, o vacio si no existe
     * @param ownerOf         como obtener el id del titular del recurso (paciente, profesional...)
     * @param requesterId     id del titular autenticado, en el mismo espacio de ids que {@code ownerOf}
     * @param notFoundMessage mensaje en español, el mismo para "no existe" y "no es suyo"
     * @return el recurso, si pertenece a {@code requesterId}
     * @throws NotFoundException si no existe o es de otra persona
     */
    public static <T> T requireOwned(Optional<T> resource, ToLongFunction<? super T> ownerOf, long requesterId,
            String notFoundMessage) {
        return resource.filter(r -> ownerOf.applyAsLong(r) == requesterId)
                .orElseThrow(() -> new NotFoundException(notFoundMessage));
    }
}
