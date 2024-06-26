/*
 * Copyright (c) 2010-2017 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.schema.processor;

import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.delta.ContainerDelta;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ShadowAssociationValueType;

import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;

/**
 * Definition of an association item, e.g., `ri:group`.
 *
 * @see ShadowItemDefinition
 */
public interface ShadowAssociationDefinition
        extends
        PrismContainerDefinition<ShadowAssociationValueType>,
        ShadowItemDefinition<ShadowAssociation, ShadowAssociationValueType> {

    /**
     * Creates a filter that provides all shadows eligible as the target value for this association.
     *
     * FIXME resolve limitations:
     *  - single object class is allowed for given association
     *  - if multiple object types are there, then the filter is for the whole class
     *  - if type type is the default object type, then it's used as such (even if the whole OC should be returned)
     */
    ObjectFilter createTargetObjectsFilter();

    ResourceObjectDefinition getTargetObjectDefinition();

    ShadowAssociationValue instantiateFromIdentifierRealValue(@NotNull QName identifierName, @NotNull Object realValue)
            throws SchemaException;

    ContainerDelta<ShadowAssociationValueType> createEmptyDelta();

    ShadowAssociationClassSimulationDefinition getSimulationDefinition();

    ShadowAssociationClassSimulationDefinition getSimulationDefinitionRequired();

    @NotNull ShadowAssociationClassDefinition getAssociationClassDefinition();

    @NotNull
    ShadowAssociationTypeDefinitionNew getAssociationTypeDefinition();

    boolean isEntitlement();

    default String getResourceOid() {
        return getTargetObjectDefinition().getResourceOid();
    }
}
