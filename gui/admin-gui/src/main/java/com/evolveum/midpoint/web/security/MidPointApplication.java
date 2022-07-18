/*
 * Copyright (C) 2010-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.security;

import com.evolveum.midpoint.authentication.api.authorization.DescriptorLoader;
import com.evolveum.midpoint.authentication.api.util.AuthUtil;
import com.evolveum.midpoint.cases.api.CaseManager;
import com.evolveum.midpoint.common.Clock;
import com.evolveum.midpoint.common.LocalizationService;
import com.evolveum.midpoint.common.configuration.api.MidpointConfiguration;
import com.evolveum.midpoint.gui.api.page.PageAdminLTE;
import com.evolveum.midpoint.gui.api.util.MidPointApplicationConfiguration;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.gui.impl.converter.CleanupPoliciesTypeConverter;
import com.evolveum.midpoint.gui.impl.converter.DurationConverter;
import com.evolveum.midpoint.gui.impl.converter.PolyStringConverter;
import com.evolveum.midpoint.gui.impl.converter.QueryTypeConverter;
import com.evolveum.midpoint.gui.impl.page.login.PageLogin;
import com.evolveum.midpoint.model.api.*;
import com.evolveum.midpoint.repo.common.SystemObjectCache;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.prism.match.MatchingRuleRegistry;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.repo.api.*;
import com.evolveum.midpoint.repo.common.expression.ExpressionFactory;
import com.evolveum.midpoint.schema.RelationRegistry;
import com.evolveum.midpoint.schema.SchemaService;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.SchemaDebugUtil;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.security.api.SecurityContextManager;
import com.evolveum.midpoint.security.enforcer.api.SecurityEnforcer;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.AsyncWebProcessManager;
import com.evolveum.midpoint.web.application.PageMounter;
import com.evolveum.midpoint.web.page.admin.home.PageDashboardInfo;
import com.evolveum.midpoint.web.page.error.*;
import com.evolveum.midpoint.web.page.self.PagePostAuthentication;
import com.evolveum.midpoint.web.page.self.PageSelfDashboard;
import com.evolveum.midpoint.web.resource.img.ImgResources;
import com.evolveum.midpoint.web.security.util.SecurityUtils;
import com.evolveum.midpoint.web.util.MidPointResourceStreamLocator;
import com.evolveum.midpoint.web.util.MidPointStringResourceLoader;
import com.evolveum.midpoint.web.util.SchrodingerComponentInitListener;
import com.evolveum.midpoint.xml.ns._public.common.common_3.CleanupPoliciesType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.DeploymentInformationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemConfigurationType;
import com.evolveum.prism.xml.ns._public.query_3.QueryType;

import org.apache.commons.configuration2.Configuration;
import org.apache.wicket.*;
import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.attributes.AjaxRequestAttributes;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.form.AjaxFormSubmitBehavior;
import org.apache.wicket.authroles.authentication.AbstractAuthenticatedWebSession;
import org.apache.wicket.authroles.authentication.AuthenticatedWebApplication;
import org.apache.wicket.core.request.mapper.MountedMapper;
import org.apache.wicket.core.util.objects.checker.CheckingObjectOutputStream;
import org.apache.wicket.core.util.objects.checker.IObjectChecker;
import org.apache.wicket.core.util.objects.checker.ObjectSerializationChecker;
import org.apache.wicket.core.util.resource.locator.IResourceStreamLocator;
import org.apache.wicket.core.util.resource.locator.caching.CachingResourceStreamLocator;
import org.apache.wicket.devutils.inspector.InspectorPage;
import org.apache.wicket.devutils.inspector.LiveSessionsPage;
import org.apache.wicket.devutils.pagestore.PageStorePage;
import org.apache.wicket.markup.head.PriorityFirstComparator;
import org.apache.wicket.markup.html.SecurePackageResourceGuard;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.mapper.parameter.PageParametersEncoder;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.request.resource.SharedResourceReference;
import org.apache.wicket.resource.loader.IStringResourceLoader;
import org.apache.wicket.serialize.java.JavaSerializer;
import org.apache.wicket.settings.ApplicationSettings;
import org.apache.wicket.settings.ResourceSettings;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.web.csrf.CsrfToken;

import javax.servlet.ServletContext;
import javax.xml.datatype.Duration;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author lazyman
 */
public class MidPointApplication extends AuthenticatedWebApplication implements ApplicationContextAware {

    public static final List<LocaleDescriptor> AVAILABLE_LOCALES;

    private static final String LOCALIZATION_DESCRIPTOR = "localization/locale.properties";

