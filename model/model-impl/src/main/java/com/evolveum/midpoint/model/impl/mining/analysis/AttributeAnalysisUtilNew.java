package com.evolveum.midpoint.model.impl.mining.analysis;

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

import org.jetbrains.annotations.NotNull;

import java.util.*;

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

    public static Set<PrismObject<UserType>> fetchPrismUsers(@NotNull RoleAnalysisService roleAnalysisService,
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

    public static Set<PrismObject<RoleType>> fetchPrismRoles(@NotNull RoleAnalysisService roleAnalysisService,
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

    public static void runUserAttributeAnalysis(Set<PrismObject<UserType>> prismUsers,
            @NotNull List<ItemPath> itemPathSet,
            List<AttributeAnalysisStructure> attributeAnalysisStructures,
            Double membershipDensity) {

        Map<String, String> nameMap = new HashMap<>();
        for (PrismObject<UserType> userTypeObject : prismUsers) {
            UserType user = userTypeObject.asObjectable();
            nameMap.put(user.getOid(), user.getName().getOrig());
        }

        processUserItemPaths(prismUsers, itemPathSet, attributeAnalysisStructures);
        processUserMultiValueItems(prismUsers, membershipDensity, attributeAnalysisStructures);
    }

    public static void runRoleAttributeAnalysis(Set<PrismObject<RoleType>> prismRoles,
            @NotNull List<ItemPath> itemPathSet,
            List<AttributeAnalysisStructure> attributeAnalysisStructures,
            Double membershipDensity) {

        processRoleItemPaths(prismRoles, itemPathSet, attributeAnalysisStructures);
        processRoleMultiValueItems(prismRoles, membershipDensity, attributeAnalysisStructures);
    }

    public static void processUserItemPaths(Set<PrismObject<UserType>> prismUsers,
            @NotNull List<ItemPath> itemPathSet,
            List<AttributeAnalysisStructure> attributeAnalysisStructures) {

        int maximumFrequency = 0;
        for (ItemPath itemPath : itemPathSet) {
            Map<String, Integer> frequencyMap = new HashMap<>();

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

            attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMap, maximumFrequency));
            attributeAnalysisStructures.add(attributeAnalysisStructure);
        }
    }

    public static void processRoleItemPaths(Set<PrismObject<RoleType>> prismRolesSet,
            @NotNull List<ItemPath> itemPathSet,
            List<AttributeAnalysisStructure> attributeAnalysisStructures) {

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
            attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMap, maximumFrequency));

            attributeAnalysisStructures.add(attributeAnalysisStructure);
        }
    }

    public static void processUserMultiValueItems(
            @NotNull Set<PrismObject<UserType>> prismUsers,
            Double membershipDensity,
            @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures) {

        processUserTypeRoleReferenceType(prismUsers, attributeAnalysisStructures);
        processUserTypeLinkReferenceType(prismUsers, attributeAnalysisStructures);
        processUserTypeAssignment(prismUsers, membershipDensity, attributeAnalysisStructures);
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
        AttributeAnalysisStructure attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMap.size(), prismUsers.size(), totalRelations, "roleMembershipRef");

        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMap, maximumFrequency));
        attributeAnalysisStructures.add(attributeAnalysisStructure);
    }

    public static void processUserTypeLinkReferenceType(
            @NotNull Set<PrismObject<UserType>> prismUsers,
            @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures) {

        Map<String, Integer> frequencyMap = new HashMap<>();
        int totalRelations = 0;
        int maximumFrequency = 0;
        for (PrismObject<UserType> userTypeObject : prismUsers) {
            UserType userObject = userTypeObject.asObjectable();
            List<ObjectReferenceType> linkRef = userObject.getLinkRef();

            for (ObjectReferenceType objectReferenceType : linkRef) {
                String oid = objectReferenceType.getOid();
                int frequency = frequencyMap.getOrDefault(oid, 0) + 1;
                maximumFrequency = Math.max(maximumFrequency, frequency);
                frequencyMap.put(oid, frequency);
                totalRelations++;
            }
        }
        AttributeAnalysisStructure attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMap.size(), prismUsers.size(), totalRelations, "linkRef");

        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMap, maximumFrequency));
        attributeAnalysisStructures.add(attributeAnalysisStructure);
    }

    public static void processUserTypeAssignment(
            @NotNull Set<PrismObject<UserType>> prismUsers,
            Double membershipDensity, @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures) {

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
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapService, maximumFrequencyService));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapOrg.size(), prismUsers.size(), totalRelationsOrg, "assignment/org");
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapOrg, maximumFrequencyOrg));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        if (membershipDensity == null) {
            attributeAnalysisStructure = new AttributeAnalysisStructure(
                    frequencyMapRole.size(), prismUsers.size(), totalRelationsRole, "assignment/role");
            attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapRole, maximumFrequencyRole));
            attributeAnalysisStructures.add(attributeAnalysisStructure);
        }

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapUser.size(), prismUsers.size(), totalRelationsUser, "assignment/user");
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapUser, maximumFrequencyUser));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapResource.size(), prismUsers.size(), totalRelationsResource, "assignment/resource");
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapResource, maximumFrequencyResource));
        attributeAnalysisStructures.add(attributeAnalysisStructure);
    }

    public static void processRoleMultiValueItems(@NotNull Set<PrismObject<RoleType>> prismRolesSet,
            Double membershipDensity,
            List<AttributeAnalysisStructure> attributeAnalysisStructures) {

        processRoleTypeLinkReferenceType(prismRolesSet, attributeAnalysisStructures);
        processRoleTypeArchetypeRefType(prismRolesSet, attributeAnalysisStructures);
        processRoleTypeMembershipRefType(prismRolesSet, attributeAnalysisStructures);
        processRoleTypeAssignments(prismRolesSet, attributeAnalysisStructures);
        processRoleTypeInducements(prismRolesSet, attributeAnalysisStructures);

        if (membershipDensity != null) {
            attributeAnalysisStructures.add(new AttributeAnalysisStructure(membershipDensity, "member/user"));
        }
    }

    public static void processRoleTypeLinkReferenceType(
            @NotNull Set<PrismObject<RoleType>> prismRole,
            @NotNull List<AttributeAnalysisStructure> attributeAnalysisStructures) {

        Map<String, Integer> frequencyMap = new HashMap<>();
        int totalRelations = 0;
        int maximumFrequency = 0;

        for (PrismObject<RoleType> rolePrism : prismRole) {
            RoleType role = rolePrism.asObjectable();
            List<ObjectReferenceType> linkRef = role.getLinkRef();

            for (ObjectReferenceType objectReferenceType : linkRef) {
                String oid = objectReferenceType.getOid();
                int frequency = frequencyMap.getOrDefault(oid, 0) + 1;
                maximumFrequency = Math.max(maximumFrequency, frequency);
                totalRelations++;
            }
        }

        AttributeAnalysisStructure attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMap.size(), prismRole.size(), totalRelations, "linkRef");
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMap, maximumFrequency));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

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
                totalRelations++;
            }
        }
        AttributeAnalysisStructure attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMap.size(), prismRole.size(), totalRelations, "archetypeRef");
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMap, maximumFrequency));
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
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMap, maximumFrequency));
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
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapService, maximumFrequencyService));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapOrg.size(), prismRolesSet.size(), totalRelationsOrg, "assignment/org");
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapOrg, maximumFrequencyOrg));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapRole.size(), prismRolesSet.size(), totalRelationsRole, "assignment/role");
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapRole, maximumFrequencyRole));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapUser.size(), prismRolesSet.size(), totalRelationsUser, "assignment/user");
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapUser, maximumFrequencyUser));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapResource.size(), prismRolesSet.size(), totalRelationsResource, "assignment/resource");
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
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapService, maximumFrequencyService));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapOrg.size(), prismRolesSet.size(), totalRelationsOrg, "inducement/org");
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapOrg, maximumFrequencyOrg));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapRole.size(), prismRolesSet.size(), totalRelationsRole, "inducement/role");
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapRole, maximumFrequencyRole));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapUser.size(), prismRolesSet.size(), totalRelationsUser, "inducement/user");
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapUser, maximumFrequencyUser));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

        attributeAnalysisStructure = new AttributeAnalysisStructure(
                frequencyMapResource.size(), prismRolesSet.size(), totalRelationsResource, "inducement/resource");
        attributeAnalysisStructure.setDescription(generateFrequencyMapDescription(frequencyMapResource, maximumFrequencyResource));
        attributeAnalysisStructures.add(attributeAnalysisStructure);

    }

    public static String generateFrequencyMapDescription(Map<String, Integer> frequencyMap,
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

}
