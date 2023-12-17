/*
 * Copyright (c) 2023 Marcelo Hashimoto
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package br.pro.hashi.sdx.dao.reflection.mock.reflector.invoke;

public class Fields {
    public boolean publicValue;
    protected boolean protectedValue;
    boolean packageValue;
    private boolean privateValue;

    public Fields() {
        this.publicValue = true;
        this.protectedValue = true;
        this.packageValue = true;
        this.privateValue = true;
    }

    public void setPrivateValue(boolean privateValue) {
        this.privateValue = privateValue;
    }
}
