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
import br.pro.hashi.sdx.dao.reflection.example.converter.DefaultImplementation;
import br.pro.hashi.sdx.dao.reflection.example.converter.ThrowImplementation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ConverterFactoryTest {
    private static final Lookup LOOKUP = MethodHandles.lookup();

    private AutoCloseable mocks;
    private @Mock Reflector reflector;
    private ConverterFactory f;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        when(reflector.getCreator(any(), any(String.class))).thenAnswer((invocation) -> {
            Class<? extends DaoConverter<?, ?>> type = invocation.getArgument(0);
            Constructor<? extends DaoConverter<?, ?>> constructor = type.getDeclaredConstructor();
            return LOOKUP.unreflectConstructor(constructor);
        });

        f = new ConverterFactory(reflector);
    }

    @AfterEach
    void tearDown() {
        assertDoesNotThrow(() -> mocks.close());
    }

    @Test
    void getsInstance() {
        assertInstanceOf(ConverterFactory.class, ConverterFactory.getInstance());
    }

    @Test
    void gets() {
        DaoConverter<?, ?> converter = f.get(DefaultImplementation.class);
        assertSame(converter, f.get(DefaultImplementation.class));
    }

    @Test
    void doesNotGetThrow() {
        assertThrows(ReflectionException.class, () -> f.get(ThrowImplementation.class));
    }
}