    private static final String PROP_NAME = ".name";
    private static final String PROP_FLAG = ".flag";
    private static final String PROP_DEFAULT = ".default";

    private static final Trace LOGGER = TraceManager.getTrace(MidPointApplication.class);

    static {
        SchemaDebugUtil.initialize();
    }

    static {
        String midpointHome = System.getProperty(MidpointConfiguration.MIDPOINT_HOME_PROPERTY);
        File file = new File(midpointHome, LOCALIZATION_DESCRIPTOR);

        Resource[] localeDescriptorResources = new Resource[] {
                new FileSystemResource(file),
                new ClassPathResource(LOCALIZATION_DESCRIPTOR)
        };

        List<LocaleDescriptor> locales = new ArrayList<>();
        for (Resource resource : localeDescriptorResources) {
            if (!resource.isReadable()) {
                continue;
            }

            try {
                LOGGER.debug("Found localization descriptor {}.", resource.getURL());
                locales = loadLocaleDescriptors(resource);

                break;
            } catch (Exception ex) {
                LoggingUtils.logUnexpectedException(LOGGER, "Couldn't load localization", ex);
            }
        }

        Collections.sort(locales);

        AVAILABLE_LOCALES = Collections.unmodifiableList(locales);
    }

    @Autowired private ModelService model;
    @Autowired private ModelInteractionService modelInteractionService;
    @Autowired private RelationRegistry relationRegistry;
    @Autowired private TaskService taskService;
    @Autowired private PrismContext prismContext;
    @Autowired private SchemaService schemaService;
    @Autowired private ExpressionFactory expressionFactory;
    @Autowired private TaskManager taskManager;
    @Autowired private ModelAuditService auditService;
    @Autowired private SqlPerformanceMonitorsCollection performanceMonitorsCollection; // temporary
    @Autowired private RepositoryService repositoryService; // temporary
    @Autowired private CacheRegistry cacheRegistry;
    @Autowired private CaseService caseService;
    @Autowired private CaseManager caseManager;
    @Autowired private MidpointConfiguration configuration;
    @Autowired private Protector protector;
    @Autowired private MatchingRuleRegistry matchingRuleRegistry;
    @Autowired private SecurityEnforcer securityEnforcer;
    @Autowired private SecurityContextManager securityContextManager;
    @Autowired private SystemObjectCache systemObjectCache;
    @Autowired private LocalizationService localizationService;
    @Autowired private AsyncWebProcessManager asyncWebProcessManager;
    @Autowired private ApplicationContext applicationContext;
    @Autowired private SystemConfigurationChangeDispatcher systemConfigurationChangeDispatcher;
    @Autowired private Clock clock;
    @Autowired private AccessCertificationService certificationService;
    @Autowired(required = false) private List<WicketConfigurator> wicketConfigurators = new ArrayList<>();
    @Autowired @Qualifier("descriptorLoader") private DescriptorLoader descriptorLoader;
    @Value("${midpoint.additionalPackagesToScan:}") private String additionalPackagesToScan;

    private WebApplicationConfiguration webApplicationConfiguration;

    private DeploymentInformationType deploymentInfo;

    public static final String MOUNT_INTERNAL_SERVER_ERROR = "/error";
    public static final String MOUNT_UNAUTHORIZED_ERROR = "/error/401";
    public static final String MOUNT_FORBIDDEN_ERROR = "/error/403";
    public static final String MOUNT_NOT_FOUND_ERROR = "/error/404";
    public static final String MOUNT_GONE_ERROR = "/error/410";

    @Override
    public Class<? extends PageAdminLTE> getHomePage() {
        if (AuthUtil.isPostAuthenticationEnabled(getTaskManager(), getModelInteractionService())) {
            return PagePostAuthentication.class;
        }

        if (WebComponentUtil.isAuthorized(AuthorizationConstants.AUTZ_UI_DASHBOARD_URL,
                AuthorizationConstants.AUTZ_UI_HOME_ALL_URL)) {
            return PageDashboardInfo.class;
        } else {
            return PageSelfDashboard.class;
        }
    }

