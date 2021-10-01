/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.assertj.core.api.Assertions;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.repo.sqale.qmodel.focus.MUser;
import com.evolveum.midpoint.repo.sqale.qmodel.focus.QUser;
import com.evolveum.midpoint.repo.sqale.qmodel.role.MService;
import com.evolveum.midpoint.repo.sqale.qmodel.role.QService;
import com.evolveum.midpoint.repo.sqale.qmodel.task.MTask;
import com.evolveum.midpoint.repo.sqale.qmodel.task.QTask;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ServiceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;

public class SqaleRepoModifyObjectTest extends SqaleRepoBaseTest {

    private String user1Oid; // typical object
    private String task1Oid; // task has more attribute type variability
    //    private String shadow1Oid; // ditto
    private String service1Oid; // object with integer attribute

    @BeforeClass
    public void initObjects() throws Exception {
        OperationResult result = createOperationResult();

        user1Oid = repositoryService.addObject(
                new UserType(prismContext).name("user-1").asPrismObject(),
                null, result);
        task1Oid = repositoryService.addObject(
                new TaskType(prismContext).name("task-1").asPrismObject(),
                null, result);
//        shadow1Oid = repositoryService.addObject(
//                new ShadowType(prismContext).name("shadow-1").asPrismObject(),
//                null, result);
        service1Oid = repositoryService.addObject(
                new ServiceType(prismContext).name("service-1").asPrismObject(),
                null, result);

        assertThatOperationResult(result).isSuccess();
    }

    @Test
    public void test100ChangeStringAttribute()
            throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException {
        OperationResult result = createOperationResult();
        MUser originalRow = selectObjectByOid(QUser.class, user1Oid);

        given("delta with email change for user 1 using property add modification");
        ObjectDelta<UserType> delta = prismContext.deltaFor(UserType.class)
                .property(UserType.F_EMAIL_ADDRESS).add("new@email.com")
                .asObjectDelta(user1Oid);

        when("modifyObject is called");
        repositoryService.modifyObject(UserType.class, user1Oid, delta.getModifications(), result);

        then("operation is successful");
        assertThatOperationResult(result).isSuccess();

        and("serialized form (fullObject) is updated");
        UserType userObject = repositoryService.getObject(UserType.class, user1Oid, null, result)
                .asObjectable();
        assertThat(userObject.getEmailAddress()).isEqualTo("new@email.com");
        assertThat(userObject.getVersion()).isEqualTo(String.valueOf(originalRow.version + 1));

        and("externalized column is updated");
        MUser row = selectObjectByOid(QUser.class, user1Oid);
        assertThat(row.version).isEqualTo(originalRow.version + 1);
        assertThat(row.emailAddress).isEqualTo("new@email.com");
    }

    /**
     * NOTE: This test documents current behavior where {@link ItemDelta#applyTo(Item)} interprets
     * ADD for single-value item as REPLACE if the value is not empty.
     * This behavior is likely to change.
     */
    @Test
    public void test101ChangeStringAttributeWithPreviousValueUsingAddModification()
            throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException {
        OperationResult result = createOperationResult();
        MUser originalRow = selectObjectByOid(QUser.class, user1Oid);

        given("delta with email change for user 1 using property add modification");
        ObjectDelta<UserType> delta = prismContext.deltaFor(UserType.class)
                .property(UserType.F_EMAIL_ADDRESS).add("new2@email.com")
                .asObjectDelta(user1Oid);

        when("modifyObject is called");
        repositoryService.modifyObject(UserType.class, user1Oid, delta.getModifications(), result);

        then("operation is successful");
        assertThatOperationResult(result).isSuccess();

        and("serialized form (fullObject) is updated and old value is overridden");
        UserType userObject = repositoryService.getObject(UserType.class, user1Oid, null, result)
                .asObjectable();
        assertThat(userObject.getEmailAddress()).isEqualTo("new2@email.com");
        assertThat(userObject.getVersion()).isEqualTo(String.valueOf(originalRow.version + 1));

        and("externalized column is updated");
        MUser row = selectObjectByOid(QUser.class, user1Oid);
        assertThat(row.version).isEqualTo(originalRow.version + 1);
        assertThat(row.emailAddress).isEqualTo("new2@email.com");
    }

