/*
 * Copyright (c) 2023 Marcelo Hashimoto.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

/**
 * Defines a simple typed DAO framework based on Google Cloud Firestore and Storage.
 */
module br.pro.hashi.sdx.dao {
    requires firebase.admin;
    requires google.cloud.firestore;
    requires google.cloud.storage;
    requires google.cloud.core;
    requires com.google.auth;
    requires com.google.auth.oauth2;
    requires com.google.api.apicommon;
    requires protobuf.java;
    requires org.slf4j;

    exports br.pro.hashi.sdx.dao;
    exports br.pro.hashi.sdx.dao.annotation;
    exports br.pro.hashi.sdx.dao.exception;
}
