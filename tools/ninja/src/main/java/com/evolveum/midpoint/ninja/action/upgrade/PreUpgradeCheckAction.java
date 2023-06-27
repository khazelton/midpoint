/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.ninja.action.upgrade;

import java.util.*;

import com.evolveum.midpoint.ninja.action.Action;
import com.evolveum.midpoint.ninja.util.ConsoleFormat;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.LabeledString;
import com.evolveum.midpoint.schema.RepositoryDiag;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.NodeType;

public class PreUpgradeCheckAction extends Action<PreUpgradeCheckOptions, Boolean> {

    @Override
    public String getOperationName() {
        return "pre-upgrade checks";
    }

    @Override
    public Boolean execute() throws Exception {
        final RepositoryService repository = context.getRepository();

        if (!repository.isNative()) {
            log.error("Repository implementation is not using native PostgreSQL");
            return false;
        }

        if (!checkNodesVersion(repository)) {
            return false;
        }

        return checkDatabaseSchemaVersion(repository);
    }

    private boolean checkDatabaseSchemaVersion(RepositoryService repository) {
        RepositoryDiag diag = repository.getRepositoryDiag();

        boolean result = validateChangeNumber(
                diag.getAdditionalDetails(), UpgradeConstants.LABEL_SCHEMA_CHANGE_NUMBER,
                UpgradeConstants.SUPPORTED_SCHEMA_CHANGE_NUMBER);
        if (!result) {
            return false;
        }

        return validateChangeNumber(
                diag.getAdditionalDetails(), UpgradeConstants.LABEL_SCHEMA_AUDIT_CHANGE_NUMBER,
                UpgradeConstants.SUPPORTED_SCHEMA_AUDIT_CHANGE_NUMBER);
    }

    private boolean validateChangeNumber(List<LabeledString> list, String label, String expected) {
        String number = getValue(list, label);
        boolean equals = Objects.equals(number, expected);

        if (!equals) {
            log.error(ConsoleFormat.formatError(
                    "Database schema change number (" + number + ") doesn't match supported one (" + expected + ") for label "
                            + label + "."));
        } else {
            log.info("Database schema change number matches supported one (" + expected + ") for label " + label + ".");
        }

        return equals;
    }

    private String getValue(List<LabeledString> list, String name) {
        if (list == null) {
            return null;
        }

        LabeledString labeled = list.stream().filter(ls -> name.equals(ls.getLabel())).findFirst().orElse(null);
        return labeled != null ? labeled.getData() : null;
    }

    private boolean checkNodesVersion(RepositoryService repository) throws SchemaException {
        OperationResult result = new OperationResult("Search nodes");

        SearchResultList<PrismObject<NodeType>> nodes = repository.searchObjects(NodeType.class, null, null, result);
        Set<String> versions = new HashSet<>();

        nodes.forEach(o -> {
            NodeType node = o.asObjectable();
            if (node.getBuild() == null) {
                return;
            }

            versions.add(node.getBuild().getVersion());
        });

        if (versions.isEmpty()) {
            log.info(ConsoleFormat.formatWarn("There are zero nodes in cluster to validate current midPoint version."));

            return true;
        } else if (versions.size() > 1) {
            log.error(ConsoleFormat.formatError(
                    "There are nodes with different versions of midPoint. Please remove incorrect nodes from cluster."));
            return false;
        }

        log.info(ConsoleFormat.formatInfoMessageWithParameter(
                "Node versions in cluster: ", Arrays.toString(versions.toArray())));

        String version = versions.iterator().next();
        boolean match = Arrays.asList(UpgradeConstants.SUPPORTED_VERSIONS).contains(version);
        if (!match) {
            log.error(ConsoleFormat.formatErrorMessageWithParameter(
                    "There are midPoint nodes with versions that doesn't match supported version for upgrade (" +
                            Arrays.toString(UpgradeConstants.SUPPORTED_VERSIONS) + ")", Arrays.toString(versions.toArray())));
            return false;
        }

        return true;
    }
}
