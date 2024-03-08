/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.impl.mining.utils;

import static com.evolveum.midpoint.common.mining.objects.analysis.RoleAnalysisAttributePathResolver.getRoleSingleValuePaths;
import static com.evolveum.midpoint.common.mining.objects.analysis.RoleAnalysisAttributePathResolver.getUserSingleValuePaths;
import static com.evolveum.midpoint.common.mining.utils.RoleAnalysisUtils.*;
import static com.evolveum.midpoint.model.impl.mining.algorithm.cluster.action.OutliersDetectionUtil.executeOutliersAnalysis;
import static com.evolveum.midpoint.model.impl.mining.algorithm.cluster.mechanism.ClusterExplanation.getClusterExplanationDescription;
import static com.evolveum.midpoint.model.impl.mining.algorithm.cluster.mechanism.ClusterExplanation.resolveClusterName;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.xml.namespace.QName;

import com.evolveum.midpoint.common.mining.objects.analysis.AttributeAnalysisStructure;
import com.evolveum.midpoint.model.impl.mining.algorithm.detection.PatternConfidenceCalculator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.common.mining.objects.handler.RoleAnalysisProgressIncrement;
import com.evolveum.midpoint.common.mining.objects.statistic.ClusterStatistic;
import com.evolveum.midpoint.model.api.mining.RoleAnalysisService;
import com.evolveum.midpoint.model.impl.mining.algorithm.cluster.mechanism.Cluster;
import com.evolveum.midpoint.model.impl.mining.algorithm.cluster.mechanism.ClusterExplanation;
import com.evolveum.midpoint.model.impl.mining.algorithm.cluster.mechanism.DataPoint;
import com.evolveum.midpoint.model.impl.mining.algorithm.detection.DefaultPatternResolver;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

/**
 * The `RoleAnalysisAlgorithmUtils` class provides utility methods for processing and analyzing data clusters
 * and outliers in role analysis.
 * These utilities are used to generate statistics, prepare cluster objects, and detect patterns during role analysis.
 */
public class RoleAnalysisAlgorithmUtils {

