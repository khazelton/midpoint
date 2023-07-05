/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.provisioning.impl.shadows.task;

import com.evolveum.midpoint.repo.common.activity.definition.AbstractWorkDefinition;
import com.evolveum.midpoint.repo.common.activity.definition.ObjectSetSpecificationProvider;
import com.evolveum.midpoint.schema.util.task.work.ObjectSetUtil;
import com.evolveum.midpoint.schema.util.task.work.WorkDefinitionBean;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MultiPropagationWorkDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectSetType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;

import org.jetbrains.annotations.NotNull;

public class MultiPropagationWorkDefinition extends AbstractWorkDefinition implements ObjectSetSpecificationProvider {

    private final ObjectSetType resources;

    MultiPropagationWorkDefinition(@NotNull WorkDefinitionBean source) {
        resources = ((MultiPropagationWorkDefinitionType) source.getBean()).getResources();
        ObjectSetUtil.assumeObjectType(resources, ResourceType.COMPLEX_TYPE);
    }

    public ObjectSetType getResources() {
        return resources;
    }

    @Override
    public ObjectSetType getObjectSetSpecification() {
        return resources;
    }

    @Override
    protected void debugDumpContent(StringBuilder sb, int indent) {
        DebugUtil.debugDumpWithLabel(sb, "resources", resources, indent+1);
    }
}
