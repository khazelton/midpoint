/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.mining.utils;

import static java.util.Collections.singleton;

import static com.evolveum.midpoint.common.mining.utils.RoleAnalysisUtils.getCurrentXMLGregorianCalendar;
import static com.evolveum.midpoint.common.mining.utils.RoleAnalysisUtils.loadIntersections;
import static com.evolveum.midpoint.model.common.expression.functions.BasicExpressionFunctions.LOGGER;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.MetadataType.F_MODIFY_TIMESTAMP;

import java.util.*;
import javax.xml.namespace.QName;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.common.mining.objects.detection.DetectedPattern;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

public class RoleAnalysisObjectUtils {

    public static PrismObject<UserType> getUserTypeObject(@NotNull ModelService modelService, String oid,
            OperationResult result, Task task) {

        try {
            return modelService.getObject(UserType.class, oid, null, task, result);
        } catch (Exception ex) {
            LoggingUtils.logExceptionOnDebugLevel(LOGGER, "Couldn't get UserType object, Probably not set yet", ex);
        } finally {
            result.recomputeStatus();
        }
        return null;
    }

    public static PrismObject<FocusType> getFocusTypeObject(@NotNull ModelService modelService, String oid,
            OperationResult result, Task task) {

        try {
            return modelService.getObject(FocusType.class, oid, null, task, result);
        } catch (Exception ex) {
            LoggingUtils.logExceptionOnDebugLevel(LOGGER, "Couldn't get FocusType object, Probably not set yet", ex);
        } finally {
            result.recomputeStatus();
        }
        return null;
    }

    public static PrismObject<RoleType> getRoleTypeObject(@NotNull ModelService modelService, String oid,
            OperationResult result, Task task) {

        try {
            return modelService.getObject(RoleType.class, oid, null, task, result);
        } catch (Exception ex) {
            LoggingUtils.logExceptionOnDebugLevel(LOGGER, "Couldn't get RoleType object, Probably not set yet", ex);
        } finally {
            result.recomputeStatus();
        }
        return null;
    }

    public static PrismObject<RoleAnalysisClusterType> getClusterTypeObject(@NotNull ModelService modelService, String oid,
            OperationResult result, Task task) {

        try {
            return modelService.getObject(RoleAnalysisClusterType.class, oid, null, task, result);
        } catch (Exception ex) {
            LoggingUtils.logExceptionOnDebugLevel(LOGGER, "Couldn't get RoleAnalysisClusterType object, Probably not set yet", ex);
        } finally {
            result.recomputeStatus();
        }
        return null;
    }

    public static PrismObject<RoleAnalysisSessionType> getSessionTypeObject(@NotNull ModelService modelService,
            OperationResult result, String oid, Task task) {

        try {
            return modelService.getObject(RoleAnalysisSessionType.class, oid, null, task, result);
        } catch (Exception ex) {
            LoggingUtils.logExceptionOnDebugLevel(LOGGER, "Couldn't get RoleAnalysisSessionType object, Probably not set yet", ex);
        } finally {
            result.recomputeStatus();
        }
        return null;
    }

    public static List<PrismObject<UserType>> extractRoleMembers(ObjectFilter userFilter, OperationResult result,
            ModelService modelService, String objectId, Task task) {

        ObjectQuery query = PrismContext.get().queryFor(UserType.class)
                .exists(AssignmentHolderType.F_ASSIGNMENT)
                .block()
                .item(AssignmentType.F_TARGET_REF)
                .ref(objectId)
                .endBlock().build();

        if (userFilter != null) {
            query.addFilter(userFilter);
        }

        try {
            return modelService.searchObjects(UserType.class, query, null, task, result);
        } catch (Exception ex) {
            LoggingUtils.logExceptionOnDebugLevel(LOGGER, "Failed to search role member objects:", ex);
        } finally {
            result.recomputeStatus();
        }

        return null;
    }

    public static void importRoleAnalysisClusterObject(OperationResult result, Task task, @NotNull ModelService modelService,
            @NotNull PrismObject<RoleAnalysisClusterType> cluster, ObjectReferenceType parentRef,
            RoleAnalysisDetectionOptionType roleAnalysisSessionDetectionOption) {
        cluster.asObjectable().setRoleAnalysisSessionRef(parentRef);
        cluster.asObjectable().setDetectionOption(roleAnalysisSessionDetectionOption);
        modelService.importObject(cluster, null, task, result);
    }

