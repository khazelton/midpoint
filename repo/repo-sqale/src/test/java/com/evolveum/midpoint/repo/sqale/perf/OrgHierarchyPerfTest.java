/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.perf;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Arrays;

import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.repo.sqale.SqaleRepoBaseTest;
import com.evolveum.midpoint.repo.sqale.qmodel.focus.QUser;
import com.evolveum.midpoint.repo.sqale.qmodel.org.QOrg;
import com.evolveum.midpoint.repo.sqale.qmodel.org.QOrgClosure;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.MetadataType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OrgType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.query_3.QueryType;

// TODO experimental, maybe temporary
public class OrgHierarchyPerfTest extends SqaleRepoBaseTest {

    public static final SecureRandom RANDOM = new SecureRandom();

    @BeforeClass
    public void initObjects() throws Exception {
        OperationResult result = createOperationResult();

        createOrgsFor(null, 5, 6, result);

        assertThatOperationResult(result).isSuccess();
    }

    private void createOrgsFor(
            OrgType parent, int levels, int typicalCountPerLevel, OperationResult result)
            throws SchemaException, ObjectAlreadyExistsException {
        if (levels == 0) {
            createUsersFor(parent, typicalCountPerLevel, result);
            return;
        }

        // for roots we want the exact number, so it does not vary wildly
        int orgs = parent != null
                ? RANDOM.nextInt(typicalCountPerLevel) + typicalCountPerLevel / 2 + 1
                : typicalCountPerLevel;
        for (int i = 1; i <= orgs; i++) {
            // names use only chars that are preserved by normalization to avoid collision
            String name = parent != null ? parent.getName() + "x" + i : "org" + i;
            OrgType org = new OrgType(prismContext).name(name);
            if (parent != null) {
                org.parentOrgRef(parent.getOid(), OrgType.COMPLEX_TYPE);
            }

            repositoryService.addObject(org.asPrismObject(), null, result);
            createOrgsFor(org, levels - 1, typicalCountPerLevel, result);
        }
    }

    private void createUsersFor(OrgType parent, int typicalCountPerLevel, OperationResult result)
            throws SchemaException, ObjectAlreadyExistsException {
        int users = RANDOM.nextInt(typicalCountPerLevel) + typicalCountPerLevel / 2 + 1;
        for (int i = 1; i <= users; i++) {
            repositoryService.addObject(
                    new UserType(prismContext).name("user" + parent.getName() + "v" + i)
                            .metadata(new MetadataType()
                                    .createChannel("create-channel")
                                    .createTimestamp(MiscUtil.asXMLGregorianCalendar(System.currentTimeMillis())))
                            .parentOrgRef(parent.getOid(), OrgType.COMPLEX_TYPE)
                            .asPrismObject(),
                    null, result);
        }
    }

    @Test
    // TODO
    public void test100Xxx() throws Exception {
        when("...");
//        OperationResult operationResult = createOperationResult();
//        SearchResultList<ObjectType> result = searchObjects(ObjectType.class,
//                prismContext.queryFor(ObjectType.class)
//                        .build(),
//                operationResult);

        then("...");
        System.out.println("Orgs: " + count(QOrg.CLASS));
        System.out.println("Org closure: " + count(new QOrgClosure()));
        System.out.println("Users: " + count(QUser.class));
    }

    // support methods
    @SafeVarargs
    @NotNull
    private <T extends ObjectType> SearchResultList<T> searchObjects(
            @NotNull Class<T> type,
            ObjectQuery query,
            OperationResult operationResult,
            SelectorOptions<GetOperationOptions>... selectorOptions)
            throws SchemaException {
        QueryType queryType = prismContext.getQueryConverter().createQueryType(query);
        String serializedQuery = prismContext.xmlSerializer().serializeAnyData(
                queryType, SchemaConstants.MODEL_EXTENSION_OBJECT_QUERY);
        display("QUERY: " + serializedQuery);

        // sanity check if it's re-parsable
        assertThat(prismContext.parserFor(serializedQuery).parseRealValue(QueryType.class))
                .isNotNull();
        return repositoryService.searchObjects(
                type,
                query,
                Arrays.asList(selectorOptions),
                operationResult)
                .map(p -> p.asObjectable());
    }
}