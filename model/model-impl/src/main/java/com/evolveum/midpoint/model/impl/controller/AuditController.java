/*
 * Copyright (C) 2016-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.model.impl.controller;

import java.util.*;
import javax.xml.datatype.XMLGregorianCalendar;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.audit.api.AuditEventRecord;
import com.evolveum.midpoint.audit.api.AuditEventStage;
import com.evolveum.midpoint.audit.api.AuditService;
import com.evolveum.midpoint.model.api.ModelAuditService;
import com.evolveum.midpoint.model.api.ModelAuthorizationAction;
import com.evolveum.midpoint.model.impl.ModelObjectResolver;
import com.evolveum.midpoint.model.impl.util.AuditHelper;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.xml.XmlTypeConverter;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.ObjectDeltaOperation;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.security.enforcer.api.AuthorizationParameters;
import com.evolveum.midpoint.security.enforcer.api.SecurityEnforcer;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.audit_3.AuditEventRecordType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AuthorizationPhaseType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CleanupPolicyType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

/**
 * @author semancik
 */
@Component
public class AuditController implements ModelAuditService {

    private static final Trace LOGGER = TraceManager.getTrace(AuditController.class);

    @Autowired private AuditService auditService;
    @Autowired private AuditHelper auditHelper;
    @Autowired private ModelObjectResolver objectResolver;
    @Autowired private SecurityEnforcer securityEnforcer;

    @Override
    public void audit(AuditEventRecord record, Task task, OperationResult result) throws SecurityViolationException, SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException {
        authorize(ModelAuthorizationAction.AUDIT_RECORD, task, result);
        auditHelper.audit(record, null, task, result);
    }

    public @NotNull SearchResultList<AuditEventRecordType> searchObjects(
            @Nullable ObjectQuery query,
            @Nullable Collection<SelectorOptions<GetOperationOptions>> options,
            @NotNull Task task,
            @NotNull OperationResult parentResult)
            throws SecurityViolationException, SchemaException, ObjectNotFoundException,
            ExpressionEvaluationException, CommunicationException, ConfigurationException {
        authorize(ModelAuthorizationAction.AUDIT_READ, task, parentResult);
        return auditService.searchObjects(query, options, parentResult);
    }

    @Override
    public long countObjects(
            @Nullable ObjectQuery query,
            @Nullable Collection<SelectorOptions<GetOperationOptions>> options,
            Task task,
            @NotNull OperationResult parentResult)
            throws SchemaException, ExpressionEvaluationException, SecurityViolationException,
            CommunicationException, ConfigurationException, ObjectNotFoundException {
        authorize(ModelAuthorizationAction.AUDIT_READ, task, parentResult);
        return auditService.countObjects(query, options, parentResult);
    }

    @Override
    public void cleanupAudit(CleanupPolicyType policy, Task task, OperationResult parentResult) throws SecurityViolationException, SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException {
        authorize(ModelAuthorizationAction.AUDIT_MANAGE, task, parentResult);
        auditService.cleanupAudit(policy, parentResult);
    }

    @Override
    public boolean supportsRetrieval() {
        return auditService.supportsRetrieval();
    }

    @Override
    public <O extends ObjectType> PrismObject<O> reconstructObject(Class<O> type, String oid, String eventIdentifier, Task task, OperationResult result) throws ObjectNotFoundException, SchemaException {

        // TODO: authorizations

        O currentObjectType = objectResolver.getObjectSimple(type, oid, null, task, result);
        PrismObject<O> currentObject = (PrismObject<O>) currentObjectType.asPrismObject();

        List<AuditEventRecord> changeTrail = getChangeTrail(oid, eventIdentifier, result);
        LOGGER.trace("Found change trail for {} containing {} events", oid, changeTrail.size());

        LOGGER.debug("TRAIL:\n{}", DebugUtil.debugDumpLazily(changeTrail, 1));

        PrismObject<O> objectFromLastEvent = getObjectFromLastEvent(currentObject, changeTrail, eventIdentifier);
        if (objectFromLastEvent != null) {
            return objectFromLastEvent;
        }

        return rollBackTime(currentObject.clone(), changeTrail);
    }

    private List<AuditEventRecord> getChangeTrail(String targetOid, String finalEventIdentifier, OperationResult result) throws ObjectNotFoundException {
        AuditEventRecord finalEvent = findEvent(finalEventIdentifier, result);
        if (finalEvent == null) {
            throw new ObjectNotFoundException("Audit event ID " + finalEventIdentifier + " was not found");
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Final event:\n{}", finalEvent.debugDump(1));
        }
        List<AuditEventRecord> changeTrail = getChangeTrail(targetOid, XmlTypeConverter.createXMLGregorianCalendar(finalEvent.getTimestamp()), result);

        // The search may have returned more events that we want to, e.g. if two
        // events happened in the same millisecond.
        Iterator<AuditEventRecord> iterator = changeTrail.iterator();
        boolean foundFinalEvent = false;
        while (iterator.hasNext()) {
            AuditEventRecord event = iterator.next();
            if (foundFinalEvent) {
                iterator.remove();
            } else if (finalEventIdentifier.equals(event.getEventIdentifier())) {
                foundFinalEvent = true;
            }
        }

        return changeTrail;
    }

