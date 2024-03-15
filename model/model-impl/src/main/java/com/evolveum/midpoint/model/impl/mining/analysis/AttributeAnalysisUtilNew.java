package com.evolveum.midpoint.model.impl.mining.analysis;

import java.util.*;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.common.mining.objects.analysis.AttributeAnalysisStructure;
import com.evolveum.midpoint.model.api.mining.RoleAnalysisService;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.impl.PrismPropertyValueImpl;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

// TODO - this class is just fast experiment

/**
 * Utility class for attribute analysis.
 * Used for calculating the density and similarity of the attributes.
 * Used for role analysis cluster similarity chart.
 */
public class AttributeAnalysisUtilNew {
    private static String extractRealValue(Object object) {
        if (object != null) {
            if (object instanceof PolyString) {
                return ((PolyString) object).getOrig();
            } else if (object instanceof PrismPropertyValueImpl) {
                Object realValue = ((PrismPropertyValueImpl<?>) object).getRealValue();
                if (realValue != null) {
                    return realValue.toString();
                }
            } else {
                return object.toString();
            }
        }
        return null;
    }

    public static @NotNull Set<PrismObject<UserType>> fetchPrismUsers(
            @NotNull RoleAnalysisService roleAnalysisService,
            @NotNull Set<String> objectOid,
            @NotNull Task task,
            @NotNull OperationResult result) {
        Set<PrismObject<UserType>> prismUsers = new HashSet<>();

        objectOid.forEach(userOid -> {
            PrismObject<UserType> userTypeObject = roleAnalysisService.getUserTypeObject(userOid, task, result);
            if (userTypeObject != null) {
                prismUsers.add(userTypeObject);
            }
        });

        return prismUsers;
    }

    public static @NotNull Set<PrismObject<RoleType>> fetchPrismRoles(
            @NotNull RoleAnalysisService roleAnalysisService,
            @NotNull Set<String> objectOid,
            @NotNull Task task,
            @NotNull OperationResult result) {
        Set<PrismObject<RoleType>> prismRolesSet = new HashSet<>();

        objectOid.forEach(roleOid -> {
            PrismObject<RoleType> rolePrismObject = roleAnalysisService.getRoleTypeObject(roleOid, task, result);
            if (rolePrismObject != null) {
                prismRolesSet.add(rolePrismObject);
            }
        });

        return prismRolesSet;
    }

    public static void runUserAttributeAnalysis(
            @NotNull Set<PrismObject<UserType>> prismUsers,
            @NotNull List<ItemPath> itemPathSet,
            @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures,
            Double membershipDensity) {

        processUserItemPaths(prismUsers, itemPathSet, attributeAnalysisStructures);
        processUserMultiValueItems(prismUsers, attributeAnalysisStructures, membershipDensity);
    }

    public static void runRoleAttributeAnalysis(
            @NotNull Set<PrismObject<RoleType>> prismRoles,
            @NotNull List<ItemPath> itemPathSet,
            @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures,
            Double membershipDensity) {

        processRoleItemPaths(prismRoles, itemPathSet, attributeAnalysisStructures);
        processRoleMultiValueItems(prismRoles, attributeAnalysisStructures, membershipDensity);
    }

    public static void processUserItemPaths(
            @NotNull Set<PrismObject<UserType>> prismUsers,
            @NotNull List<ItemPath> itemPathSet,
            @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures) {

        int usersCount = prismUsers.size();
        int maximumFrequency = 0;

        for (ItemPath itemPath : itemPathSet) {
            Map<String, Integer> frequencyMap = new TreeMap<>(Collections.reverseOrder());
            int totalRelations = 0;

            for (PrismObject<UserType> userTypeObject : prismUsers) {
                Item<PrismValue, ItemDefinition<?>> item = userTypeObject.findItem(itemPath);

                if (item != null) {
                    Object object = item.getRealValue();
                    String comparedValue = extractRealValue(object);
                    if (comparedValue != null) {
                        int frequency = frequencyMap.getOrDefault(comparedValue, 0) + 1;
                        maximumFrequency = Math.max(maximumFrequency, frequency);
                        frequencyMap.put(comparedValue, frequency);
                        totalRelations++;
                    }
                }
            }

            AttributeAnalysisStructure attributeAnalysisStructure = new AttributeAnalysisStructure(
                    frequencyMap.size(), prismUsers.size(), totalRelations, itemPath.toString());
            attributeAnalysisStructure.setComplexType(UserType.COMPLEX_TYPE);

            attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMap, maximumFrequency));
            generateAttributeStatisticsSingleValue(
                    frequencyMap, attributeAnalysisStructure, usersCount);