    @Test
    public void test102DeleteStringAttribute()
            throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException {
        OperationResult result = createOperationResult();
        MUser originalRow = selectObjectByOid(QUser.class, user1Oid);

        given("delta with email replace to null ('delete') for user 1");
        ObjectDelta<UserType> delta = prismContext.deltaFor(UserType.class)
                .property(UserType.F_EMAIL_ADDRESS).replace()
                .asObjectDelta(user1Oid);

        when("modifyObject is called");
        repositoryService.modifyObject(UserType.class, user1Oid, delta.getModifications(), result);

        then("operation is successful");
        assertThatOperationResult(result).isSuccess();

        and("serialized form (fullObject) is updated and email is gone");
        UserType userObject = repositoryService.getObject(UserType.class, user1Oid, null, result)
                .asObjectable();
        assertThat(userObject.getEmailAddress()).isNull();
        assertThat(userObject.getVersion()).isEqualTo(String.valueOf(originalRow.version + 1));

        and("externalized column is set to NULL");
        MUser row = selectObjectByOid(QUser.class, user1Oid);
        assertThat(row.version).isEqualTo(originalRow.version + 1);
        assertThat(row.emailAddress).isNull();
    }

    @Test
    public void test103StringReplacePreviousNullValueIsOk()
            throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException {
        OperationResult result = createOperationResult();

        given("delta with email change for user 1 using property replace modification");
        ObjectDelta<UserType> delta = prismContext.deltaFor(UserType.class)
                .property(UserType.F_EMAIL_ADDRESS).replace("newer@email.com")
                .asObjectDelta(user1Oid);

        when("modifyObject is called");
        repositoryService.modifyObject(UserType.class, user1Oid, delta.getModifications(), result);

        then("operation is successful");
        assertThatOperationResult(result).isSuccess();

        and("serialized form (fullObject) is updated");
        UserType userObject = repositoryService.getObject(UserType.class, user1Oid, null, result)
                .asObjectable();
        assertThat(userObject.getEmailAddress()).isEqualTo("newer@email.com");

        and("externalized column is set to NULL");
        MUser row = selectObjectByOid(QUser.class, user1Oid);
        assertThat(row.emailAddress).isEqualTo("newer@email.com");
    }

    @Test
    public void test104StringDeleteWithWrongValueDoesNotChangeAnything()
            throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException {
        OperationResult result = createOperationResult();

        given("delta with email delete for user 1 using wrong previous value");
        ObjectDelta<UserType> delta = prismContext.deltaFor(UserType.class)
                // without value it would not be recognized as delete
                .property(UserType.F_EMAIL_ADDRESS).delete("x")
                .asObjectDelta(user1Oid);

        when("modifyObject is called");
        repositoryService.modifyObject(UserType.class, user1Oid, delta.getModifications(), result);

        then("operation is successful");
        assertThatOperationResult(result).isSuccess();

        // Internal note: the delta is filter by prismObject.narrowModifications
        and("serialized form (fullObject) is not changed and previous email is still there");
        UserType userObject = repositoryService.getObject(UserType.class, user1Oid, null, result)
                .asObjectable();
        assertThat(userObject.getEmailAddress()).isEqualTo("newer@email.com");

        and("externalized column is updated");
        MUser row = selectObjectByOid(QUser.class, user1Oid);
        assertThat(row.emailAddress).isEqualTo("newer@email.com");
    }

