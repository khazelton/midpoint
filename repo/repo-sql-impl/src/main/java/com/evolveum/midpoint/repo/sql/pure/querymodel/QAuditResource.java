/*
 * Copyright (C) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sql.pure.querymodel;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;

import java.sql.Types;

import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.ForeignKey;
import com.querydsl.sql.PrimaryKey;

import com.evolveum.midpoint.repo.sql.pure.FlexibleRelationalPathBase;
import com.evolveum.midpoint.repo.sql.pure.querymodel.beans.MAuditResource;

/**
 * Querydsl query type for M_AUDIT_RESOURCE table.
 */
@SuppressWarnings("unused")
public class QAuditResource extends FlexibleRelationalPathBase<MAuditResource> {

    private static final long serialVersionUID = 1568947773;

    public static final String TABLE_NAME = "M_AUDIT_RESOURCE";

    public static final ColumnMetadata RECORD_ID =
            ColumnMetadata.named("RECORD_ID").ofType(Types.BIGINT).withSize(19).notNull();
    public static final ColumnMetadata RESOURCE_OID =
            ColumnMetadata.named("RESOURCEOID").ofType(Types.VARCHAR).withSize(255).notNull();

    public final NumberPath<Long> recordId = createLong("recordId", RECORD_ID);
    public final StringPath resourceOid = createString("resourceOid", RESOURCE_OID);

    public final PrimaryKey<MAuditResource> constraint84 = createPrimaryKey(recordId, resourceOid);
    public final ForeignKey<QAuditEventRecord> auditResourceFk = createForeignKey(recordId, "ID");

    public QAuditResource(String variable) {
        this(variable, "PUBLIC", TABLE_NAME);
    }

    public QAuditResource(String variable, String schema, String table) {
        super(MAuditResource.class, forVariable(variable), schema, table);
    }
}