    private List<AuditEventRecord> getChangeTrail(String targetOid, XMLGregorianCalendar from, OperationResult result) {
        Map<String, Object> params = new HashMap<>();
        params.put("from", from);
        params.put("targetOid", targetOid);
        params.put("stage", AuditEventStage.EXECUTION);
        return auditService.listRecords(
                "select * from m_audit_event as aer where (aer.timestampValue >= :from) and (aer.targetOid = :targetOid) and (aer.eventStage = :stage) order by aer.timestampValue desc",
                params, result);
    }

    private AuditEventRecord findEvent(String eventIdentifier, OperationResult result) {
        Map<String, Object> params = new HashMap<>();
        params.put("eventIdentifier", eventIdentifier);
        List<AuditEventRecord> listRecords = auditService
                .listRecords("select * from m_audit_event as aer where (aer.eventIdentifier = :eventIdentifier)", params, result);
        if (listRecords == null || listRecords.isEmpty()) {
            return null;
        }
        if (listRecords.size() > 1) {
            LOGGER.error("Found " + listRecords.size() + " audit records for event ID " + eventIdentifier + " (expecting just one)");
        }
        return listRecords.get(0);
    }

    private <O extends ObjectType> PrismObject<O> getObjectFromLastEvent(PrismObject<O> object, List<AuditEventRecord> changeTrail, String eventIdentifier) {
        if (changeTrail.isEmpty()) {
            return object;
        }
        AuditEventRecord lastEvent = changeTrail.remove(changeTrail.size() - 1);
        if (!eventIdentifier.equals(lastEvent.getEventIdentifier())) {
            throw new IllegalStateException("Wrong last event identifier, expected " + eventIdentifier + " but was " + lastEvent.getEventIdentifier());
        }
        Collection<ObjectDeltaOperation<? extends ObjectType>> lastEventDeltasOperations = lastEvent.getDeltas();
        for (ObjectDeltaOperation<? extends ObjectType> lastEventDeltasOperation : lastEventDeltasOperations) {
            ObjectDelta<O> objectDelta = (ObjectDelta<O>) lastEventDeltasOperation.getObjectDelta();
            if (!isApplicable(lastEventDeltasOperation, object, lastEvent)) {
                continue;
            }
            if (objectDelta.isAdd()) {
                // We are lucky. This is object add, so we have complete object there. No need to roll back
                // the operations.
                PrismObject<O> objectToAdd = objectDelta.getObjectToAdd();
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Taking object from add delta in last event {}:\n{}", lastEvent.getEventIdentifier(), objectToAdd.debugDump(1));
                }
                return objectToAdd;
            }
        }
        return null;
    }

    private <O extends ObjectType> PrismObject<O> rollBackTime(PrismObject<O> object, List<AuditEventRecord> changeTrail) throws SchemaException {
        for (AuditEventRecord event : changeTrail) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Applying event {} ({})", event.getEventIdentifier(), XmlTypeConverter.createXMLGregorianCalendar(event.getTimestamp()));
            }
            Collection<ObjectDeltaOperation<? extends ObjectType>> deltaOperations = event.getDeltas();
            for (ObjectDeltaOperation<? extends ObjectType> deltaOperation : deltaOperations) {
                ObjectDelta<O> objectDelta = (ObjectDelta<O>) deltaOperation.getObjectDelta();
                if (!isApplicable(deltaOperation, object, event)) {
                    continue;
                }
                if (objectDelta.isDelete()) {
                    throw new SchemaException("Delete delta found in the audit trail. Object history cannot be reconstructed.");
                }
                if (objectDelta.isAdd()) {
                    throw new SchemaException("Add delta found in the audit trail. Object history cannot be reconstructed.");
                }
                ObjectDelta<O> reverseDelta = objectDelta.createReverseDelta();
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Applying delta (reverse):\n{}", reverseDelta.debugDump(1));
                }
                reverseDelta.applyTo(object);
            }
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Object after application of event {} ({}):\n{}", event.getEventIdentifier(),
                        XmlTypeConverter.createXMLGregorianCalendar(event.getTimestamp()), object.debugDump(1));
            }
        }
        return object;
    }

    private <O extends ObjectType> boolean isApplicable(ObjectDeltaOperation<? extends ObjectType> lastEventDeltasOperation,
            PrismObject<O> object, AuditEventRecord lastEvent) {
        OperationResult executionResult = lastEventDeltasOperation.getExecutionResult();
        ObjectDelta<O> objectDelta = (ObjectDelta<O>) lastEventDeltasOperation.getObjectDelta();
        if (executionResult != null && executionResult.getStatus() == OperationResultStatus.FATAL_ERROR) {
            LOGGER.trace("Skipping delta {} in event {} because it is {}", objectDelta, lastEvent.getEventIdentifier(),
                    executionResult.getStatus());
            return false;
        }
        if (!object.getOid().equals(objectDelta.getOid())) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Skipping delta {} in event {} because OID does not match ({} vs {})", objectDelta, lastEvent.getEventIdentifier(),
                        object.getOid(), objectDelta.getOid());
            }
            return false;
        }
        return true;
    }

    private void authorize(ModelAuthorizationAction action, Task task, OperationResult result) throws SecurityViolationException, SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException {
        securityEnforcer.authorize(action.getUrl(), AuthorizationPhaseType.REQUEST, AuthorizationParameters.EMPTY, null, task, result);
        securityEnforcer.authorize(action.getUrl(), AuthorizationPhaseType.EXECUTION, AuthorizationParameters.EMPTY, null, task, result);
    }

}
