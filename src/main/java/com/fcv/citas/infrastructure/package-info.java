/**
 * Adaptadores e infraestructura: unico lugar donde se permiten Spring, JPA, Jackson y la
 * configuracion del framework.
 *
 * <ul>
 *   <li>{@code rest} - adaptadores primarios (controladores, DTO, manejo de errores).</li>
 *   <li>{@code persistence} - adaptadores secundarios (entidades JPA, repositorios, mappers).</li>
 *   <li>{@code security} - configuracion de Spring Security.</li>
 *   <li>{@code config} - configuracion general y CORS.</li>
 * </ul>
 */
package com.fcv.citas.infrastructure;
