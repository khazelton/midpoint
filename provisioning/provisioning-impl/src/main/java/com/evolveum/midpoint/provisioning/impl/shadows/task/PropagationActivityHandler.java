/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.provisioning.impl.shadows.task;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.xml.ns._public.common.common_3.PropagationWorkStateType;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.provisioning.impl.shadows.ShadowsFacade;
import com.evolveum.midpoint.repo.common.activity.definition.WorkDefinitionFactory;
import com.evolveum.midpoint.repo.common.activity.execution.ExecutionInstantiationContext;
import com.evolveum.midpoint.repo.common.activity.handlers.ActivityHandler;
import com.evolveum.midpoint.repo.common.activity.handlers.ActivityHandlerRegistry;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.xml.ns._public.common.common_3.PropagationWorkDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemObjectsType;

/**
 * TODO
 */
@Component
public class PropagationActivityHandler implements ActivityHandler<PropagationWorkDefinition, PropagationActivityHandler> {

    private static final String LEGACY_HANDLER_URI = SchemaConstants.NS_PROVISIONING_TASK + "/propagation/handler-3";
    private static final String ARCHETYPE_OID = SystemObjectsType.ARCHETYPE_SYSTEM_TASK.value(); // TODO

    @Autowired WorkDefinitionFactory workDefinitionFactory;
    @Autowired ActivityHandlerRegistry handlerRegistry;
    @Autowired ProvisioningService provisioningService;
    @Autowired ShadowsFacade shadowsFacade;

    @PostConstruct
    public void register() {
        handlerRegistry.register(PropagationWorkDefinitionType.COMPLEX_TYPE, LEGACY_HANDLER_URI,
                PropagationWorkDefinition.class, PropagationWorkDefinition::new, this);
    }

    @PreDestroy
    public void unregister() {
        handlerRegistry.unregister(PropagationWorkDefinitionType.COMPLEX_TYPE, LEGACY_HANDLER_URI,
                PropagationWorkDefinition.class);
    }

    @Override
    public @NotNull PropagationActivityExecution createExecution(
            @NotNull ExecutionInstantiationContext<PropagationWorkDefinition, PropagationActivityHandler> context,
            @NotNull OperationResult result) {
        return new PropagationActivityExecution(context);
    }

    @Override
    public String getIdentifierPrefix() {
        return "propagation";
    }
}