    @Test
    public void test105StringReplaceWithExistingValueWorksOk()
            throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException {
        OperationResult result = createOperationResult();

        given("delta with email replace for user 1 (email has previous value)");
        ObjectDelta<UserType> delta = prismContext.deltaFor(UserType.class)
                // without value it would not be recognized as delete
                .property(UserType.F_EMAIL_ADDRESS).replace("newest@email.com")
                .asObjectDelta(user1Oid);

        when("modifyObject is called");
        repositoryService.modifyObject(UserType.class, user1Oid, delta.getModifications(), result);

        then("operation is successful");
        assertThatOperationResult(result).isSuccess();

        and("serialized form (fullObject) is changed and email value is replaced");
        UserType userObject = repositoryService.getObject(UserType.class, user1Oid, null, result)
                .asObjectable();
        assertThat(userObject.getEmailAddress()).isEqualTo("newest@email.com");

        and("externalized column is updated");
        MUser row = selectObjectByOid(QUser.class, user1Oid);
        assertThat(row.emailAddress).isEqualTo("newest@email.com");
    }

    @Test
    public void test106StringDeletedUsingTheRightValue()
            throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException {
        OperationResult result = createOperationResult();

        given("delta with email delete for user 1 using valid previous value");
        ObjectDelta<UserType> delta = prismContext.deltaFor(UserType.class)
                // without value it would not be recognized as delete
                .property(UserType.F_EMAIL_ADDRESS).delete("newest@email.com")
                .asObjectDelta(user1Oid);

        when("modifyObject is called");
        repositoryService.modifyObject(UserType.class, user1Oid, delta.getModifications(), result);

        then("operation is successful");
        assertThatOperationResult(result).isSuccess();

        // Internal note: the delta is filter by prismObject.narrowModifications
        and("serialized form (fullObject) is changed and email is gone");
        UserType userObject = repositoryService.getObject(UserType.class, user1Oid, null, result)
                .asObjectable();
        assertThat(userObject.getEmailAddress()).isNull();

        and("externalized column is set to NULL");
        MUser row = selectObjectByOid(QUser.class, user1Oid);
        assertThat(row.emailAddress).isNull();
    }

    @Test
    public void test110ChangeInstantAttribute()
            throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException {
        OperationResult result = createOperationResult();
        MTask originalRow = selectObjectByOid(QTask.class, task1Oid);

        given("delta with last run start timestamp change for task 1 adding value");
        ObjectDelta<TaskType> delta = prismContext.deltaFor(TaskType.class)
                .property(TaskType.F_LAST_RUN_START_TIMESTAMP)
                .add(MiscUtil.asXMLGregorianCalendar(1L))
                .asObjectDelta(task1Oid);

        when("modifyObject is called");
        repositoryService.modifyObject(TaskType.class, task1Oid, delta.getModifications(), result);

        then("operation is successful");
        assertThatOperationResult(result).isSuccess();

        and("serialized form (fullObject) is updated");
        TaskType taskObject = repositoryService.getObject(TaskType.class, task1Oid, null, result)
                .asObjectable();
        assertThat(taskObject.getLastRunStartTimestamp())
                .isEqualTo(MiscUtil.asXMLGregorianCalendar(1L));
        assertThat(taskObject.getVersion()).isEqualTo(String.valueOf(originalRow.version + 1));

        and("externalized column is updated");
        MTask row = selectObjectByOid(QTask.class, task1Oid);
        assertThat(row.version).isEqualTo(originalRow.version + 1);
        assertThat(row.lastRunStartTimestamp).isEqualTo(Instant.ofEpochMilli(1));
    }

