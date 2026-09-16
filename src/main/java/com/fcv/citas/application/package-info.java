/**
 * Capa de aplicacion: casos de uso y puertos de entrada del sistema de agendamiento.
 *
 * <p><strong>Regla de arquitectura (hexagonal) - no negociable:</strong> este paquete y sus
 * subpaquetes no pueden importar Spring, Jakarta Persistence (JPA), Jackson ni ninguna otra
 * libreria de infraestructura. Solo Java SE ({@code java.*}) y {@code com.fcv.citas.domain}.</p>
 *
 * <p>Los casos de uso se orquestan contra los puertos de salida declarados en el dominio; quien
 * los conecta con JPA, REST o seguridad es {@code com.fcv.citas.infrastructure}, mediante
 * configuracion explicita de beans. Nada en este paquete se anota con {@code @Service},
 * {@code @Component} ni {@code @Transactional}.</p>
 */
package com.fcv.citas.application;
