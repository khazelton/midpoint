/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqlbase.mapping;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import com.querydsl.core.types.Predicate;
import com.querydsl.sql.SQLQuery;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.repo.sqlbase.SqlQueryContext;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;

/**
 * Resolver that knows how to traverse to the specified target query type.
 * By default EXISTS subquery is used which is better for multi-value table stored items
 * to avoid result multiplication.
 * The resolver supports mapping supplier to avoid call cycles during mapping initialization.
 *
 * @param <Q> type of source entity path (where the mapping is)
 * @param <R> row type for {@link Q}
 * @param <TS> schema type for the target entity, can be owning container or object
 * @param <TQ> type of target entity path
 * @param <TR> row type related to the target entity path {@link TQ}
 */
public class TableRelationResolver<
        Q extends FlexibleRelationalPathBase<R>, R,
        TS, TQ extends FlexibleRelationalPathBase<TR>, TR>
        implements ItemRelationResolver<Q, R, TQ, TR> {

    protected final Supplier<QueryTableMapping<TS, TQ, TR>> targetMappingSupplier;
    protected final BiFunction<Q, TQ, Predicate> correlationPredicate;
    private final boolean useSubquery;


    public BiFunction<Q, TQ, Predicate> getCorrelationPredicate() {
        return correlationPredicate;
    }

    public Supplier<QueryTableMapping<TS, TQ, TR>> getTargetMappingSupplier() {
        return targetMappingSupplier;
    }

    public static <Q extends FlexibleRelationalPathBase<R>, R, TS, TQ extends FlexibleRelationalPathBase<TR>, TR>
    TableRelationResolver<Q, R, TS, TQ, TR> usingSubquery(
            @NotNull QueryTableMapping<TS, TQ, TR> targetMapping,
            @NotNull BiFunction<Q, TQ, Predicate> correlationPredicate) {
        return new TableRelationResolver<>(targetMapping, correlationPredicate);
    }

    /**
     * Currently the decision to use `JOIN` is static in the mapping, but it can be more flexible.
     * If the query does not order by such a path, `EXISTS` is more efficient and should be used.
     * This would require order examination first and then using this info in {@link #resolve(SqlQueryContext)},
     * perhaps accessible via the context parameter.
     */
    public static <Q extends FlexibleRelationalPathBase<R>, R, TS, TQ extends FlexibleRelationalPathBase<TR>, TR>
    TableRelationResolver<Q, R, TS, TQ, TR> usingJoin(
            @NotNull Supplier<QueryTableMapping<TS, TQ, TR>> targetMappingSupplier,
            @NotNull BiFunction<Q, TQ, Predicate> correlationPredicate) {
        return new TableRelationResolver<>(targetMappingSupplier, correlationPredicate);
    }

    /**
     * Constructor for relation resolver using `EXISTS` subquery to the table.
     * This is good for multi-value containers.
     */
    protected TableRelationResolver(
            @NotNull QueryTableMapping<TS, TQ, TR> targetMapping,
            @NotNull BiFunction<Q, TQ, Predicate> correlationPredicate) {
        this.targetMappingSupplier = () -> targetMapping;
        this.correlationPredicate = correlationPredicate;
        this.useSubquery = true;
    }

    /**
     * Constructor for table-stored relation resolver using `LEFT JOIN`.
     * This is good when we know only one result will match the joining condition,
     * e.g. owning object or object referenced by embedded (single-value) reference.
     * Using `JOIN` is necessary if ordering by the target is required.
     */
    private TableRelationResolver(
            @NotNull Supplier<QueryTableMapping<TS, TQ, TR>> targetMappingSupplier,
            @NotNull BiFunction<Q, TQ, Predicate> correlationPredicate) {
        this.targetMappingSupplier = targetMappingSupplier;
        this.correlationPredicate = correlationPredicate;
        this.useSubquery = false;
    }

    /**
     * Creates the EXISTS subquery using provided query context.
     *
     * @param context query context used for subquery creation
     * @return result with context for subquery entity path and its mapping
     */
    @Override
    public ResolutionResult<TQ, TR> resolve(SqlQueryContext<?, Q, R> context) {
        if (useSubquery) {
            SqlQueryContext<TS, TQ, TR> subcontext = context.subquery(targetMappingSupplier.get());
            SQLQuery<?> subquery = subcontext.sqlQuery();
            subquery.where(correlationPredicate.apply(context.path(), subcontext.path()));

            return new ResolutionResult<>(subcontext, subcontext.mapping(), true);
        } else {
            SqlQueryContext<TS, TQ, TR> subcontext = context.leftJoin(
                    targetMappingSupplier.get(), correlationPredicate);
            return new ResolutionResult<>(subcontext, subcontext.mapping());
        }
    }

    public TableRelationResolver<Q, R, TS, TQ, TR> replaceTable(QueryTableMapping<? extends TS, TQ, TR> target) {
        // FIXME: Add check

        return new TableRelationResolver(() -> target, correlationPredicate);
    }

    public TableRelationResolver<Q, R, TS, TQ, TR>  forceSubquery() {

        return usingSubquery(targetMappingSupplier.get(), correlationPredicate);
    }

    public <AQ extends FlexibleRelationalPathBase<AR>, AS, AR>
    TableRelationResolver<TQ, TR, AS, AQ, AR> reverse(
            @NotNull QueryTableMapping<AS, AQ, AR> targetMapping) {
        //noinspection unchecked
        return new TableRelationResolver<>(targetMapping, (t, a) -> correlationPredicate.apply((Q) a, t));
    }
}
