/*
 * Copyright (C) 2010-2024 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.ninja.action.mining.generator.context;

import static com.evolveum.midpoint.schema.util.FocusTypeUtil.createArchetypeAssignment;
import static com.evolveum.midpoint.schema.util.FocusTypeUtil.createTargetAssignment;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.ninja.action.mining.generator.object.InitialObjectsDefinition;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * Utility methods for generating rbac data.
 * <p>
 * Part of RBAC Testing Data, which provides testing data for role mining and other RBAC-related processes.
 */
public class RbacGeneratorUtils {

    /**
     * Retrieves a randomly selected location business role.
     *
     * @return The randomly selected location business role.
     */
    protected static @NotNull InitialObjectsDefinition.LocationInitialBusinessRole getRandomLocationBusinessRole() {
        InitialObjectsDefinition.LocationInitialBusinessRole[] roles = InitialObjectsDefinition.LocationInitialBusinessRole.values();
        Random random = new Random();
        return roles[random.nextInt(roles.length)];
    }

    /**
     * Retrieves a randomly selected job business role.
     *
     * @return The randomly selected job business role.
     */
    protected static @NotNull InitialObjectsDefinition.JobInitialBusinessRole getRandomJobBusinessRole() {
        InitialObjectsDefinition.JobInitialBusinessRole[] roles = InitialObjectsDefinition.JobInitialBusinessRole.values();
        Random random = new Random();
        return roles[random.nextInt(roles.length)];
    }

    /**
     * Retrieves a randomly selected plankton abstract role.
     *
     * @return The randomly selected plankton abstract role.
     */
    protected static @NotNull InitialObjectsDefinition.PlanktonApplicationBusinessAbstractRole getRandomPlanktonRole() {
        InitialObjectsDefinition.PlanktonApplicationBusinessAbstractRole[] roles = InitialObjectsDefinition.PlanktonApplicationBusinessAbstractRole.values();
        Random random = new Random();
        return roles[random.nextInt(roles.length)];
    }

    /**
     * Retrieves a list of randomly selected plankton abstract roles.
     *
     * @param minRoles The minimum number of roles to select.
     * @return A list of randomly selected plankton abstract roles.
     */
    protected static @NotNull List<InitialObjectsDefinition.PlanktonApplicationBusinessAbstractRole> getRandomPlanktonRoles(
            int minRoles) {
        int maxRoles = InitialObjectsDefinition.PlanktonApplicationBusinessAbstractRole.values().length;

        Random random = new Random();
        int numRoles = minRoles + random.nextInt(maxRoles - minRoles + 1);

        Set<InitialObjectsDefinition.PlanktonApplicationBusinessAbstractRole> selectedRoles = EnumSet.noneOf(
                InitialObjectsDefinition.PlanktonApplicationBusinessAbstractRole.class);

        while (selectedRoles.size() < numRoles) {
            InitialObjectsDefinition.PlanktonApplicationBusinessAbstractRole randomRole = getRandomPlanktonRole();
            selectedRoles.add(randomRole);
        }

        return new ArrayList<>(selectedRoles);
    }

    /**
     * Retrieves a randomly selected job title from the provided list, including an empty string.
     *
     * @return The randomly selected job title.
     */
    public static @NotNull String getRandomlyJobTitlesWithNone() {
        List<String> jobTitles = List.of("engineer", "analyst", "reviewer", "editor", "");
        Random random = new Random();
        return jobTitles.get(random.nextInt(jobTitles.size()));
    }

    /**
     * Retrieves a randomly selected job title from the provided list, excluding an empty string.
     *
     * @return The randomly selected job title.
     */
    public static @NotNull String getRandomlyJobTitles() {
        List<String> jobTitles = List.of("engineer", "analyst", "reviewer", "editor");
        Random random = new Random();
        return jobTitles.get(random.nextInt(jobTitles.size()));
    }