    /**
     * Processes the clusters and generates cluster statistics, including the detection of patterns and outliers.
     * This method is used in role analysis to analyze clusters of data points.
     *
     * @param roleAnalysisService The role analysis service for performing role analysis operations.
     * @param dataPoints The data points representing cluster data.
     * @param clusters The clusters to process.
     * @param session The role analysis session.
     * @param handler A progress handler to report processing status.
     * @param task The current task.
     * @param result The operation result.
     * @return A list of PrismObjects representing the processed clusters.
     */
    @NotNull
    public List<PrismObject<RoleAnalysisClusterType>> processClusters(
            @NotNull RoleAnalysisService roleAnalysisService,
            @NotNull List<DataPoint> dataPoints,
            List<Cluster<DataPoint>> clusters,
            @NotNull RoleAnalysisSessionType session,
            @NotNull RoleAnalysisProgressIncrement handler,
            @NotNull Task task,
            @NotNull OperationResult result) {

        Integer sessionTypeObjectCount = roleAnalysisService.countSessionTypeObjects(task, result);

        RoleAnalysisOptionType analysisOption = session.getAnalysisOption();
        RoleAnalysisProcessModeType processMode = analysisOption.getProcessMode();

        QName complexType = processMode.equals(RoleAnalysisProcessModeType.ROLE)
                ? RoleType.COMPLEX_TYPE
                : UserType.COMPLEX_TYPE;

        int size = clusters.size();
        handler.enterNewStep("Generate Cluster Statistics model");
        handler.setOperationCountToProcess(size);
        List<PrismObject<RoleAnalysisClusterType>> clusterTypeObjectWithStatistic = IntStream.range(0, size)
                .mapToObj(i -> {
                    handler.iterateActualStatus();
                    return prepareClusters(roleAnalysisService, clusters.get(i), String.valueOf(i), dataPoints,
                            session, complexType, sessionTypeObjectCount,
                            task, result);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, Integer> nameOccurrences = new HashMap<>();

        double maxReduction = 0;
        for (PrismObject<RoleAnalysisClusterType> clusterPrismObject : clusterTypeObjectWithStatistic) {
            RoleAnalysisClusterType cluster = clusterPrismObject.asObjectable();
            String orig = cluster.getName().getOrig();
            int count = nameOccurrences.getOrDefault(orig, 0);
            if (count > 0) {
                cluster.setName(PolyStringType.fromOrig(orig + " (" + (count + 1) + ")"));
            }
            nameOccurrences.put(orig, count + 1);

            Double detectedReductionMetric = cluster.getClusterStatistics().getDetectedReductionMetric();
            if (detectedReductionMetric != null) {
                maxReduction = Math.max(maxReduction, detectedReductionMetric);
            }
        }

        boolean executeDetection = true;
        RoleAnalysisCategoryType analysisCategory = analysisOption.getAnalysisCategory();
        if (analysisCategory.equals(RoleAnalysisCategoryType.OUTLIERS)) {
            executeDetection = false;
        }

        for (PrismObject<RoleAnalysisClusterType> roleAnalysisClusterTypePrismObject : clusterTypeObjectWithStatistic) {
            RoleAnalysisClusterType cluster = roleAnalysisClusterTypePrismObject.asObjectable();
            processMetricAnalysis(cluster, session, maxReduction, executeDetection);
        }

        handler.enterNewStep("Prepare Outliers");
        handler.setOperationCountToProcess(dataPoints.size());
        PrismObject<RoleAnalysisClusterType> outlierCluster;
        if (!dataPoints.isEmpty()) {
            outlierCluster = prepareOutlierClusters(roleAnalysisService
                    , dataPoints, complexType, analysisOption, sessionTypeObjectCount, handler,
                    task, result);
            clusterTypeObjectWithStatistic.add(outlierCluster);

        }

        return clusterTypeObjectWithStatistic;
    }

    @NotNull
    public List<PrismObject<RoleAnalysisClusterType>> processExactMatch(
            @NotNull RoleAnalysisService roleAnalysisService,
            @NotNull List<DataPoint> dataPoints,
            @NotNull RoleAnalysisSessionType session,
            @NotNull RoleAnalysisProgressIncrement handler,
            @NotNull Task task,
            @NotNull OperationResult result) {

        Integer sessionTypeObjectCount = roleAnalysisService.countSessionTypeObjects(task, result);

        RoleAnalysisOptionType analysisOption = session.getAnalysisOption();
        QName processedObjectComplexType = analysisOption.getProcessMode().equals(RoleAnalysisProcessModeType.ROLE)
                ? RoleType.COMPLEX_TYPE
                : UserType.COMPLEX_TYPE;

        QName propertiesComplexType = processedObjectComplexType.equals(RoleType.COMPLEX_TYPE)
                ? UserType.COMPLEX_TYPE
                : RoleType.COMPLEX_TYPE;

        List<DataPoint> dataPointsOutliers = new ArrayList<>();
        int size = dataPoints.size();

        handler.enterNewStep("Generate Cluster Statistics model");
        handler.setOperationCountToProcess(size);
        List<PrismObject<RoleAnalysisClusterType>> clusterTypeObjectWithStatistic = IntStream.range(0, size)
                .mapToObj(i -> {
                    handler.iterateActualStatus();

                    return exactPrepareDataPoints(roleAnalysisService, dataPoints.get(i), String.valueOf(i), session,
                            dataPointsOutliers, processedObjectComplexType, propertiesComplexType, sessionTypeObjectCount,
                            task, result);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        handler.enterNewStep("Skip pattern detection step");

        handler.enterNewStep("Prepare Outliers");
        handler.setOperationCountToProcess(dataPoints.size());

        if (!dataPoints.isEmpty()) {
            PrismObject<RoleAnalysisClusterType> clusterTypePrismObject = prepareOutlierClusters(roleAnalysisService, dataPoints,
                    processedObjectComplexType, analysisOption, sessionTypeObjectCount, handler,
                    task, result);
            clusterTypeObjectWithStatistic.add(clusterTypePrismObject);
        }

        return clusterTypeObjectWithStatistic;
    }

    private ClusterStatistic statisticLoad(
            @NotNull RoleAnalysisService roleAnalysisService,
            @NotNull List<DataPoint> clusterDataPoints,
            @NotNull List<DataPoint> allDataPoints,
            @NotNull String clusterIndex,
            @NotNull QName complexType,
            @NotNull Integer sessionTypeObjectCount,
            @NotNull Task task,
            @NotNull OperationResult result) {

        PolyStringType name = PolyStringType.fromOrig(sessionTypeObjectCount + "_cluster_" + clusterIndex);

        int minVectorPoint = Integer.MAX_VALUE;
        int maxVectorPoint = -1;

        int totalAssignPropertiesRelation = 0;
        int totalMembersCount = 0;

        Set<String> membersOidsSet = new HashSet<>();
        Set<String> propertiesOidsSet = new HashSet<>();

        for (DataPoint clusterDataPoint : clusterDataPoints) {
            allDataPoints.remove(clusterDataPoint);
            Set<String> properties = clusterDataPoint.getProperties();
            Set<String> members = clusterDataPoint.getMembers();
            membersOidsSet.addAll(members);
            propertiesOidsSet.addAll(properties);

            int groupSize = members.size();
            totalMembersCount += groupSize;

            int occupyPointsCount = properties.size();
            totalAssignPropertiesRelation += (occupyPointsCount * groupSize);
            minVectorPoint = Math.min(minVectorPoint, occupyPointsCount);
            maxVectorPoint = Math.max(maxVectorPoint, occupyPointsCount);
        }

        int existingPropertiesInCluster = propertiesOidsSet.size();

        if (existingPropertiesInCluster == 0 || totalMembersCount == 0) {
            return null;
        }

        int allPossibleRelation = existingPropertiesInCluster * totalMembersCount;

        double meanPoints = (double) totalAssignPropertiesRelation / totalMembersCount;

        double density = Math.min((totalAssignPropertiesRelation / (double) allPossibleRelation) * 100, 100);

        Set<ObjectReferenceType> processedObjectsRef = roleAnalysisService
                .generateObjectReferences(membersOidsSet, complexType, task, result);

        ClusterStatistic clusterStatistic = new ClusterStatistic(name, processedObjectsRef, totalMembersCount,
                existingPropertiesInCluster, minVectorPoint, maxVectorPoint, meanPoints, density);

        extractAttributeStatistics(roleAnalysisService, complexType, task, result, density, propertiesOidsSet, membersOidsSet, clusterStatistic);

        return clusterStatistic;
    }

    private static void extractAttributeStatistics(@NotNull RoleAnalysisService roleAnalysisService,
            @NotNull QName complexType,
            @NotNull Task task,
            @NotNull OperationResult result,
            double density,
            Set<String> propertiesOidsSet,
            Set<String> membersOidsSet,
            ClusterStatistic clusterStatistic) {
        Set<PrismObject<UserType>> users;
        Set<PrismObject<RoleType>> roles;

        boolean isRoleMode = complexType.equals(RoleType.COMPLEX_TYPE);

        Double roleDensity = null;
        Double userDensity = null;
        if (isRoleMode) {
            roleDensity = density;
            users = propertiesOidsSet.stream().map(oid -> roleAnalysisService
                            .cacheUserTypeObject(new HashMap<>(), oid, task, result))
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            roles = membersOidsSet.stream().map(oid -> roleAnalysisService
                            .cacheRoleTypeObject(new HashMap<>(), oid, task, result))
                    .filter(Objects::nonNull).collect(Collectors.toSet());
        } else {
            userDensity = density;
            users = membersOidsSet.stream().map(oid -> roleAnalysisService
                            .cacheUserTypeObject(new HashMap<>(), oid, task, result))
                    .filter(Objects::nonNull).collect(Collectors.toSet());

            roles = propertiesOidsSet.stream().map(oid -> roleAnalysisService
                            .cacheRoleTypeObject(new HashMap<>(), oid, task, result))
                    .filter(Objects::nonNull).collect(Collectors.toSet());
        }

        List<ItemPath> userValuePaths = getUserSingleValuePaths();
        List<ItemPath> roleValuePaths = getRoleSingleValuePaths();

        List<AttributeAnalysisStructure> userAttributeAnalysisStructures = roleAnalysisService
                .userTypeAttributeAnalysis(users, userValuePaths, userDensity);
        List<AttributeAnalysisStructure> roleAttributeAnalysisStructures = roleAnalysisService
                .roleTypeAttributeAnalysis(roles, roleValuePaths, roleDensity);

        clusterStatistic.setUserAttributeAnalysisStructures(userAttributeAnalysisStructures);
        clusterStatistic.setRoleAttributeAnalysisStructures(roleAttributeAnalysisStructures);
    }

    private ClusterStatistic exactStatisticLoad(
            @NotNull RoleAnalysisService roleAnalysisService,
            @NotNull DataPoint clusterDataPoints,
            @NotNull String clusterIndex,
            int threshold,
            @NotNull List<DataPoint> dataPointsOutliers,
            @NotNull QName processedObjectComplexType,
            @NotNull QName propertiesComplexType,
            @NotNull Integer sessionTypeObjectCount,
            @NotNull Task task,
            @NotNull OperationResult result) {

        Set<String> elementsOids = new HashSet<>(clusterDataPoints.getMembers());
        Set<String> occupiedPoints = new HashSet<>(clusterDataPoints.getProperties());

        if (elementsOids.size() < threshold) {
            dataPointsOutliers.add(clusterDataPoints);
            return null;
        }

        PolyStringType name = PolyStringType.fromOrig(sessionTypeObjectCount + "_cluster_" + clusterIndex);

        Set<ObjectReferenceType> membersObjectsRef = roleAnalysisService.generateObjectReferences(elementsOids,
                processedObjectComplexType,
                task, result);

        Set<ObjectReferenceType> propertiesObjectRef = roleAnalysisService.generateObjectReferences(occupiedPoints,
                propertiesComplexType,
                task, result);

        double density = 100;

        int membersCount = membersObjectsRef.size();
        int propertiesCount = propertiesObjectRef.size();

        if (propertiesCount == 0 || membersCount == 0) {
            return null;
        }

        return new ClusterStatistic(name, propertiesObjectRef, membersObjectsRef, membersCount, propertiesCount,
                propertiesCount, propertiesCount, propertiesCount, density);
    }

    private PrismObject<RoleAnalysisClusterType> exactPrepareDataPoints(
            @NotNull RoleAnalysisService roleAnalysisService,
            @NotNull DataPoint dataPointCluster,
            @NotNull String clusterIndex,
            @NotNull RoleAnalysisSessionType session,
            @NotNull List<DataPoint> dataPointsOutliers,
            @NotNull QName processedObjectComplexType,
            @NotNull QName propertiesComplexType,
            @NotNull Integer sessionTypeObjectCount,
            @NotNull Task task,
            @NotNull OperationResult result) {

        AbstractAnalysisSessionOptionType sessionOptionType = getSessionOptionType(session);
        int minMembersCount = sessionOptionType.getMinMembersCount();
        ClusterStatistic clusterStatistic = exactStatisticLoad(roleAnalysisService, dataPointCluster, clusterIndex, minMembersCount,
                dataPointsOutliers, processedObjectComplexType, propertiesComplexType, sessionTypeObjectCount, task, result);

        if (clusterStatistic != null) {
            RoleAnalysisOptionType analysisOption = session.getAnalysisOption();
            AnalysisClusterStatisticType roleAnalysisClusterStatisticType = createClusterStatisticType(clusterStatistic,
                    analysisOption.getProcessMode());

            boolean executeDetection = true;
            RoleAnalysisCategoryType analysisCategory = analysisOption.getAnalysisCategory();
            if (analysisCategory.equals(RoleAnalysisCategoryType.OUTLIERS)) {
                executeDetection = false;
            }
            return generateClusterObject(roleAnalysisService, null, null, clusterStatistic, session,
                    roleAnalysisClusterStatisticType, session.getAnalysisOption(), executeDetection,
                    task, result);
        } else {return null;}
    }

    private PrismObject<RoleAnalysisClusterType> prepareClusters(
            @NotNull RoleAnalysisService roleAnalysisService,
            Cluster<DataPoint> cluster,
            @NotNull String clusterIndex,
            @NotNull List<DataPoint> dataPoints,
            @NotNull RoleAnalysisSessionType session,
            @NotNull QName complexType,
            @NotNull Integer sessionTypeObjectCount,
            @NotNull Task task,
            @NotNull OperationResult result) {

        List<DataPoint> dataPointCluster = cluster.getPoints();
        Set<ClusterExplanation> explanations = cluster.getExplanations();
        String clusterExplanationDescription = getClusterExplanationDescription(explanations);
        String candidateName = resolveClusterName(explanations);

        Set<String> elementsOids = new HashSet<>();
        for (DataPoint clusterDataPoint : dataPointCluster) {
            Set<String> elements = clusterDataPoint.getMembers();
            elementsOids.addAll(elements);
        }

        AbstractAnalysisSessionOptionType sessionOptionType = getSessionOptionType(session);

        if (elementsOids.size() < sessionOptionType.getMinMembersCount()) {
            return null;
        }

        ClusterStatistic clusterStatistic = statisticLoad(roleAnalysisService, dataPointCluster, dataPoints, clusterIndex,
                complexType, sessionTypeObjectCount, task, result);

        assert clusterStatistic != null;
        RoleAnalysisOptionType analysisOption = session.getAnalysisOption();
        AnalysisClusterStatisticType roleAnalysisClusterStatisticType = createClusterStatisticType(clusterStatistic,
                analysisOption.getProcessMode());

        boolean detect = isDetectable(session, clusterStatistic);
        return generateClusterObject(roleAnalysisService,
                clusterExplanationDescription,
                candidateName,
                clusterStatistic,
                session,
                roleAnalysisClusterStatisticType,
                analysisOption,
                detect,
                task,
                result);

    }

    private static boolean isDetectable(@NotNull RoleAnalysisSessionType session, ClusterStatistic clusterStatistic) {
        boolean detect = true;
        RoleAnalysisDetectionProcessType detectMode = session.getDefaultDetectionOption().getDetectionProcessMode();

        RoleAnalysisCategoryType analysisCategory = session.getAnalysisOption().getAnalysisCategory();
        if (analysisCategory.equals(RoleAnalysisCategoryType.OUTLIERS)) {
            return false;
        }
        if (detectMode == null) {
            detectMode = RoleAnalysisDetectionProcessType.FULL;
        }

        if (detectMode.equals(RoleAnalysisDetectionProcessType.PARTIAL)) {
            if (clusterStatistic.getPropertiesCount() > 300 || clusterStatistic.getMembersCount() > 300) {
                detect = false;
            }
        } else if (detectMode.equals(RoleAnalysisDetectionProcessType.SKIP)) {
            detect = false;
        }
        return detect;
    }

    private PrismObject<RoleAnalysisClusterType> prepareOutlierClusters(
            @NotNull RoleAnalysisService roleAnalysisService,
            @NotNull List<DataPoint> dataPoints,
            @NotNull QName complexType,
            @NotNull RoleAnalysisOptionType analysisOption,
            @NotNull Integer sessionTypeObjectCount,
            @NotNull RoleAnalysisProgressIncrement handler,
            @NotNull Task task,
            @NotNull OperationResult result) {

        int minVectorPoint = Integer.MAX_VALUE;
        int maxVectorPoint = -1;

        int totalDataPoints = dataPoints.size();
        int sumPoints = 0;

        Set<String> elementsOid = new HashSet<>();
        Set<String> pointsSet = new HashSet<>();
        for (DataPoint dataPoint : dataPoints) {
            handler.iterateActualStatus();

            Set<String> points = dataPoint.getProperties();
            pointsSet.addAll(points);
            elementsOid.addAll(dataPoint.getMembers());

            int pointsSize = points.size();
            sumPoints += pointsSize;
            minVectorPoint = Math.min(minVectorPoint, pointsSize);
            maxVectorPoint = Math.max(maxVectorPoint, pointsSize);
        }

        double meanPoints = (double) sumPoints / totalDataPoints;

        int pointsSize = pointsSet.size();
        int elementSize = elementsOid.size();
        double density = (sumPoints / (double) (elementSize * pointsSize)) * 100;

        PolyStringType name = PolyStringType.fromOrig(sessionTypeObjectCount + "_outliers");

        Set<ObjectReferenceType> processedObjectsRef = new HashSet<>();
        ObjectReferenceType objectReferenceType;
        for (String element : elementsOid) {
            objectReferenceType = new ObjectReferenceType();
            objectReferenceType.setType(complexType);
            objectReferenceType.setOid(element);
            processedObjectsRef.add(objectReferenceType);
        }

        ClusterStatistic clusterStatistic = new ClusterStatistic(name, processedObjectsRef, elementSize,
                pointsSize, minVectorPoint, maxVectorPoint, meanPoints, density);

        AnalysisClusterStatisticType roleAnalysisClusterStatisticType = createClusterStatisticType(clusterStatistic,
                analysisOption.getProcessMode());

        PrismObject<RoleAnalysisClusterType> clusterObject = generateClusterObject(roleAnalysisService,
                null, null, clusterStatistic,
                null,
                roleAnalysisClusterStatisticType,
                analysisOption,
                false,
                task,
                result
        );
        clusterObject.asObjectable().setCategory(RoleAnalysisClusterCategory.OUTLIERS);
        return clusterObject;
    }

    private @NotNull PrismObject<RoleAnalysisClusterType> generateClusterObject(
            @NotNull RoleAnalysisService roleAnalysisService,
            @Nullable String clusterExplanationDescription,
            @Nullable String candidateName,
            @NotNull ClusterStatistic clusterStatistic,
            @Nullable RoleAnalysisSessionType session,
            @NotNull AnalysisClusterStatisticType roleAnalysisClusterStatisticType,
            @NotNull RoleAnalysisOptionType analysisOption,
            boolean detectPattern,
            @NotNull Task task,
            @NotNull OperationResult result) {

        PrismObject<RoleAnalysisClusterType> clusterTypePrismObject = prepareClusterPrismObject();
        assert clusterTypePrismObject != null;

        Set<ObjectReferenceType> members = clusterStatistic.getMembersRef();

        RoleAnalysisClusterType cluster = clusterTypePrismObject.asObjectable();
        cluster.setOid(String.valueOf(UUID.randomUUID()));
        cluster.setCategory(RoleAnalysisClusterCategory.INLIERS);

        cluster.getMember().addAll(members);
        cluster.setName(candidateName != null ? PolyStringType.fromOrig(candidateName) : clusterStatistic.getName());

        if (clusterExplanationDescription != null) {
            cluster.setDescription(clusterExplanationDescription);
        }

        double maxReduction = 0;
        List<RoleAnalysisDetectionPatternType> detectedPatterns = processPatternAnalysis(
                roleAnalysisService, clusterStatistic, cluster, analysisOption, session, detectPattern, task, result);

        if (detectedPatterns != null) {
            cluster.getDetectedPattern().addAll(detectedPatterns);
            maxReduction = calculateMaxReduction(detectedPatterns);
        }

        roleAnalysisClusterStatisticType.setDetectedReductionMetric(maxReduction);

        resolveAttributeStatistics(clusterStatistic, roleAnalysisClusterStatisticType);

        cluster.setClusterStatistics(roleAnalysisClusterStatisticType);

        processOutliersAnalysis(roleAnalysisService, cluster, session, analysisOption, task, result);

        return clusterTypePrismObject;
    }

    private static void resolveAttributeStatistics(@NotNull ClusterStatistic clusterStatistic,
            @NotNull AnalysisClusterStatisticType roleAnalysisClusterStatisticType) {
        List<AttributeAnalysisStructure> roleAttributeAnalysisStructures = clusterStatistic.getRoleAttributeAnalysisStructures();
        List<AttributeAnalysisStructure> userAttributeAnalysisStructures = clusterStatistic.getUserAttributeAnalysisStructures();
        if (roleAttributeAnalysisStructures != null && !roleAttributeAnalysisStructures.isEmpty()) {
            RoleAnalysisAttributeAnalysisResult roleAnalysis = new RoleAnalysisAttributeAnalysisResult();
            for (AttributeAnalysisStructure roleAttributeAnalysisStructure : roleAttributeAnalysisStructures) {
                double density = roleAttributeAnalysisStructure.getDensity();
                if (density == 0) {
                    continue;
                }
                RoleAnalysisAttributeAnalysis roleAnalysisAttributeAnalysis = new RoleAnalysisAttributeAnalysis();
                roleAnalysisAttributeAnalysis.setDensity(density);
                roleAnalysisAttributeAnalysis.setItemPath(roleAttributeAnalysisStructure.getItemPath());
                roleAnalysisAttributeAnalysis.setDescription(roleAttributeAnalysisStructure.getDescription());
                roleAnalysisAttributeAnalysis.setJsonDescription(roleAttributeAnalysisStructure.getJsonDescription());
                roleAnalysis.getAttributeAnalysis().add(roleAnalysisAttributeAnalysis);
            }
            roleAnalysisClusterStatisticType.setRoleAttributeAnalysisResult(roleAnalysis);
        }

        if (userAttributeAnalysisStructures != null && !userAttributeAnalysisStructures.isEmpty()) {
            RoleAnalysisAttributeAnalysisResult userAnalysis = new RoleAnalysisAttributeAnalysisResult();
            for (AttributeAnalysisStructure userAttributeAnalysisStructure : userAttributeAnalysisStructures) {
                double density = userAttributeAnalysisStructure.getDensity();
                if (density == 0) {
                    continue;
                }
                RoleAnalysisAttributeAnalysis userAnalysisAttributeAnalysis = new RoleAnalysisAttributeAnalysis();
                userAnalysisAttributeAnalysis.setDensity(density);
                userAnalysisAttributeAnalysis.setItemPath(userAttributeAnalysisStructure.getItemPath());
                userAnalysisAttributeAnalysis.setDescription(userAttributeAnalysisStructure.getDescription());
                userAnalysisAttributeAnalysis.setJsonDescription(userAttributeAnalysisStructure.getJsonDescription());
                userAnalysis.getAttributeAnalysis().add(userAnalysisAttributeAnalysis);
            }
            roleAnalysisClusterStatisticType.setUserAttributeAnalysisResult(userAnalysis);
        }
    }

    private void processOutliersAnalysis(
            @NotNull RoleAnalysisService roleAnalysisService,
            @NotNull RoleAnalysisClusterType cluster,
            @Nullable RoleAnalysisSessionType session,
            @NotNull RoleAnalysisOptionType analysisOption,
            @NotNull Task task,
            @NotNull OperationResult result) {

        if (session != null && analysisOption.getAnalysisCategory().equals(RoleAnalysisCategoryType.OUTLIERS)) {
            RoleAnalysisDetectionOptionType detectionOption = session.getDefaultDetectionOption();
            Double min = detectionOption.getFrequencyRange().getMin();
            if (min == null) {
                detectionOption.getFrequencyRange().setMin(0.01);
            }
            Collection<RoleAnalysisOutlierType> roleAnalysisOutlierTypes = executeOutliersAnalysis(
                    roleAnalysisService, cluster, session, analysisOption, min, task, result);

            for (RoleAnalysisOutlierType roleAnalysisOutlierType : roleAnalysisOutlierTypes) {
                roleAnalysisOutlierType.setTargetClusterRef(new ObjectReferenceType()
                        .oid(session.getOid())
                        .type(RoleAnalysisSessionType.COMPLEX_TYPE));

                ObjectReferenceType targetObjectRef = roleAnalysisOutlierType.getTargetObjectRef();
                PrismObject<FocusType> object = roleAnalysisService
                        .getObject(FocusType.class, targetObjectRef.getOid(), task, result);

                roleAnalysisOutlierType.setName(object != null && object.getName() != null
                        ? PolyStringType.fromOrig(object.getName() + " (outlier)")
                        : PolyStringType.fromOrig("outlier_" + session.getName() + "_" + UUID.randomUUID()));

                roleAnalysisService.resolveOutliers(roleAnalysisOutlierType, task, result, session.getOid());
            }
        }
    }

    private List<RoleAnalysisDetectionPatternType> processPatternAnalysis(
            @NotNull RoleAnalysisService roleAnalysisService,
            @NotNull ClusterStatistic clusterStatistic,
            @NotNull RoleAnalysisClusterType cluster,
            @NotNull RoleAnalysisOptionType analysisOption,
            @Nullable RoleAnalysisSessionType session,
            boolean detectPattern,
            @NotNull Task task,
            @NotNull OperationResult result) {

        if (session == null || !detectPattern) {
            return null;
        }

        RoleAnalysisProcessModeType mode = analysisOption.getProcessMode();
        DefaultPatternResolver defaultPatternResolver = new DefaultPatternResolver(roleAnalysisService, mode);
        List<RoleAnalysisDetectionPatternType> detectedPatterns = defaultPatternResolver
                .loadPattern(session, clusterStatistic, cluster, result, task);

        Map<String, PrismObject<UserType>> userExistCache = new HashMap<>();
        Map<String, PrismObject<RoleType>> roleExistCache = new HashMap<>();

        List<ItemPath> userValuePaths = getUserSingleValuePaths();
        List<ItemPath> roleValuePaths = getRoleSingleValuePaths();

        roleAnalysisService.processAttributeAnalysis(detectedPatterns, userExistCache, roleExistCache,
                userValuePaths, roleValuePaths, task, result);

        return detectedPatterns;
    }

    private double calculateMaxReduction(@NotNull List<RoleAnalysisDetectionPatternType> detectedPatterns) {
        double maxReduction = 0;
        for (RoleAnalysisDetectionPatternType detectedPattern : detectedPatterns) {
            Double clusterMetric = detectedPattern.getClusterMetric();
            if (clusterMetric != null) {
                maxReduction = Math.max(maxReduction, clusterMetric);
            }
        }
        return maxReduction;

    }

    private void processMetricAnalysis(
            @NotNull RoleAnalysisClusterType cluster,
            @Nullable RoleAnalysisSessionType session,
            double maxReduction,
            boolean detectPattern) {

        if (session == null || !detectPattern) {
            return;
        }

        List<RoleAnalysisDetectionPatternType> detectedPatterns = cluster.getDetectedPattern();

        for (RoleAnalysisDetectionPatternType detectedPattern : detectedPatterns) {
            PatternConfidenceCalculator patternConfidenceCalculator = new PatternConfidenceCalculator(detectedPattern, maxReduction);
            double itemConfidence = patternConfidenceCalculator.calculateItemConfidence();
            double reductionFactorConfidence = patternConfidenceCalculator.calculateReductionFactorConfidence();
            detectedPattern.setItemConfidence(itemConfidence);
            detectedPattern.setReductionConfidence(reductionFactorConfidence);
        }
    }

}
