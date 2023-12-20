/*
 * Copyright (c) 2023 Marcelo Hashimoto
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package br.pro.hashi.sdx.dao.reflection.example.converter;

import br.pro.hashi.sdx.dao.DaoConverter;

public class DefaultImplementation implements DaoConverter<Integer, Double> {
    @Override
    public Double to(Integer source) {
        return source.doubleValue();
    }

    @Override
    public Integer from(Double target) {
        return target.intValue();
    }
}