    /**
     * Creates an assignment of type OrgType with the provided OID.
     *
     * @param oid The OID of the organization.
     * @return The created assignment.
     */
    protected static @NotNull AssignmentType createOrgAssignment(@NotNull String oid) {
        ObjectReferenceType ref = new ObjectReferenceType()
                .oid(oid)
                .type(OrgType.COMPLEX_TYPE);

        return createTargetAssignment(ref);
    }

    /**
     * Creates an assignment of type RoleType with the provided OID.
     *
     * @param oid The OID of the role object.
     * @return The created assignment.
     */
    protected static @NotNull AssignmentType createRoleAssignment(@NotNull String oid) {
        ObjectReferenceType ref = new ObjectReferenceType()
                .oid(oid)
                .type(RoleType.COMPLEX_TYPE);

        return createTargetAssignment(ref);
    }

    /**
     * Creates an assignment of type ArchetypeType with the provided OID.
     *
     * @param user The user for which to create the assignment.
     * @param archetypeOid The OID of the archetype object.
     */
    public static void setUpArchetypeUser(@NotNull UserType user, @NotNull String archetypeOid) {
        AssignmentType targetAssignment = createArchetypeAssignment(archetypeOid);
        user.getAssignment().add(targetAssignment);
        user.getArchetypeRef().add(new ObjectReferenceType().oid(archetypeOid).type(ArchetypeType.COMPLEX_TYPE));
    }

    /**
     * Retrieves a list of PrismObjects representing business RoleType assignments for the specified AssignmentHolderType.
     *
     * @param object The AssignmentHolderType for which to retrieve business role assignments.
     * @param repository The RepositoryService used to retrieve objects from the repository.
     * @param result The OperationResult to track the operation's result.
     * @return A list of PrismObjects representing the business RoleType assignments.
     * @throws SchemaException If an error related to schema occurs.
     * @throws ObjectNotFoundException If an object cannot be found in the repository.
     */
    protected static @NotNull List<PrismObject<RoleType>> getBusinessRolesOidAssignment(
            @NotNull AssignmentHolderType object,
            @NotNull RepositoryService repository,
            @NotNull OperationResult result) throws SchemaException, ObjectNotFoundException {
        List<AssignmentType> assignments = object.getAssignment();

        List<PrismObject<RoleType>> list = new ArrayList<>();
        for (AssignmentType assignment : assignments) {
            ObjectReferenceType targetRef = assignment.getTargetRef();
            if (targetRef != null) {
                if (targetRef.getType().equals(RoleType.COMPLEX_TYPE)) {
                    String oid = targetRef.getOid();
                    PrismObject<RoleType> roleTypeObject = repository.getObject(RoleType.class, oid, null, result);
                    RoleType role = roleTypeObject.asObjectable();
                    List<ObjectReferenceType> archetypeRef = role.getArchetypeRef();
                    if (archetypeRef != null && !archetypeRef.isEmpty()) {
                        ObjectReferenceType objectReferenceType = archetypeRef.get(0);
                        String oid1 = objectReferenceType.getOid();
                        if (oid1.equals("00000000-0000-0000-0000-000000000321")) {
                            list.add(roleTypeObject);
                        }
                    }
                }
            }
        }
        return list;
    }

    /**
     * Loads names from a CSV file located at the specified path.
     * <p>
     * NOTE: names are expected to be in the column with the specified header at "name" value.
     *
     * @param csvPath The path to the CSV file.
     * @return A set containing the loaded names.
     * @throws IOException If an I/O error occurs while reading the CSV file.
     */
    protected static @NotNull Set<String> loadNamesFromCSV(@NotNull String csvPath) throws IOException {
        Set<String> names = new HashSet<>();

        try (Reader reader = new FileReader(csvPath);
                CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            int nameColumnIndex = csvParser.getHeaderMap().get("name");
            for (CSVRecord record : csvParser) {
                String name = record.get(nameColumnIndex).trim();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }

        return names;
    }

}
