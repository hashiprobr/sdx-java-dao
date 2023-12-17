/*
 * Copyright (c) 2023 Marcelo Hashimoto
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package br.pro.hashi.sdx.dao.reflection.exception;

public class AnnotationException extends ReflectionException {
    public AnnotationException(Class<?> type, String message) {
        this(type.getName(), message);
    }

    public AnnotationException(String typeName, String message) {
        super("%s: %s".formatted(typeName, message));
    }
}