    @Test
    public void test111DeleteInstantAttribute()
            throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException {
        OperationResult result = createOperationResult();

        given("delta with last run start timestamp replace to null ('delete') for task 1");
        ObjectDelta<TaskType> delta = prismContext.deltaFor(TaskType.class)
                .property(TaskType.F_LAST_RUN_START_TIMESTAMP).replace()
                .asObjectDelta(task1Oid);

        and("task row previously having the timestamp value");
        MTask originalRow = selectObjectByOid(QTask.class, task1Oid);
        assertThat(originalRow.lastRunStartTimestamp).isNotNull();

        when("modifyObject is called");
        repositoryService.modifyObject(TaskType.class, task1Oid, delta.getModifications(), result);

        then("operation is successful");
        assertThatOperationResult(result).isSuccess();

        and("serialized form (fullObject) is updated and email is gone");
        TaskType taskObject = repositoryService.getObject(TaskType.class, task1Oid, null, result)
                .asObjectable();
        assertThat(taskObject.getLastRunStartTimestamp()).isNull();
        assertThat(taskObject.getVersion()).isEqualTo(String.valueOf(originalRow.version + 1));

        and("externalized column is set to NULL");
        MTask row = selectObjectByOid(QTask.class, task1Oid);
        assertThat(row.version).isEqualTo(originalRow.version + 1);
        assertThat(row.lastRunStartTimestamp).isNull();
    }

    @Test
    public void test115ChangeIntegerAttribute()
            throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException {
        OperationResult result = createOperationResult();
        MService originalRow = selectObjectByOid(QService.class, service1Oid);

        given("delta with display order change for service 1");
        ObjectDelta<ServiceType> delta = prismContext.deltaFor(ServiceType.class)
                .property(ServiceType.F_DISPLAY_ORDER).replace(5)
                .asObjectDelta(service1Oid);

        when("modifyObject is called");
        repositoryService.modifyObject(
                ServiceType.class, service1Oid, delta.getModifications(), result);

        then("operation is successful");
        assertThatOperationResult(result).isSuccess();

        and("serialized form (fullObject) is updated");
        ServiceType serviceObject = repositoryService
                .getObject(ServiceType.class, service1Oid, null, result)
                .asObjectable();
        assertThat(serviceObject.getDisplayOrder()).isEqualTo(5);
        assertThat(serviceObject.getVersion()).isEqualTo(String.valueOf(originalRow.version + 1));

        and("externalized column is updated");
        MService row = selectObjectByOid(QService.class, service1Oid);
        assertThat(row.version).isEqualTo(originalRow.version + 1);
        assertThat(row.displayOrder).isEqualTo(5);
    }

    @Test
    public void test116DeleteIntegerAttribute()
            throws ObjectAlreadyExistsException, ObjectNotFoundException, SchemaException {
        OperationResult result = createOperationResult();

        given("delta with display order replace to null for service 1");
        ObjectDelta<ServiceType> delta = prismContext.deltaFor(ServiceType.class)
                .property(ServiceType.F_DISPLAY_ORDER).replace()
                .asObjectDelta(service1Oid);

        and("service row previously having the display order value");
        MService originalRow = selectObjectByOid(QService.class, service1Oid);
        assertThat(originalRow.displayOrder).isNotNull();

        when("modifyObject is called");
        repositoryService.modifyObject(ServiceType.class, service1Oid, delta.getModifications(), result);

        then("operation is successful");
        assertThatOperationResult(result).isSuccess();

        and("serialized form (fullObject) is updated and display order is gone");
        ServiceType serviceObject = repositoryService
                .getObject(ServiceType.class, service1Oid, null, result)
                .asObjectable();
        assertThat(serviceObject.getDisplayOrder()).isNull();
        assertThat(serviceObject.getVersion()).isEqualTo(String.valueOf(originalRow.version + 1));

        and("externalized column is set to NULL");
        MService row = selectObjectByOid(QService.class, service1Oid);
        assertThat(row.version).isEqualTo(originalRow.version + 1);
        assertThat(row.displayOrder).isNull();
    }

    @Test
    public void test900ModificationsMustNotBeNull() {
        OperationResult result = createOperationResult();

        given("null modifications");

        expect("modifyObject throws exception");
        //noinspection ConstantConditions
        Assertions.assertThatThrownBy(() ->
                repositoryService.modifyObject(UserType.class, user1Oid, null, result))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Modifications must not be null.");
    }
}