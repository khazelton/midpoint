/*
 * Copyright (C) 2010-2024 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.lens.projector.focus.inbounds;

import java.util.ArrayList;
import java.util.List;

import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.path.PathKeyedMap;

/**
 * Holds all mappings waiting for their evaluation.
 *
 * It is basically a map indexed by the path of the target item.
 *
 * NOTE: Actually, we do not utilize the fact that it is a map. It could be a plain list of mappings.
 */
public class MappingEvaluationRequests extends PathKeyedMap<List<InboundMappingEvaluationRequest<?, ?>>> {

    public void add(ItemPath targetPath, InboundMappingEvaluationRequest<?, ?> request) {
        computeIfAbsent(targetPath, k -> new ArrayList<>())
                .add(request);
    }
}