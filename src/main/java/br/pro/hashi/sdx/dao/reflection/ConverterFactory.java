/*
 * Copyright (c) 2023 Marcelo Hashimoto
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package br.pro.hashi.sdx.dao.reflection;

import br.pro.hashi.sdx.dao.DaoConverter;
import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class ConverterFactory {
    private static final ConverterFactory INSTANCE = newInstance();

    private static ConverterFactory newInstance() {
        Reflector reflector = Reflector.getInstance();
        return new ConverterFactory(reflector);
    }

    static ConverterFactory getInstance() {
        return INSTANCE;
    }

    private final Reflector reflector;
    private final ConcurrentMap<Class<? extends DaoConverter<?, ?>>, DaoConverter<?, ?>> cache;

    ConverterFactory(Reflector reflector) {
        this.reflector = reflector;
        this.cache = new ConcurrentHashMap<>();
    }

    DaoConverter<?, ?> get(Class<? extends DaoConverter<?, ?>> type) {
        return cache.computeIfAbsent(type, this::compute);
    }

    private DaoConverter<?, ?> compute(Class<? extends DaoConverter<?, ?>> type) {
        DaoConverter<?, ?> converter;
        MethodHandle creator = reflector.getCreator(type, type.getName());
        try {
            converter = (DaoConverter<?, ?>) creator.invoke();
        } catch (Throwable throwable) {
            throw new ReflectionException(throwable);
        }
        return converter;
    }
}
