/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.visualizer;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.ChangeType;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;

import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.impl.visualizer.output.VisualizationImpl;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.SingleLocalizableMessage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

/**
 * Created by Viliam Repan (lazyman).
 */
@Component
public class ActivationDescriptionHandler implements VisualizationDescriptionHandler {

    private static final Trace LOGGER = TraceManager.getTrace(ActivationDescriptionHandler.class);

    @Override
    public boolean match(VisualizationImpl visualization, VisualizationImpl parentVisualization) {

        PrismContainerValue<?> value = visualization.getSourceValue();
        if (value == null) {
            return false;
        }

        if (parentVisualization != null && parentVisualization.getSourceDelta() != null) {
            if (existDelta(visualization, parentVisualization, ActivationType.F_EFFECTIVE_STATUS)) {
                return true;
            }
            if (existDelta(visualization, parentVisualization, ActivationType.F_ADMINISTRATIVE_STATUS)) {
                return true;
            }
        }

        if (ChangeType.ADD != visualization.getChangeType()) {
            return false;
        }

        if (ActivationType.class.equals(value.getCompileTimeClass())) {
            // if there's effective status
            return value.findProperty(ActivationType.F_EFFECTIVE_STATUS) != null;
        }

        // we're modifying/deleting effective status
        return ActivationType.F_EFFECTIVE_STATUS.equivalent(value.getPath());
    }

    private boolean existDelta(VisualizationImpl visualization, VisualizationImpl parentVisualization, ItemName path) {
        ActivationStatusType status = getRealValueForDelta(visualization, parentVisualization, path);
        if (status != null) {
            return true;
        }
        return false;
    }

    @Override
    public void apply(VisualizationImpl visualization, VisualizationImpl parentVisualization, Task task, OperationResult result) {
        ActivationStatusType status;

        status = getRealValueForDelta(visualization, parentVisualization, ActivationType.F_EFFECTIVE_STATUS);

        if (status == null) {
            status = getRealValueForDelta(visualization, parentVisualization, ActivationType.F_ADMINISTRATIVE_STATUS);
        }

        PrismContainerValue<?> value = visualization.getSourceValue();

        if (status == null) {
            PrismProperty<ActivationStatusType> effectiveStatus = value.findProperty(ActivationType.F_EFFECTIVE_STATUS);
            status = effectiveStatus.getRealValue();
        }

        if (status == null) {
            return;
        }

        PrismContainerValue root = value.getRootValue();
        PrismContainerDefinition rootDef = root.getDefinition();
        Class clazz = root.getCompileTimeClass();

        ItemPath path = value.getPath();
        //different as '/activationContainerPath'
        if (rootDef != null && path.size() > 1) {
            path = path.allExceptLast();
            path = path.removeIds();
            PrismContainerDefinition findDef = rootDef.findContainerDefinition(path);
            if (findDef != null) {
                rootDef = findDef;
                clazz = findDef.getCompileTimeClass();
            }
        }

        String typeKey;
        if (clazz != null && ObjectType.class.isAssignableFrom(clazz)) {
            typeKey = "ObjectTypes." + ObjectTypes.getObjectType(clazz).name();
        } else {
            typeKey = rootDef != null ? rootDef.getDisplayName() : null;
        }

        if (typeKey == null) {
            typeKey = "ObjectTypes.OBJECT";
        }

        visualization.getName().setOverview(
                new SingleLocalizableMessage("ActivationDescriptionHandler.effectiveStatus", new Object[] {
                        new SingleLocalizableMessage(typeKey),
                        new SingleLocalizableMessage("ActivationDescriptionHandler.ActivationStatusType." + status.name())
                })
        );
    }

    private ActivationStatusType getRealValueForDelta(
            VisualizationImpl visualization, VisualizationImpl parentVisualization, ItemName itemPath) {
        try {
            PrismContainerValue<?> value = visualization.getSourceValue();
            ItemPath path = ItemPath.create(visualization.getSourceRelPath());
            if (value.getId() != null) {
                path = path.append(value.getId());
            }
            path = path.append(itemPath);

            PropertyDelta<Object> deltaItem = parentVisualization.getSourceDelta().findPropertyDelta(ItemPath.create(
                    visualization.getSourceRelPath(), path));

            if (deltaItem != null && deltaItem.getItemNew() != null) {
                return (ActivationStatusType) deltaItem.getItemNew().getRealValue();
            }
        } catch (Exception e) {
            LOGGER.trace("Couldn't find delta item for path " + itemPath, e);
        }
        return null;
    }
}
