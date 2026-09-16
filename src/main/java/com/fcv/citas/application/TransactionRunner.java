package com.fcv.citas.application;

import java.util.function.Supplier;

/**
 * Puerto de la capa de aplicacion para delimitar una unidad transaccional sin depender de
 * Spring. La infraestructura lo implementa con {@code TransactionTemplate}.
 */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);
}
