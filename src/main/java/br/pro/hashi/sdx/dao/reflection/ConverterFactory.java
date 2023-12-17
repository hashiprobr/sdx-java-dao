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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;

class ConverterFactory {
    private static final ConverterFactory INSTANCE = newInstance();

    private static ConverterFactory newInstance() {
        Reflector reflector = Reflector.getInstance();
        return new ConverterFactory(reflector);
    }

    static ConverterFactory getInstance() {
        return INSTANCE;
    }

    private final Logger logger;
    private final Reflector reflector;
    private final Map<Class<? extends DaoConverter<?, ?>>, DaoConverter<?, ?>> cache;

    ConverterFactory(Reflector reflector) {
        this.logger = LoggerFactory.getLogger(ConverterFactory.class);
        this.reflector = reflector;
        this.cache = new HashMap<>();
    }

    DaoConverter<?, ?> get(Class<? extends DaoConverter<?, ?>> type) {
        DaoConverter<?, ?> converter = cache.get(type);
        if (converter == null) {
            String typeName = type.getName();
            MethodHandle creator = reflector.getCreator(type, typeName);
            try {
                converter = (DaoConverter<?, ?>) creator.invoke();
            } catch (Throwable throwable) {
                throw new ReflectionException(throwable);
            }
            cache.put(type, converter);
            logger.info("Registered %s".formatted(typeName));
        }
        return converter;
    }
}
