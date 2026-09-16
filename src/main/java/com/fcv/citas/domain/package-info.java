/**
 * Nucleo de dominio del sistema de agendamiento (entidades de dominio, value objects,
 * excepciones de negocio y puertos de salida).
 *
 * <p><strong>Regla de arquitectura (hexagonal) - no negociable:</strong> ninguna clase de este
 * paquete ni de sus subpaquetes puede importar Spring, Jakarta Persistence (JPA), Jackson,
 * Servlet API ni cualquier otra libreria de infraestructura. Solo se permite Java SE
 * ({@code java.*}) y otras clases de {@code com.fcv.citas.domain}.</p>
 *
 * <p>Consecuencia practica: las reglas RN-01..RN-12 del PRD se expresan aqui con tipos propios y
 * son verificables con pruebas unitarias puras, sin contexto de Spring ni base de datos.</p>
 *
 * <p>La dependencia va siempre hacia adentro: {@code infrastructure -> application -> domain}.
 * El dominio define interfaces (puertos de salida) que la infraestructura implementa.</p>
 */
package com.fcv.citas.domain;
