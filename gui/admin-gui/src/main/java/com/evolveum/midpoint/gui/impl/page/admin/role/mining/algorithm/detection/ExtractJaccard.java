/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.role.mining.algorithm.detection;

import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.algorithm.utils.ExtractPatternUtils.addDetectedObjectJaccard;

import java.util.*;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import com.evolveum.midpoint.gui.impl.page.admin.role.mining.algorithm.object.DetectionOption;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.objects.MiningRoleTypeChunk;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.objects.MiningUserTypeChunk;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.RoleUtils;

public class ExtractJaccard implements DetectionOperation {

    @Override
    public List<DetectedPattern> performUserBasedDetection(List<MiningRoleTypeChunk> miningRoleTypeChunks,
            DetectionOption roleAnalysisSessionDetectionOptionType) {

        double minFrequency = roleAnalysisSessionDetectionOptionType.getMinFrequencyThreshold();
        double maxFrequency = roleAnalysisSessionDetectionOptionType.getMaxFrequencyThreshold();
        int minIntersection = roleAnalysisSessionDetectionOptionType.getMinPropertiesOverlap();
        int minOccupancy = roleAnalysisSessionDetectionOptionType.getMinOccupancy();
        double similarity = 0.8;

        List<DetectedPattern> detectedPatterns = new ArrayList<>();
        List<MiningRoleTypeChunk> preparedObjects = new ArrayList<>();
        Set<Set<String>> existingDetectedProperties = new HashSet<>();
        for (MiningRoleTypeChunk miningRoleTypeChunk : miningRoleTypeChunks) {
            double frequency = miningRoleTypeChunk.getFrequency();
            if (frequency < minFrequency || frequency > maxFrequency) {
                continue;
            }
            Set<String> members = new HashSet<>(miningRoleTypeChunk.getUsers());
            int membersCount = members.size();
            if (membersCount < minIntersection) {
                continue;
            }

            boolean addPreparedObject = true;
            Set<String> properties = new HashSet<>(miningRoleTypeChunk.getRoles());
            int propertiesCount = miningRoleTypeChunk.getRoles().size();
            if (propertiesCount >= minOccupancy) {

                if (!existingDetectedProperties.contains(properties)) {
                    addPreparedObject = false;
                    existingDetectedProperties.add(properties);
                    detectedPatterns.add(addDetectedObjectJaccard(new HashSet<>(miningRoleTypeChunk.getRoles()),
                            members, null));
                }

            }

            if(addPreparedObject) {
                preparedObjects.add(miningRoleTypeChunk);
            }

        }

        ListMultimap<MiningRoleTypeChunk, MiningRoleTypeChunk> map = ArrayListMultimap.create();
        for (int i = 0; i < preparedObjects.size(); i++) {
            MiningRoleTypeChunk miningRoleTypeChunkA = preparedObjects.get(i);
            Set<String> pointsA = new HashSet<>(miningRoleTypeChunkA.getUsers());
            for (MiningRoleTypeChunk miningRoleTypeChunkB : preparedObjects) {
                HashSet<String> pointsB = new HashSet<>(miningRoleTypeChunkB.getUsers());
                double resultSimilarity = RoleUtils.jacquardSimilarity(pointsA, pointsB);

                if (resultSimilarity >= similarity) {
                    map.putAll(miningRoleTypeChunkB, Collections.singleton(miningRoleTypeChunkA));
                    map.putAll(miningRoleTypeChunkA, Collections.singleton(miningRoleTypeChunkB));
                }

            }
        }

        for (MiningRoleTypeChunk key : map.keySet()) {
            List<MiningRoleTypeChunk> values = map.get(key);
            Set<String> members = new HashSet<>(key.getUsers());
            Set<String> properties = new HashSet<>(key.getRoles());
            for (MiningRoleTypeChunk value : values) {
                members.addAll(value.getUsers());
                properties.addAll(value.getRoles());
            }

            if (properties.size() < minIntersection) {
                continue;
            }

            if (existingDetectedProperties.contains(properties)) {
                continue;
            }
            existingDetectedProperties.add(properties);

            detectedPatterns.add(addDetectedObjectJaccard(properties, members, null));
        }

        return detectedPatterns;
    }

    public static double calculateJaccardSimilarity(Set<String> set1, Set<String> set2) {
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        return (double) intersection.size() / union.size();
    }

    @Override
    public List<DetectedPattern> performRoleBasedDetection(List<MiningUserTypeChunk> miningUserTypeChunks,
            DetectionOption roleAnalysisSessionDetectionOptionType) {

        double minFrequency = roleAnalysisSessionDetectionOptionType.getMinFrequencyThreshold();
        double maxFrequency = roleAnalysisSessionDetectionOptionType.getMaxFrequencyThreshold();
        int minIntersection = roleAnalysisSessionDetectionOptionType.getMinPropertiesOverlap();
        int minOccupancy = roleAnalysisSessionDetectionOptionType.getMinOccupancy();
        double similarity = 0.8;

        List<DetectedPattern> intersections = new ArrayList<>();
        List<MiningUserTypeChunk> preparedObjects = new ArrayList<>();
        for (MiningUserTypeChunk miningUserTypeChunk : miningUserTypeChunks) {
            double frequency = miningUserTypeChunk.getFrequency();
            if (frequency < minFrequency || frequency > maxFrequency) {
                continue;
            }

            Set<String> members = new HashSet<>(miningUserTypeChunk.getRoles());
            if (members.size() < minIntersection) {
                continue;
            }
            preparedObjects.add(miningUserTypeChunk);

            Set<String> properties = new HashSet<>(miningUserTypeChunk.getUsers());
            int propertiesCount = properties.size();
            if (propertiesCount >= minOccupancy) {
                intersections.add(addDetectedObjectJaccard(properties, members, null));
            }

        }

        ListMultimap<MiningUserTypeChunk, MiningUserTypeChunk> map = ArrayListMultimap.create();
        for (int i = 0; i < preparedObjects.size(); i++) {
            MiningUserTypeChunk miningUserTypeChunkA = preparedObjects.get(i);
            Set<String> pointsA = new HashSet<>(miningUserTypeChunkA.getRoles());
            for (int j = i + 1; j < preparedObjects.size(); j++) {
                MiningUserTypeChunk miningUserTypeChunkB = preparedObjects.get(j);
                HashSet<String> pointsB = new HashSet<>(miningUserTypeChunkB.getRoles());
                double resultSimilarity = RoleUtils.jacquardSimilarity(pointsA, pointsB);

                if (resultSimilarity >= similarity) {
                    map.putAll(miningUserTypeChunkB, Collections.singleton(miningUserTypeChunkA));
                }

            }
        }

        for (MiningUserTypeChunk key : map.keySet()) {
            List<MiningUserTypeChunk> values = map.get(key);
            Set<String> properties = new HashSet<>(key.getUsers());
            Set<String> members = new HashSet<>(key.getRoles());
            for (MiningUserTypeChunk value : values) {
                members.addAll(value.getRoles());
                properties.addAll(value.getUsers());
            }

            intersections.add(addDetectedObjectJaccard(properties, members, null));

        }

        return intersections;
    }

}
