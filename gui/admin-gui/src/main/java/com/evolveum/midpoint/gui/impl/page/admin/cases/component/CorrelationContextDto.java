/*
 * Copyright (C) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.cases.component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.evolveum.midpoint.model.api.correlation.CorrelationService;

import com.evolveum.midpoint.util.DebugUtil;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.model.api.correlation.CorrelationCaseDescription;
import com.evolveum.midpoint.model.api.correlation.CorrelationCaseDescription.CandidateDescription;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.cases.CorrelationCaseUtil;
import com.evolveum.midpoint.schema.util.cases.OwnerOptionIdentifier;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Represents the whole correlation context: a set of options, including "new owner" one.
 */
class CorrelationContextDto implements Serializable {

    private static final Trace LOGGER = TraceManager.getTrace(CorrelationContextDto.class);

    static final String F_OPTION_HEADERS = "optionHeaders";
    static final String F_CORRELATION_OPTIONS = "correlationOptions";
    static final String F_CORRELATION_PROPERTIES = "correlationProperties";

    // TODO move into properties
    private static final String TEXT_BEING_CORRELATED = "Object being correlated";
    private static final String TEXT_CANDIDATE = "Correlation candidate %d";

    /**
     * Headers for individual options.
     */
    private final List<String> optionHeaders = new ArrayList<>();

    /**
     * All correlation options. Correspond to columns in the correlation options table.
     *
     * The first one is the "new owner" option.
     */
    private final List<CorrelationOptionDto> correlationOptions = new ArrayList<>();

    /**
     * Properties that will be presented by the GUI. Obtained either from the correlation configuration
     * or determined dynamically.
     *
     * Correspond to rows in the correlation options table.
     */
    private final List<CorrelationCaseDescription.CorrelationProperty> correlationProperties = new ArrayList<>();

    CorrelationContextDto(CaseType aCase, PageBase pageBase, Task task, OperationResult result) throws CommonException {
        load(aCase, pageBase, task, result);
    }

    private void load(CaseType aCase, PageBase pageBase, Task task, OperationResult result) throws CommonException {
        ResourceObjectOwnerOptionsType ownerOptions = CorrelationCaseUtil.getOwnerOptions(aCase);
        if (ownerOptions != null) {
            // TODO Reconsider the necessity of this (note that everything is in candidate descriptions provided later,
            //  so we probably do not need the resolved candidates)
            resolvePotentialOwners(ownerOptions, pageBase, task, result);
        }
        CorrelationCaseDescription<?> correlationCaseDescription =
                pageBase.getCorrelationService().describeCorrelationCase(
                        aCase,
                        new CorrelationService.CorrelationCaseDescriptionOptions().explain(true),
                        task,
                        result);
        createCorrelationOptions(aCase, correlationCaseDescription);
        createCorrelationPropertiesDefinitions(aCase, pageBase, task, result);
    }

    private void resolvePotentialOwners(ResourceObjectOwnerOptionsType potentialOwners, PageBase pageBase, Task task, OperationResult result) {
        for (ResourceObjectOwnerOptionType potentialOwner : potentialOwners.getOption()) {
            resolve(potentialOwner.getCandidateOwnerRef(), "candidate " + potentialOwner.getIdentifier(), pageBase, task, result);
        }
    }

    private void resolve(ObjectReferenceType ref, String desc, PageBase pageBase, Task task, OperationResult result) {
        LOGGER.trace("Resolving {}: {}", desc, ref);
        if (ref == null || ref.getOid() == null) {
            LOGGER.trace("Null ref or no OID");
            return;
        }
        if (ref.getObject() != null) {
            LOGGER.trace("Already resolved");
            return;
        }
        ref.asReferenceValue().setObject(
                loadObject(ref.getOid(), pageBase, task, result));
    }

    private PrismObject<?> loadObject(String oid, PageBase pageBase, Task task, OperationResult result) {
        try {
            // We do not need the identities container here, as the secondary values are obtained from
            // the correlation case description.
            return pageBase.getModelService()
                    .getObject(FocusType.class, oid, null, task, result);
        } catch (Exception e) {
            result.recordFatalError(e);
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't resolve focus {}", e, oid);
            return null;
        }
    }

    private void createCorrelationOptions(CaseType aCase, CorrelationCaseDescription<?> caseDescription)
            throws SchemaException {
        Map<String, ? extends CandidateDescription<?>> candidates =
                caseDescription.getCandidates().stream()
                        .collect(Collectors.toMap(CandidateDescription::getOid, c -> c));

        CaseCorrelationContextType context = aCase.getCorrelationContext();
        int suggestionNumber = 1;
        for (ResourceObjectOwnerOptionType potentialOwner : CorrelationCaseUtil.getOwnerOptionsList(aCase)) {
            OwnerOptionIdentifier identifier = OwnerOptionIdentifier.of(potentialOwner);
            String optionIdentifierRaw = potentialOwner.getIdentifier(); // the same as identifier.getStringValue()
            assert optionIdentifierRaw != null;
            if (identifier.isNewOwner()) {
                optionHeaders.add(0, TEXT_BEING_CORRELATED);
                correlationOptions.add(0,
                        new CorrelationOptionDto.NewOwner(context.getPreFocusRef(), optionIdentifierRaw));
            } else {
                optionHeaders.add(String.format(TEXT_CANDIDATE, suggestionNumber));
                CandidateDescription<?> candidateDescription = candidates.get(identifier.getExistingOwnerId());
                ObjectReferenceType candidateOwnerRef = potentialOwner.getCandidateOwnerRef(); // also in candidateDescription
                if (candidateDescription != null && candidateOwnerRef != null) {
                    correlationOptions.add(
                            new CorrelationOptionDto.Candidate(candidateDescription, candidateOwnerRef, optionIdentifierRaw));
                    suggestionNumber++;
                } else {
                    LOGGER.warn("No candidate or potentialOwner content for {}? In:\n{}\n{}",
                            identifier.getExistingOwnerId(),
                            DebugUtil.debugDump(candidates, 1), potentialOwner.debugDump(1));
                }
            }
        }
    }

    private void createCorrelationPropertiesDefinitions(CaseType aCase, PageBase pageBase, Task task, OperationResult result)
            throws CommonException {
        correlationProperties.clear();
        correlationProperties.addAll(
                pageBase.getCorrelationService()
                        .describeCorrelationCase(aCase, null, task, result)
                        .getCorrelationProperties()
                        .values());
    }

    /** Accessed via {@link #F_CORRELATION_OPTIONS}. */
    @SuppressWarnings("unused")
    public List<CorrelationOptionDto> getCorrelationOptions() {
        return correlationOptions;
    }

    /** Accessed via {@link #F_OPTION_HEADERS}. */
    @SuppressWarnings("unused")
    public List<String> getOptionHeaders() {
        return optionHeaders;
    }

    /** Accessed via {@link #F_CORRELATION_PROPERTIES}. */
    @SuppressWarnings("unused")
    public List<CorrelationCaseDescription.CorrelationProperty> getCorrelationProperties() {
        return correlationProperties;
    }

    boolean hasConfidences() {
        return correlationOptions.stream().anyMatch(
                option -> option.getCandidateConfidence() != null);
    }
}