    @Override
    public void init() {
        super.init();

        getCspSettings().blocking().disabled();

        // This is needed for wicket to work correctly. Also jQuery version in webjars should match AdminLTE jQuery version.
        // We'll try to use npm/webpack to create this jquery resource directly, without webjars [todo lazyman]
        getJavaScriptLibrarySettings().setJQueryReference(
                new PackageResourceReference(MidPointApplication.class, "../../../../../META-INF/resources/webjars/jquery/3.6.0/jquery.min.js"));

        getComponentInstantiationListeners().add(new SpringComponentInjector(this, applicationContext, true));

        systemConfigurationChangeDispatcher.registerListener(new DeploymentInformationChangeListener(this));
        SystemConfigurationType config = getSystemConfigurationIfAvailable();
        if (config != null) {
            deploymentInfo = config.getDeploymentInformation();
        }

        ResourceSettings resourceSettings = getResourceSettings();
        resourceSettings.setParentFolderPlaceholder("$-$");
        resourceSettings.setHeaderItemComparator(new PriorityFirstComparator(true));
        SecurePackageResourceGuard guard = (SecurePackageResourceGuard) resourceSettings.getPackageResourceGuard();
        guard.addPattern("+*.woff2");

        List<IStringResourceLoader> resourceLoaders = resourceSettings.getStringResourceLoaders();
        resourceLoaders.add(0, new MidPointStringResourceLoader(localizationService));

        IResourceStreamLocator locator = new CachingResourceStreamLocator(
                new MidPointResourceStreamLocator(resourceSettings.getResourceFinders()));
        resourceSettings.setResourceStreamLocator(locator);

        resourceSettings.setThrowExceptionOnMissingResource(false);
        getMarkupSettings().setStripWicketTags(true);
        getMarkupSettings().setStripComments(true);

        if (RuntimeConfigurationType.DEVELOPMENT.equals(getConfigurationType())) {
            getDebugSettings().setAjaxDebugModeEnabled(true);
            getDebugSettings().setDevelopmentUtilitiesEnabled(true);
            initializeDevelopmentSerializers();
            mount(new MountedMapper("/inspector", InspectorPage.class, new PageParametersEncoder()));
            mount(new MountedMapper("/liveSession", LiveSessionsPage.class, new PageParametersEncoder()));
            mount(new MountedMapper("/pageStore", PageStorePage.class, new PageParametersEncoder()));
        }

        //pretty url for resources (e.g. images)
        mountFiles(ImgResources.BASE_PATH, ImgResources.class);

        //exception handling an error pages
        ApplicationSettings appSettings = getApplicationSettings();
        appSettings.setAccessDeniedPage(PageError401.class);
        appSettings.setInternalErrorPage(PageError.class);
        appSettings.setPageExpiredErrorPage(PageError.class);

        mount(new MountedMapper(MOUNT_INTERNAL_SERVER_ERROR, PageError.class, new PageParametersEncoder()));
        mount(new MountedMapper(MOUNT_UNAUTHORIZED_ERROR, PageError401.class, new PageParametersEncoder()));
        mount(new MountedMapper(MOUNT_FORBIDDEN_ERROR, PageError403.class, new PageParametersEncoder()));
        mount(new MountedMapper(MOUNT_NOT_FOUND_ERROR, PageError404.class, new PageParametersEncoder()));
        mount(new MountedMapper(MOUNT_GONE_ERROR, PageError410.class, new PageParametersEncoder()));

        getRequestCycleListeners().add(new LoggingRequestCycleListener(this));

        getAjaxRequestTargetListeners().add(new AjaxRequestTarget.IListener() {

            @Override
            public void updateAjaxAttributes(AbstractDefaultAjaxBehavior behavior, AjaxRequestAttributes attributes) {
                // check whether behavior will use POST method, if not then don't put CSRF token there
                if (!isPostMethodTypeBehavior(behavior, attributes)) {
                    return;
                }

                CsrfToken csrfToken = SecurityUtils.getCsrfToken();
                if (csrfToken == null) {
                    return;
                }

                String parameterName = csrfToken.getParameterName();
                String value = csrfToken.getToken();

                attributes.getExtraParameters().put(parameterName, value);

            }

        });

        getSessionListeners().add((ISessionListener) asyncWebProcessManager);

        //descriptor loader, used for customization
        new PageMounter().loadData(this);
        descriptorLoader.loadData();

        if (applicationContext != null) {

            Map<String, MidPointApplicationConfiguration> map =
                    applicationContext.getBeansOfType(MidPointApplicationConfiguration.class);
            if (map != null) {
                map.forEach((key, value) -> value.init(this));
            }
        }

        // for schrodinger selenide library
        initializeSchrodinger();

        ServletContext servletContext = getServletContext();
        if (servletContext != null) {
            taskManager.setWebContextPath(servletContext.getContextPath());
        }

        // Additional wicket configuration
        wicketConfigurators.forEach(c -> c.configure(this));

        // default select2 css/js should not be attached via wicket resources. It's already embedded in vendors js/css
        org.wicketstuff.select2.ApplicationSettings settings = org.wicketstuff.select2.ApplicationSettings.get();
        settings.setIncludeJavascriptFull(false);
        settings.setIncludeJavascript(false);
        settings.setIncludeCss(false);
    }

