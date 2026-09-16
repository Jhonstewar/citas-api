/**
 * Adaptadores secundarios de persistencia: entidades JPA, repositorios Spring Data, mappers y
 * adaptadores que implementan los puertos de salida del dominio.
 *
 * <p>El esquema real lo gobierna Flyway ({@code classpath:db/migration}); Hibernate corre con
 * {@code ddl-auto: validate} y solo verifica que las entidades JPA coincidan con las
 * migraciones. Ninguna entidad JPA se usa como entidad de dominio.</p>
 */
package com.fcv.citas.infrastructure.persistence;