    public static void modifySessionAfterClustering(ObjectReferenceType sessionRef,
            RoleAnalysisSessionStatisticType sessionStatistic,
            ModelService modelService, OperationResult result, Task task) {

        try {

            ObjectDelta<RoleAnalysisSessionType> delta = PrismContext.get().deltaFor(RoleAnalysisSessionType.class)
                    .item(RoleAnalysisSessionType.F_SESSION_STATISTIC)
                    .replace(sessionStatistic)
                    .asObjectDelta(sessionRef.getOid());

            modelService.executeChanges(singleton(delta), null, task, result);

        } catch (SchemaException | ObjectAlreadyExistsException | ObjectNotFoundException | ExpressionEvaluationException |
                CommunicationException | ConfigurationException | PolicyViolationException | SecurityViolationException e) {
            LOGGER.error("Couldn't modify  RoleAnalysisSessionType {}", sessionRef, e);
        }

    }

    public static void replaceRoleAnalysisClusterDetectionPattern(String clusterOid,
            ModelService modelService, OperationResult result, List<DetectedPattern> detectedPatterns, Task task) {

        List<RoleAnalysisDetectionPatternType> roleAnalysisClusterDetectionTypes = loadIntersections(detectedPatterns);

        double max = 0;
        Collection<PrismContainerValue<?>> collection = new ArrayList<>();
        for (RoleAnalysisDetectionPatternType clusterDetectionType : roleAnalysisClusterDetectionTypes) {
            collection.add(clusterDetectionType.asPrismContainerValue());
            max = Math.max(max, clusterDetectionType.getClusterMetric());
        }

        PrismObject<RoleAnalysisClusterType> clusterTypeObject = getClusterTypeObject(modelService, clusterOid, result, task);

        if (clusterTypeObject == null) {
            return;
        }
        AnalysisClusterStatisticType clusterStatistics = clusterTypeObject.asObjectable().getClusterStatistics();

        AnalysisClusterStatisticType analysisClusterStatisticType = new AnalysisClusterStatisticType();
        analysisClusterStatisticType.setDetectedReductionMetric(max);
        analysisClusterStatisticType.setMembershipDensity(clusterStatistics.getMembershipDensity());
        analysisClusterStatisticType.setRolesCount(clusterStatistics.getRolesCount());
        analysisClusterStatisticType.setUsersCount(clusterStatistics.getUsersCount());
        analysisClusterStatisticType.setMembershipMean(clusterStatistics.getMembershipMean());
        analysisClusterStatisticType.setMembershipRange(clusterStatistics.getMembershipRange());

        try {

            ObjectDelta<RoleAnalysisClusterType> delta = PrismContext.get().deltaFor(RoleAnalysisClusterType.class)
                    .item(RoleAnalysisClusterType.F_DETECTED_PATTERN).replace(collection)
                    .item(RoleAnalysisClusterType.F_METADATA, F_MODIFY_TIMESTAMP).replace(getCurrentXMLGregorianCalendar())
                    .item(RoleAnalysisClusterType.F_CLUSTER_STATISTICS).replace(analysisClusterStatisticType.asPrismContainerValue())
                    .asObjectDelta(clusterOid);

            modelService.executeChanges(singleton(delta), null, task, result);

        } catch (SchemaException | ObjectAlreadyExistsException | ObjectNotFoundException | ExpressionEvaluationException |
                CommunicationException | ConfigurationException | PolicyViolationException | SecurityViolationException e) {
            LOGGER.error("Couldn't modify RoleAnalysisClusterType {}", clusterOid, e);
        }

    }

    public static @NotNull Set<ObjectReferenceType> createObjectReferences(Set<String> objects, QName complexType,
            ModelService modelService, Task task, OperationResult operationResult) {

        Set<ObjectReferenceType> objectReferenceList = new HashSet<>();
        for (String item : objects) {

            PrismObject<FocusType> object = getFocusTypeObject(modelService, item, operationResult, task);
            ObjectReferenceType objectReferenceType = new ObjectReferenceType();
            objectReferenceType.setType(complexType);
            objectReferenceType.setOid(item);
            if (object != null) {
                objectReferenceType.setTargetName(PolyStringType.fromOrig(object.getName().toString()));
            }
            objectReferenceList.add(objectReferenceType);

        }
        return objectReferenceList;
    }
}