    public DeploymentInformationType getDeploymentInfo() {
        return deploymentInfo;
    }

    private void initializeSchrodinger() {
        if (applicationContext == null) {
            return;
        }

        Environment environment = applicationContext.getEnvironment();
        String value = environment.getProperty(MidpointConfiguration.MIDPOINT_SCHRODINGER_PROPERTY);
        boolean enabled = Boolean.parseBoolean(value);

        if (enabled) {
            LOGGER.info("Schrodinger plugin enabled");
            getComponentInitializationListeners().add(new SchrodingerComponentInitListener());
        }
    }

    private boolean isPostMethodTypeBehavior(AbstractDefaultAjaxBehavior behavior, AjaxRequestAttributes attributes) {
        if (behavior instanceof AjaxFormComponentUpdatingBehavior) {
            // these also uses POST, but they set it after this method is called
            return true;
        }

        if (behavior instanceof AjaxFormSubmitBehavior) {
            AjaxFormSubmitBehavior fb = (AjaxFormSubmitBehavior) behavior;
            Form form = fb.getForm();
            String formMethod = form.getMarkupAttributes().getString("method");
            if (formMethod == null || "POST".equalsIgnoreCase(formMethod) || form.getRootForm().isMultiPart()) {
                // this will also use POST
                return true;
            }
        }

        return AjaxRequestAttributes.Method.POST.equals(attributes.getMethod());
    }

    private static List<LocaleDescriptor> loadLocaleDescriptors(Resource resource) throws IOException {
        List<LocaleDescriptor> locales = new ArrayList<>();

        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            properties.load(reader);

            Map<String, Map<String, String>> localeMap = new HashMap<>();
            //noinspection unchecked,rawtypes
            Set<String> keys = (Set) properties.keySet();
            for (String key : keys) {
                String[] array = key.split("\\.");
                if (array.length != 2) {
                    continue;
                }

                String locale = array[0];
                Map<String, String> map = localeMap.computeIfAbsent(locale, k -> new HashMap<>());

                map.put(key, properties.getProperty(key));
            }

            for (String key : localeMap.keySet()) {
                Map<String, String> localeDefinition = localeMap.get(key);
                if (!localeDefinition.containsKey(key + PROP_NAME)
                        || !localeDefinition.containsKey(key + PROP_FLAG)) {
                    continue;
                }

                LocaleDescriptor descriptor = new LocaleDescriptor(
                        localeDefinition.get(key + PROP_NAME),
                        localeDefinition.get(key + PROP_FLAG),
                        localeDefinition.get(key + PROP_DEFAULT),
                        WebComponentUtil.getLocaleFromString(key)
                );
                locales.add(descriptor);
            }
        }

