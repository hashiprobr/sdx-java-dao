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

import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

class ParserFactory {
    private static final ParserFactory INSTANCE = newInstance();

    private static ParserFactory newInstance() {
        Reflector reflector = Reflector.getInstance();
        return new ParserFactory(reflector);
    }

    static ParserFactory getInstance() {
        return INSTANCE;
    }

    private final Reflector reflector;
    private final ConcurrentMap<Class<?>, Function<String, ?>> cache;

    ParserFactory(Reflector reflector) {
        ConcurrentMap<Class<?>, Function<String, ?>> cache = new ConcurrentHashMap<>();
        cache.put(boolean.class, Boolean::parseBoolean);
        cache.put(byte.class, Byte::parseByte);
        cache.put(short.class, Short::parseShort);
        cache.put(int.class, Integer::parseInt);
        cache.put(long.class, Long::parseLong);
        cache.put(float.class, Float::parseFloat);
        cache.put(double.class, Double::parseDouble);
        cache.put(char.class, this::parseChar);
        cache.put(Character.class, this::parseChar);
        cache.put(BigInteger.class, BigInteger::new);
        cache.put(BigDecimal.class, BigDecimal::new);
        cache.put(String.class, (valueString) -> valueString);
        this.reflector = reflector;
        this.cache = cache;
    }

    char parseChar(String valueString) {
        if (valueString.length() != 1) {
            throw new IllegalArgumentException("Value string must have exactly one character");
        }
        return valueString.charAt(0);
    }

    @SuppressWarnings("unchecked")
    public <K> Function<String, K> get(Class<K> type) {
        return (Function<String, K>) cache.computeIfAbsent(type, this::compute);
    }

    private <K> Function<String, K> compute(Class<K> type) {
        String typeName = type.getName();
        Method method;
        try {
            method = type.getDeclaredMethod("valueOf", String.class);
        } catch (NoSuchMethodException exception) {
            throw new ReflectionException("Class %s must have a valueOf(String) method".formatted(typeName));
        }
        if (!method.getReturnType().equals(type)) {
            throw new ReflectionException("Method valueOf(String) of class %s must return an instance of this class".formatted(typeName));
        }
        int modifiers = method.getModifiers();
        if (!(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers))) {
            throw new ReflectionException("Method valueOf(String) of class %s must be public and static".formatted(typeName));
        }
        for (Class<?> exceptionType : method.getExceptionTypes()) {
            if (!RuntimeException.class.isAssignableFrom(exceptionType)) {
                throw new ReflectionException("Method valueOf(String) of class %s can only throw unchecked exceptions".formatted(typeName));
            }
        }
        MethodHandle handle = reflector.unreflect(method);
        return (valueString) -> invoke(handle, valueString);
    }

    @SuppressWarnings("unchecked")
    <K> K invoke(MethodHandle handle, String valueString) {
        K value;
        try {
            value = (K) handle.invoke(valueString);
        } catch (Throwable throwable) {
            if (throwable instanceof RuntimeException exception) {
                throw exception;
            }
            throw new AssertionError(throwable);
        }
        return value;
    }
}
