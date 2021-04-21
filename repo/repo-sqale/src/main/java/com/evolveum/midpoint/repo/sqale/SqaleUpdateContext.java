/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale;

import javax.xml.namespace.QName;

import com.querydsl.core.types.Path;

import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;

public abstract class SqaleUpdateContext<S, Q extends FlexibleRelationalPathBase<R>, R> {

    protected final SqaleTransformerSupport transformerSupport;
    protected final JdbcSession jdbcSession;
    protected final S object;
    protected final R row;

    public SqaleUpdateContext(SqaleTransformerSupport sqlTransformerSupport,
            JdbcSession jdbcSession, S object, R row) {
        this.transformerSupport = sqlTransformerSupport;
        this.jdbcSession = jdbcSession;
        this.object = object;
        this.row = row;
    }

    public SqaleTransformerSupport transformerSupport() {
        return transformerSupport;
    }

    public Integer processCacheableRelation(QName relation) {
        return transformerSupport.processCacheableRelation(relation);
    }

    public Integer processCacheableUri(String uri) {
        return transformerSupport.processCacheableUri(uri);
    }

    public JdbcSession jdbcSession() {
        return jdbcSession;
    }

    public S schemaObject() {
        return object;
    }

    public R row() {
        return row;
    }

    public abstract Q path();

    public abstract <P extends Path<T>, T> void set(P path, T value);
}
