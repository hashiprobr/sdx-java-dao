/*
 * Copyright (c) 2023 Marcelo Hashimoto
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package br.pro.hashi.sdx.dao.reflection.mock.parser;

public class NonStaticMethod {
    private boolean value;

    public NonStaticMethod valueOf(String s) {
        return new NonStaticMethod();
    }
}