            attributeAnalysisStructures.add(attributeAnalysisStructure);
        }
    }

    public static void processRoleItemPaths(
            @NotNull Set<PrismObject<RoleType>> prismRolesSet,
            @NotNull List<ItemPath> itemPathSet,
            @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures) {

        int roleCount = prismRolesSet.size();
        int maximumFrequency = 0;

        for (ItemPath itemPath : itemPathSet) {
            Map<String, Integer> frequencyMap = new HashMap<>();
            int totalRelations = 0;

            for (PrismObject<RoleType> rolePrismObject : prismRolesSet) {
                Item<PrismValue, ItemDefinition<?>> item = rolePrismObject.findItem(itemPath);

                if (item != null) {
                    Object object = item.getRealValue();
                    String comparedValue = extractRealValue(object);
                    if (comparedValue != null) {
                        int frequency = frequencyMap.getOrDefault(comparedValue, 0) + 1;
                        maximumFrequency = Math.max(maximumFrequency, frequency);
                        frequencyMap.put(comparedValue, frequency);
                        totalRelations++;
                    }
                }
            }

            AttributeAnalysisStructure attributeAnalysisStructure = new AttributeAnalysisStructure(
                    frequencyMap.size(), prismRolesSet.size(), totalRelations, itemPath.toString());
            attributeAnalysisStructure.setComplexType(RoleType.COMPLEX_TYPE);
            attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMap, maximumFrequency));
            generateAttributeStatisticsSingleValue(
                    frequencyMap, attributeAnalysisStructure, roleCount);

            attributeAnalysisStructures.add(attributeAnalysisStructure);
        }
    }

    public static void processUserMultiValueItems(
            @NotNull Set<PrismObject<UserType>> prismUsers,
            @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures,
            Double membershipDensity) {

        processUserTypeRoleReferenceType(prismUsers, attributeAnalysisStructures);
        processUserTypeAssignment(prismUsers, attributeAnalysisStructures, membershipDensity);
    }

    public static void processUserTypeRoleReferenceType(
            @NotNull Set<PrismObject<UserType>> prismUsers,
            @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures) {

        Map<String, Integer> frequencyMap = new HashMap<>();
        int totalRelations = 0;
        int maximumFrequency = 0;

        for (PrismObject<UserType> userTypeObject : prismUsers) {
            UserType userObject = userTypeObject.asObjectable();
            List<ObjectReferenceType> roleMembershipRef = userObject.getRoleMembershipRef();

            for (ObjectReferenceType objectReferenceType : roleMembershipRef) {
                String oid = objectReferenceType.getOid();

                int frequency = frequencyMap.getOrDefault(oid, 0) + 1;
                maximumFrequency = Math.max(maximumFrequency, frequency);
                frequencyMap.put(oid, frequency);
                totalRelations++;
            }
        }

        Map<Integer, List<String>> frequencyMapMerged = new HashMap<>();
        for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            List<String> list = frequencyMapMerged.getOrDefault(value, new ArrayList<>());
            list.add(key);
            frequencyMapMerged.put(value, list);
        }

        int usersCount = prismUsers.size();
        String itemPath = "roleMembershipRef";
        AttributeAnalysisStructure attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMap.size(), prismUsers.size(), totalRelations, itemPath);
        attributeAnalysisStructure.setComplexType(UserType.COMPLEX_TYPE);

        generateAttributeStatisticsMultiValue(frequencyMap, attributeAnalysisStructure, usersCount);

        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMap, maximumFrequency));
        attributeAnalysisStructures.add(attributeAnalysisStructure);
    }

    public static void processUserTypeAssignment(
            @NotNull Set<PrismObject<UserType>> prismUsers,
            @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures,
            Double membershipDensity) {

        if (membershipDensity != null) {
            attributeAnalysisStructures.add(new AttributeAnalysisStructure(membershipDensity, "assignment/role"));
        }

        Map<String, Integer> frequencyMapUser = new HashMap<>();
        int totalRelationsUser = 0;
        int maximumFrequencyUser = 0;

        Map<String, Integer> frequencyMapRole = new HashMap<>();
        int totalRelationsRole = 0;
        int maximumFrequencyRole = 0;

        Map<String, Integer> frequencyMapOrg = new HashMap<>();
        int totalRelationsOrg = 0;
        int maximumFrequencyOrg = 0;

        Map<String, Integer> frequencyMapService = new HashMap<>();
        int totalRelationsService = 0;
        int maximumFrequencyService = 0;

        Map<String, Integer> frequencyMapResource = new HashMap<>();
        int totalRelationsResource = 0;
        int maximumFrequencyResource = 0;

        for (PrismObject<UserType> userTypeObject : prismUsers) {
            UserType userObject = userTypeObject.asObjectable();
            List<AssignmentType> assignment = userObject.getAssignment();

            for (AssignmentType assignmentType : assignment) {
                ObjectReferenceType targetRef = assignmentType.getTargetRef();

                if (targetRef == null) {
                    continue;
                }

                String oid = targetRef.getOid();
                String type = targetRef.getType().getLocalPart();

                if (ResourceType.COMPLEX_TYPE.getLocalPart().equals(type)) {
                    int frequency = frequencyMapResource.getOrDefault(oid, 0) + 1;
                    maximumFrequencyResource = Math.max(maximumFrequencyResource, frequency);
                    frequencyMapResource.put(oid, frequency);
                    totalRelationsResource++;
                } else if (RoleType.COMPLEX_TYPE.getLocalPart().equals(type) && membershipDensity == null) {
                    int frequency = frequencyMapRole.getOrDefault(oid, 0) + 1;
                    maximumFrequencyRole = Math.max(maximumFrequencyRole, frequency);
                    frequencyMapRole.put(oid, frequency);
                    totalRelationsRole++;
                } else if (OrgType.COMPLEX_TYPE.getLocalPart().equals(type)) {
                    int frequency = frequencyMapOrg.getOrDefault(oid, 0) + 1;
                    maximumFrequencyOrg = Math.max(maximumFrequencyOrg, frequency);
                    frequencyMapOrg.put(oid, frequency);
                    totalRelationsOrg++;
                } else if (ServiceType.COMPLEX_TYPE.getLocalPart().equals(type)) {
                    int frequency = frequencyMapService.getOrDefault(oid, 0) + 1;
                    maximumFrequencyService = Math.max(maximumFrequencyService, frequency);
                    frequencyMapService.put(oid, frequency);
                    totalRelationsService++;
                } else if (UserType.COMPLEX_TYPE.getLocalPart().equals(type)) {
                    int frequency = frequencyMapUser.getOrDefault(oid, 0) + 1;
                    maximumFrequencyUser = Math.max(maximumFrequencyUser, frequency);
                    frequencyMapUser.put(oid, frequency);
                    totalRelationsUser++;
                }
            }

        }

        AttributeAnalysisStructure attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapService.size(), prismUsers.size(), totalRelationsService, "assignment/service");
        attributeAnalysisStructure.setComplexType(UserType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapService, maximumFrequencyService));
        generateAttributeStatisticsMultiValue(
                frequencyMapService, attributeAnalysisStructure, prismUsers.size());
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapOrg.size(), prismUsers.size(), totalRelationsOrg, "assignment/org");
        attributeAnalysisStructure.setComplexType(UserType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapOrg, maximumFrequencyOrg));
        generateAttributeStatisticsMultiValue(frequencyMapOrg, attributeAnalysisStructure, prismUsers.size());
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        if (membershipDensity == null) {
            attributeAnalysisStructure = new AttributeAnalysisStructure(
                    frequencyMapRole.size(), prismUsers.size(), totalRelationsRole, "assignment/role");
            attributeAnalysisStructure.setComplexType(UserType.COMPLEX_TYPE);
            attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapRole, maximumFrequencyRole));
            generateAttributeStatisticsMultiValue(frequencyMapRole, attributeAnalysisStructure, prismUsers.size());
            attributeAnalysisStructures.add(attributeAnalysisStructure);
        }

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapUser.size(), prismUsers.size(), totalRelationsUser, "assignment/user");
        attributeAnalysisStructure.setComplexType(UserType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapUser, maximumFrequencyUser));
        generateAttributeStatisticsMultiValue(frequencyMapUser, attributeAnalysisStructure, prismUsers.size());
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapResource.size(), prismUsers.size(), totalRelationsResource, "assignment/resource");
        attributeAnalysisStructure.setComplexType(UserType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapResource, maximumFrequencyResource));
        generateAttributeStatisticsMultiValue(frequencyMapResource, attributeAnalysisStructure, prismUsers.size());
        attributeAnalysisStructures.add(attributeAnalysisStructure);
    }

    public static void processRoleMultiValueItems(@NotNull Set<PrismObject<RoleType>> prismRolesSet,
            @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures,
            Double membershipDensity) {

        processRoleTypeArchetypeRefType(prismRolesSet, attributeAnalysisStructures);
        processRoleTypeMembershipRefType(prismRolesSet, attributeAnalysisStructures);
        processRoleTypeAssignments(prismRolesSet, attributeAnalysisStructures);
        processRoleTypeInducements(prismRolesSet, attributeAnalysisStructures);

        if (membershipDensity != null) {
            AttributeAnalysisStructure attributeAnalysisStructure = new AttributeAnalysisStructure(membershipDensity, "member/user");
            attributeAnalysisStructure.setComplexType(RoleType.COMPLEX_TYPE);
            attributeAnalysisStructures.add(attributeAnalysisStructure);
        }
    }

    public static void processRoleTypeArchetypeRefType(
            @NotNull Set<PrismObject<RoleType>> prismRole,
            @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures) {

        Map<String, Integer> frequencyMap = new HashMap<>();
        int totalRelations = 0;
        int maximumFrequency = 0;

        for (PrismObject<RoleType> rolePrism : prismRole) {
            RoleType role = rolePrism.asObjectable();
            List<ObjectReferenceType> archetypeRef = role.getArchetypeRef();

            for (ObjectReferenceType objectReferenceType : archetypeRef) {
                String oid = objectReferenceType.getOid();
                int frequency = frequencyMap.getOrDefault(oid, 0) + 1;
                maximumFrequency = Math.max(maximumFrequency, frequency);
                frequencyMap.put(oid, frequency);
                totalRelations++;
            }
        }
        AttributeAnalysisStructure attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMap.size(), prismRole.size(), totalRelations, "archetypeRef");
        attributeAnalysisStructure.setComplexType(RoleType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMap, maximumFrequency));
        generateAttributeStatisticsMultiValue(frequencyMap, attributeAnalysisStructure, prismRole.size());
        attributeAnalysisStructures.add(attributeAnalysisStructure);

    }

    public static void processRoleTypeMembershipRefType(
            @NotNull Set<PrismObject<RoleType>> prismRole,
            @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures) {

        Map<String, Integer> frequencyMap = new HashMap<>();
        int totalRelations = 0;
        int maximumFrequency = 0;

        for (PrismObject<RoleType> rolePrism : prismRole) {
            RoleType role = rolePrism.asObjectable();
            List<ObjectReferenceType> roleMembershipRef = role.getRoleMembershipRef();

            for (ObjectReferenceType objectReferenceType : roleMembershipRef) {
                String oid = objectReferenceType.getOid();
                int frequency = frequencyMap.getOrDefault(oid, 0) + 1;
                maximumFrequency = Math.max(maximumFrequency, frequency);
                frequencyMap.put(oid, frequency);
                totalRelations++;
            }
        }
        AttributeAnalysisStructure attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMap.size(), prismRole.size(), totalRelations, "roleMembershipRef");
        attributeAnalysisStructure.setComplexType(RoleType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMap, maximumFrequency));
        generateAttributeStatisticsMultiValue(frequencyMap, attributeAnalysisStructure, prismRole.size());
        attributeAnalysisStructures.add(attributeAnalysisStructure);

    }

    public static void processRoleTypeAssignments(
            @NotNull Set<PrismObject<RoleType>> prismRolesSet,
            @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures) {

        Map<String, Integer> frequencyMapUser = new HashMap<>();
        int totalRelationsUser = 0;
        int maximumFrequencyUser = 0;

        Map<String, Integer> frequencyMapRole = new HashMap<>();
        int totalRelationsRole = 0;
        int maximumFrequencyRole = 0;

        Map<String, Integer> frequencyMapOrg = new HashMap<>();
        int totalRelationsOrg = 0;
        int maximumFrequencyOrg = 0;

        Map<String, Integer> frequencyMapService = new HashMap<>();
        int totalRelationsService = 0;
        int maximumFrequencyService = 0;

        Map<String, Integer> frequencyMapResource = new HashMap<>();
        int totalRelationsResource = 0;
        int maximumFrequencyResource = 0;

        for (PrismObject<RoleType> rolePrismObject : prismRolesSet) {
            RoleType roleObject = rolePrismObject.asObjectable();
            List<AssignmentType> assignment = roleObject.getAssignment();

            for (AssignmentType assignmentType : assignment) {
                ObjectReferenceType targetRef = assignmentType.getTargetRef();

                if (targetRef == null) {
                    continue;
                }

                String oid = targetRef.getOid();
                String type = targetRef.getType().getLocalPart();

                if (ResourceType.COMPLEX_TYPE.getLocalPart().equals(type)) {
                    int frequency = frequencyMapResource.getOrDefault(oid, 0) + 1;
                    maximumFrequencyResource = Math.max(maximumFrequencyResource, frequency);
                    frequencyMapResource.put(oid, frequency);
                    totalRelationsResource++;
                } else if (RoleType.COMPLEX_TYPE.getLocalPart().equals(type)) {
                    int frequency = frequencyMapRole.getOrDefault(oid, 0) + 1;
                    maximumFrequencyRole = Math.max(maximumFrequencyRole, frequency);
                    frequencyMapRole.put(oid, frequency);
                    totalRelationsRole++;
                } else if (OrgType.COMPLEX_TYPE.getLocalPart().equals(type)) {
                    int frequency = frequencyMapOrg.getOrDefault(oid, 0) + 1;
                    maximumFrequencyOrg = Math.max(maximumFrequencyOrg, frequency);
                    frequencyMapOrg.put(oid, frequency);
                    totalRelationsOrg++;
                } else if (ServiceType.COMPLEX_TYPE.getLocalPart().equals(type)) {
                    int frequency = frequencyMapService.getOrDefault(oid, 0) + 1;
                    maximumFrequencyService = Math.max(maximumFrequencyService, frequency);
                    frequencyMapService.put(oid, frequency);
                    totalRelationsService++;
                } else if (UserType.COMPLEX_TYPE.getLocalPart().equals(type)) {
                    int frequency = frequencyMapUser.getOrDefault(oid, 0) + 1;
                    maximumFrequencyUser = Math.max(maximumFrequencyUser, frequency);
                    frequencyMapUser.put(oid, frequency);
                    totalRelationsUser++;
                }
            }
        }

        AttributeAnalysisStructure attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapService.size(), prismRolesSet.size(), totalRelationsService, "assignment/service");
        attributeAnalysisStructure.setComplexType(RoleType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapService, maximumFrequencyService));
        generateAttributeStatisticsMultiValue(frequencyMapService, attributeAnalysisStructure, prismRolesSet.size());
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapOrg.size(), prismRolesSet.size(), totalRelationsOrg, "assignment/org");
        attributeAnalysisStructure.setComplexType(RoleType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapOrg, maximumFrequencyOrg));
        generateAttributeStatisticsMultiValue(frequencyMapOrg, attributeAnalysisStructure, prismRolesSet.size());
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapRole.size(), prismRolesSet.size(), totalRelationsRole, "assignment/role");
        attributeAnalysisStructure.setComplexType(RoleType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapRole, maximumFrequencyRole));
        generateAttributeStatisticsMultiValue(frequencyMapRole, attributeAnalysisStructure, prismRolesSet.size());
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapUser.size(), prismRolesSet.size(), totalRelationsUser, "assignment/user");
        attributeAnalysisStructure.setComplexType(RoleType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapUser, maximumFrequencyUser));
        generateAttributeStatisticsMultiValue(frequencyMapUser, attributeAnalysisStructure, prismRolesSet.size());
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapResource.size(), prismRolesSet.size(), totalRelationsResource, "assignment/resource");
        attributeAnalysisStructure.setComplexType(RoleType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapResource, maximumFrequencyResource));

        attributeAnalysisStructures.add(attributeAnalysisStructure);

    }

    public static void processRoleTypeInducements(
            @NotNull Set<PrismObject<RoleType>> prismRolesSet,
            @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures) {

        Map<String, Integer> frequencyMapUser = new HashMap<>();
        int totalRelationsUser = 0;
        int maximumFrequencyUser = 0;

        Map<String, Integer> frequencyMapRole = new HashMap<>();
        int totalRelationsRole = 0;
        int maximumFrequencyRole = 0;

        Map<String, Integer> frequencyMapOrg = new HashMap<>();
        int totalRelationsOrg = 0;
        int maximumFrequencyOrg = 0;

        Map<String, Integer> frequencyMapService = new HashMap<>();
        int totalRelationsService = 0;
        int maximumFrequencyService = 0;

        Map<String, Integer> frequencyMapResource = new HashMap<>();
        int totalRelationsResource = 0;
        int maximumFrequencyResource = 0;

        for (PrismObject<RoleType> rolePrismObject : prismRolesSet) {
            RoleType roleObject = rolePrismObject.asObjectable();
            List<AssignmentType> assignment = roleObject.getInducement();

            for (AssignmentType assignmentType : assignment) {
                ObjectReferenceType targetRef = assignmentType.getTargetRef();

                if (targetRef == null) {
                    continue;
                }

                String oid = targetRef.getOid();
                String type = targetRef.getType().getLocalPart();

                if (ResourceType.COMPLEX_TYPE.getLocalPart().equals(type)) {
                    int frequency = frequencyMapResource.getOrDefault(oid, 0) + 1;
                    maximumFrequencyResource = Math.max(maximumFrequencyResource, frequency);
                    frequencyMapResource.put(oid, frequency);
                    totalRelationsResource++;
                } else if (RoleType.COMPLEX_TYPE.getLocalPart().equals(type)) {
                    int frequency = frequencyMapRole.getOrDefault(oid, 0) + 1;
                    maximumFrequencyRole = Math.max(maximumFrequencyRole, frequency);
                    frequencyMapRole.put(oid, frequency);
                    totalRelationsRole++;
                } else if (OrgType.COMPLEX_TYPE.getLocalPart().equals(type)) {
                    int frequency = frequencyMapOrg.getOrDefault(oid, 0) + 1;
                    maximumFrequencyOrg = Math.max(maximumFrequencyOrg, frequency);
                    frequencyMapOrg.put(oid, frequency);
                    totalRelationsOrg++;
                } else if (ServiceType.COMPLEX_TYPE.getLocalPart().equals(type)) {
                    int frequency = frequencyMapService.getOrDefault(oid, 0) + 1;
                    maximumFrequencyService = Math.max(maximumFrequencyService, frequency);
                    frequencyMapService.put(oid, frequency);
                    totalRelationsService++;
                } else if (UserType.COMPLEX_TYPE.getLocalPart().equals(type)) {
                    int frequency = frequencyMapUser.getOrDefault(oid, 0) + 1;
                    maximumFrequencyUser = Math.max(maximumFrequencyUser, frequency);
                    frequencyMapUser.put(oid, frequency);
                    totalRelationsUser++;
                }
            }
        }

        AttributeAnalysisStructure attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapService.size(), prismRolesSet.size(), totalRelationsService, "inducement/service");
        attributeAnalysisStructure.setComplexType(RoleType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapService, maximumFrequencyService));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapOrg.size(), prismRolesSet.size(), totalRelationsOrg, "inducement/org");
        attributeAnalysisStructure.setComplexType(RoleType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapOrg, maximumFrequencyOrg));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapRole.size(), prismRolesSet.size(), totalRelationsRole, "inducement/role");
        attributeAnalysisStructure.setComplexType(RoleType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapRole, maximumFrequencyRole));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapUser.size(), prismRolesSet.size(), totalRelationsUser, "inducement/user");
        attributeAnalysisStructure.setComplexType(RoleType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapUser, maximumFrequencyUser));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapResource.size(), prismRolesSet.size(), totalRelationsResource, "inducement/resource");
        attributeAnalysisStructure.setComplexType(RoleType.COMPLEX_TYPE);
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapResource, maximumFrequencyResource));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

    }

    public static String generateFrequencyMapDescription(
            @NotNull Map<String, Integer> frequencyMap,
            int maximumFrequency) {
        StringBuilder description = new StringBuilder("Analysis of value frequencies:\n");

        if (frequencyMap.isEmpty()) {
            description.append("No values found in the frequency map.");
        } else {
            int totalValues = frequencyMap.size();
            int totalOccurrences = frequencyMap.values().stream().mapToInt(Integer::intValue).sum();
            double averageOccurrences = (double) totalOccurrences / totalValues;

            description.append("Total unique values: ").append(totalValues).append("\n");
            description.append("Total occurrences: ").append(totalOccurrences).append("\n");
            description.append("Average occurrences per value: ").append(String.format("%.2f", averageOccurrences)).append("\n");

            int threshold = totalOccurrences / totalValues;
            Set<String> highFrequencyValues = new HashSet<>();
            Set<String> lowFrequencyValues = new HashSet<>();
            Set<String> maximumFrequencyValues = new HashSet<>();

            int maxFrequency = 0;
            int minFrequency = 0;

            int maxFrequencyLow = 0;
            int minFrequencyLow = 0;
            for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
                String value = entry.getKey();
                int frequency = entry.getValue();

                if (frequency >= threshold) {
                    minFrequency = Math.min(minFrequency, frequency);
                    maxFrequency = Math.max(maxFrequency, frequency);
                    highFrequencyValues.add(value);
                } else {
                    minFrequencyLow = Math.min(minFrequencyLow, frequency);
                    maxFrequencyLow = Math.max(maxFrequencyLow, frequency);
                    lowFrequencyValues.add(value);
                }

                if (frequency == maximumFrequency) {
                    maximumFrequencyValues.add(value);
                }
            }

            description.append("Maximum frequency")
                    .append("(").append(maximumFrequency)
                    .append("times)").append(": ")
                    .append(maximumFrequencyValues)
                    .append("\n");

            description.append("High frequency values")
                    .append("(").append(minFrequency).append("-").append(maxFrequency)
                    .append("times)").append(": ")
                    .append(highFrequencyValues)
                    .append("\n");

            description.append("Low frequency values:")
                    .append("(").append(minFrequencyLow).append("-").append(maxFrequencyLow)
                    .append("times)").append(": ")
                    .append(lowFrequencyValues)
                    .append("\n");

        }

        return description.toString();
    }

    public static void generateAttributeStatisticsSingleValue(
            @NotNull Map<String, Integer> frequencyMap,
            @NotNull AttributeAnalysisStructure attributeAnalysisStructure,
            int prismObjectsCount) {

        attributeAnalysisStructure.setAttributeStatistics(new ArrayList<>());
        frequencyMap.forEach((key, value) -> {
            RoleAnalysisAttributeStatistics attributeStatistic = new RoleAnalysisAttributeStatistics();
            double percentageFrequency = (double) value / prismObjectsCount * 100;
            attributeStatistic.setAttributeValue(key);
            attributeStatistic.setFrequency(percentageFrequency);
            attributeAnalysisStructure.getAttributeStatistics().add(attributeStatistic);
        });

        attributeAnalysisStructure.setMultiValue(false);
    }

    public static void generateAttributeStatisticsMultiValue(
            @NotNull Map<String, Integer> frequencyMap,
            @NotNull AttributeAnalysisStructure attributeAnalysisStructure,
            int prismObjectsCount) {

        attributeAnalysisStructure.setAttributeStatistics(new ArrayList<>());
        frequencyMap.forEach((key, value) -> {
            RoleAnalysisAttributeStatistics attributeStatistic = new RoleAnalysisAttributeStatistics();
            double percentageFrequency = (double) value / prismObjectsCount * 100;
            attributeStatistic.setAttributeValue(key);
            attributeStatistic.setFrequency(percentageFrequency);
            attributeAnalysisStructure.getAttributeStatistics().add(attributeStatistic);
        });
        attributeAnalysisStructure.setMultiValue(true);
    }
}
