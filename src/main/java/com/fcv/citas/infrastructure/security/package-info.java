/**
 * Adaptadores de seguridad: cadena de filtros stateless con resource server JWT (HS256), beans
 * {@code JwtEncoder}/{@code JwtDecoder}, conversion del claim {@code roles} a {@code ROLE_*},
 * resolucion del Bearer que ignora la cabecera en las rutas publicas de autenticacion, hash de
 * contraseñas (BCrypt delegante) y generacion del refresh token opaco.
 */
package com.fcv.citas.infrastructure.security;
