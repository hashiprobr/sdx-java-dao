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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class HandleFactoryTest {
    private AutoCloseable mocks;
    private HandleFactory f;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        f = new HandleFactory();
    }

    @AfterEach
    void tearDown() {
        assertDoesNotThrow(() -> mocks.close());
    }

    @Test
    void getsInstance() {
        assertInstanceOf(HandleFactory.class, HandleFactory.getInstance());
    }

    @Test
    void gets() {
        Handle<Object> handle;
        try (@SuppressWarnings("rawtypes") MockedStatic<Handle> handleStatic = mockStatic(Handle.class)) {
            handleStatic.when(() -> Handle.newInstance(Object.class)).thenAnswer((invocation) -> mock(Handle.class));
            handle = f.get(Object.class);
        }
        assertSame(handle, f.get(Object.class));
    }
}
