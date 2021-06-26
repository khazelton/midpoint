/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.mapping;

import java.util.function.Function;

import com.querydsl.core.types.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.repo.sqale.delta.ItemDeltaProcessor;
import com.evolveum.midpoint.repo.sqale.delta.item.ExtensionItemDeltaProcessor;
import com.evolveum.midpoint.repo.sqale.filtering.ExtensionItemFilterProcessor;
import com.evolveum.midpoint.repo.sqale.jsonb.JsonbPath;
import com.evolveum.midpoint.repo.sqale.qmodel.ext.MExtItemHolderType;
import com.evolveum.midpoint.repo.sqale.update.SqaleUpdateContext;
import com.evolveum.midpoint.repo.sqlbase.SqlQueryContext;
import com.evolveum.midpoint.repo.sqlbase.filtering.item.ItemFilterProcessor;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;

/**
 * TODO: this much "lazier" than normal item mapper, we'll get to ext definitions only here, not sooner.
 *
 * @param <Q> entity path owning the mapped item
 * @param <R> row type with the mapped item
 */
public class ExtensionItemSqlMapper<Q extends FlexibleRelationalPathBase<R>, R>
        implements UpdatableItemSqlMapper<Q, R> {

    private final Function<Q, JsonbPath> rootToExtensionPath;
    private final MExtItemHolderType holderType;

    public ExtensionItemSqlMapper(
            @NotNull Function<Q, JsonbPath> rootToExtensionPath,
            @NotNull MExtItemHolderType holderType) {
        this.rootToExtensionPath = rootToExtensionPath;
        this.holderType = holderType;
    }

    @Override
    public @Nullable Path<?> itemPrimaryPath(Q entityPath) {
        // TODO this currently does NOT work because:
        //  - sorting by ext does not make sense, we want to sort by ext->something (or ->>)
        //  - that also means that we don't want to return Path but Expression instead
        //  - but sorting by ext/something is not yet supported in SqlQueryContext.processOrdering
        return rootToExtensionPath.apply(entityPath);
    }

    @Override
    public @Nullable <T extends ObjectFilter> ItemFilterProcessor<T> createFilterProcessor(
            SqlQueryContext<?, ?, ?> sqlQueryContext) {
        //noinspection unchecked
        return (ItemFilterProcessor<T>) new ExtensionItemFilterProcessor(
                sqlQueryContext,
                (Function<FlexibleRelationalPathBase<?>, JsonbPath>) rootToExtensionPath,
                holderType);
    }

    @Override
    public ItemDeltaProcessor createItemDeltaProcessor(
            SqaleUpdateContext<?, ?, ?> sqlUpdateContext) {
        return new ExtensionItemDeltaProcessor(sqlUpdateContext, holderType);
    }
}