        return locales;
    }

    @Override
    protected IConverterLocator newConverterLocator() {
        ConverterLocator locator = new ConverterLocator();

        locator.set(PolyString.class, new PolyStringConverter());
        locator.set(Duration.class, new DurationConverter());
        locator.set(QueryType.class, new QueryTypeConverter(prismContext));
        locator.set(CleanupPoliciesType.class, new CleanupPoliciesTypeConverter(prismContext));
        return locator;
    }

    private void initializeDevelopmentSerializers() {
        JavaSerializer javaSerializer = new JavaSerializer(getApplicationKey()) {
            @Override
            protected ObjectOutputStream newObjectOutputStream(OutputStream out) throws IOException {
                IObjectChecker checker1 = new MidPointObjectChecker();
//                IObjectChecker checker2 = new NotDetachedModelChecker();
//                IObjectChecker sessionChecker = new SessionChecker();
                IObjectChecker checker3 = new ObjectSerializationChecker();

                return new CheckingObjectOutputStream(out, checker1, checker3);
            }
        };
        getFrameworkSettings().setSerializer(javaSerializer);

    }

    private void mountFiles(String path, Class<?> clazz) {
        try {
            String packagePath = clazz.getPackage().getName().replace('.', '/');

            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] pngResources = resolver.getResources("classpath:" + packagePath + "/*.png");
            Resource[] gifResources = resolver.getResources("classpath:" + packagePath + "/*.gif");
            List<Resource> allResources = new ArrayList<>(Arrays.asList(pngResources));
            allResources.addAll(Arrays.asList(gifResources));

            for (Resource resource : allResources) {
                URI uri = resource.getURI();
                File file = new File(uri.toString());
                mountResource(path + "/" + file.getName(), new SharedResourceReference(clazz, file.getName()));
            }
        } catch (Exception ex) {
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't mount files", ex);
        }
    }

    public WebApplicationConfiguration getWebApplicationConfiguration() {
        if (webApplicationConfiguration == null) {
            Configuration config = configuration.getConfiguration(MidpointConfiguration.WEB_APP_CONFIGURATION);
            webApplicationConfiguration = new WebApplicationConfiguration(config);
        }
        return webApplicationConfiguration;
    }

    public SecurityEnforcer getSecurityEnforcer() {
        return securityEnforcer;
    }

    public ModelService getModel() {
        return model;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public LocalizationService getLocalizationService() {
        return localizationService;
    }

    public ModelAuditService getAuditService() {
        return auditService;
    }

    public SqlPerformanceMonitorsCollection getSqlPerformanceMonitorsCollection() {
        return performanceMonitorsCollection;
    }

    public RepositoryService getRepositoryService() {
        return repositoryService;
    }

    public CacheRegistry getCacheRegistry() {
        return cacheRegistry;
    }

    public TaskService getTaskService() {
        return taskService;
    }

    public PrismContext getPrismContext() {
        return prismContext;
    }

    public SchemaService getSchemaService() {
        return schemaService;
    }

    public ExpressionFactory getExpressionFactory() {
        return expressionFactory;
    }

    public Protector getProtector() {
        return protector;
    }

    @Override
    protected Class<? extends WebPage> getSignInPageClass() {
        return PageLogin.class;
    }

    @Override
    protected Class<? extends AbstractAuthenticatedWebSession> getWebSessionClass() {
        return MidPointAuthWebSession.class;
    }

    public CaseService getWorkflowService() {
        return caseService;
    }

    public CaseManager getWorkflowManager() {
        return caseManager;
    }

    public ModelInteractionService getModelInteractionService() {
        return modelInteractionService;
    }

    public RelationRegistry getRelationRegistry() {
        return relationRegistry;
    }

    public AccessCertificationService getCertificationService() {
        return certificationService;
    }

    public static boolean containsLocale(Locale locale) {
        if (locale == null) {
            return false;
        }

        for (LocaleDescriptor descriptor : AVAILABLE_LOCALES) {
            if (locale.equals(descriptor.getLocale())) {
                return true;
            }
        }

        return false;
    }

    public static Locale getDefaultLocale() {
        for (LocaleDescriptor descriptor : AVAILABLE_LOCALES) {
            if (descriptor.isDefault()) {
                return descriptor.getLocale();
            }
        }

        return new Locale("en", "US");
    }

    public MatchingRuleRegistry getMatchingRuleRegistry() {
        return matchingRuleRegistry;
    }

    public SystemConfigurationType getSystemConfiguration() throws SchemaException {
        PrismObject<SystemConfigurationType> config = systemObjectCache.getSystemConfiguration(new OperationResult("dummy"));
        return config != null ? config.asObjectable() : null;
    }

    public SystemConfigurationType getSystemConfigurationIfAvailable() {
        try {
            PrismObject<SystemConfigurationType> config = systemObjectCache.getSystemConfiguration(new OperationResult("dummy"));
            return config != null ? config.asObjectable() : null;
        } catch (SchemaException e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't retrieve system configuration", e);
            return null;
        }
    }

    /**
     * Returns customizable comma-separated list of additional packages to scan.
     * This can be set with `midpoint.additionalPackagesToScan` property.
     */
    public String getAdditionalPackagesToScan() {
        return additionalPackagesToScan;
    }

    public static MidPointApplication get() {
        return (MidPointApplication) WebApplication.get();
    }

    public Task createSimpleTask(String operation) {
        MidPointPrincipal user = AuthUtil.getPrincipalUser();
        if (user == null) {
            throw new RestartResponseException(PageLogin.class);
        }
        return WebModelServiceUtils.createSimpleTask(operation, user.getFocus().asPrismObject(), getTaskManager());
    }

    public AsyncWebProcessManager getAsyncWebProcessManager() {
        return asyncWebProcessManager;
    }

    public SecurityContextManager getSecurityContextManager() {
        return securityContextManager;
    }

    private static class DeploymentInformationChangeListener implements SystemConfigurationChangeListener {

        private final MidPointApplication application;

        public DeploymentInformationChangeListener(MidPointApplication application) {
            this.application = application;
        }

        @Override
        public void update(@Nullable SystemConfigurationType value) {
            application.deploymentInfo = value != null ? value.getDeploymentInformation() : null;
        }
    }

    /* (non-Javadoc)
     * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
     */
    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public Clock getClock() {
        return clock;
    }
}
