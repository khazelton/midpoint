package com.evolveum.midpoint.ninja;

import java.io.IOException;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.testng.annotations.BeforeClass;

import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.test.util.AbstractSpringTest;
import com.evolveum.midpoint.test.util.InfraTestMixin;

public abstract class NinjaSpringTest extends AbstractSpringTest implements InfraTestMixin, NinjaTestMixin {

    @Qualifier("repositoryService")
    @Autowired
    protected RepositoryService repository;

    @Autowired
    protected DataSource repositoryDataSource;

    @Autowired
    protected ApplicationContext applicationContext;

    @BeforeClass
    public void beforeClass() throws IOException {
        setupMidpointHome();

        clearMidpointTestDatabase(applicationContext);
    }
}