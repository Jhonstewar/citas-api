/**
 * Adaptadores primarios HTTP: controladores REST, DTO de request/response y manejo de errores.
 *
 * <p>Los DTO viven aqui y nunca se filtran al dominio: el mapeo DTO -> modelo de dominio ocurre
 * en el borde. Todavia no hay controladores; la autenticacion la implementa un slice posterior.</p>
 */
package com.fcv.citas.infrastructure.rest;
