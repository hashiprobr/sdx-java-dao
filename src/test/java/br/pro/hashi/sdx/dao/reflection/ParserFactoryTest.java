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

import br.pro.hashi.sdx.dao.reflection.example.parser.*;
import br.pro.hashi.sdx.dao.reflection.exception.ReflectionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ParserFactoryTest {
    private static final Lookup LOOKUP = MethodHandles.lookup();

    private AutoCloseable mocks;
    private @Mock Reflector reflector;
    private ParserFactory f;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        when(reflector.unreflect(any(Method.class))).thenAnswer((invocation) -> {
            Method method = invocation.getArgument(0);
            return LOOKUP.unreflect(method);
        });

        f = new ParserFactory(reflector);
    }

    @AfterEach
    void tearDown() {
        assertDoesNotThrow(() -> mocks.close());
    }

    @Test
    void getsInstance() {
        assertInstanceOf(ParserFactory.class, ParserFactory.getInstance());
    }

    @Test
    void doesNotParseCharFromEmptyString() {
        assertThrows(IllegalArgumentException.class, () -> f.parseChar(""));
    }

    @Test
    void doesNotParseCharFromLargeString() {
        assertThrows(IllegalArgumentException.class, () -> f.parseChar("cc"));
    }

    @Test
    void getsAndAppliesBooleanParser() {
        assertTrue(f.get(boolean.class).apply("true"));
    }

    @Test
    void getsAndAppliesByteParser() {
        assertEquals((byte) 1, f.get(byte.class).apply("1"));
    }

    @Test
    void getsAndAppliesShortParser() {
        assertEquals((short) 2, f.get(short.class).apply("2"));
    }

    @Test
    void getsAndAppliesIntParser() {
        assertEquals(3, f.get(int.class).apply("3"));
    }

    @Test
    void getsAndAppliesLongParser() {
        assertEquals(4L, f.get(long.class).apply("4"));
    }

    @Test
    void getsAndAppliesFloatParser() {
        assertEquals(5.5F, f.get(float.class).apply("5.5"));
    }

    @Test
    void getsAndAppliesDoubleParser() {
        assertEquals(6.6, f.get(double.class).apply("6.6"));
    }

    @Test
    void getsAndAppliesCharParser() {
        assertEquals('c', f.get(char.class).apply("c"));
    }

    @Test
    void getsAndAppliesCharacterParser() {
        assertEquals(Character.valueOf('c'), f.get(Character.class).apply("c"));
    }

    @Test
    void getsAndAppliesBigIntegerParser() {
        assertEquals(BigInteger.valueOf(7), f.get(BigInteger.class).apply("7"));
    }

    @Test
    void getsAndAppliesBigDecimalParser() {
        assertEquals(BigDecimal.valueOf(8.8), f.get(BigDecimal.class).apply("8.8"));
    }

    @Test
    void getsAndAppliesStringParser() {
        assertEquals("s", f.get(String.class).apply("s"));
    }

    @Test
    void getsAndApplies() {
        Function<String, DefaultMethod> parser = f.get(DefaultMethod.class);
        assertSame(parser, f.get(DefaultMethod.class));
        assertInstanceOf(DefaultMethod.class, parser.apply("s"));
    }

    @ParameterizedTest
    @ValueSource(classes = {
            MissingMethod.class,
            NonInstanceMethod.class,
            NonPublicMethod.class,
            NonStaticMethod.class,
            CheckedMethod.class})
    void doesNotGet(Class<?> type) {
        assertThrows(ReflectionException.class, () -> f.get(type));
    }

    @Test
    void doesNotApplyUnchecked() {
        Function<String, UncheckedMethod> parser = f.get(UncheckedMethod.class);
        assertThrows(RuntimeException.class, () -> parser.apply("s"));
    }

    @Test
    void doesNotInvokeChecked() {
        MethodHandle handle = assertDoesNotThrow(() -> {
            Method method = CheckedMethod.class.getDeclaredMethod("valueOf", String.class);
            return LOOKUP.unreflect(method);
        });
        assertThrows(AssertionError.class, () -> f.invoke(handle, "s"));
    }
}
