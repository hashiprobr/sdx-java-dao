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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class HandleFactory {
    private static final HandleFactory INSTANCE = new HandleFactory();

    public static HandleFactory getInstance() {
        return INSTANCE;
    }

    private final ConcurrentMap<Class<?>, Handle<?>> cache;

    HandleFactory() {
        this.cache = new ConcurrentHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public <E> Handle<E> get(Class<E> type) {
        return (Handle<E>) cache.computeIfAbsent(type, Handle::newInstance);
    }
}
