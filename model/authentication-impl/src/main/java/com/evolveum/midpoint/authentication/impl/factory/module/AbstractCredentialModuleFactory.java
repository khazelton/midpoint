/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.authentication.impl.factory.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.evolveum.midpoint.authentication.api.config.ModuleAuthentication;

import jakarta.servlet.ServletRequest;

import com.evolveum.midpoint.authentication.impl.module.configurer.ModuleWebSecurityConfigurer;
import com.evolveum.midpoint.authentication.impl.util.AuthModuleImpl;
import com.evolveum.midpoint.authentication.api.AuthModule;
import com.evolveum.midpoint.authentication.api.AuthenticationChannel;
import com.evolveum.midpoint.authentication.impl.module.authentication.ModuleAuthenticationImpl;
import com.evolveum.midpoint.authentication.api.ModuleWebSecurityConfiguration;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

/**
 * @author skublik
 */
public abstract class AbstractCredentialModuleFactory<
        C extends ModuleWebSecurityConfiguration,
        CA extends ModuleWebSecurityConfigurer<C>,
        MT extends AbstractAuthenticationModuleType,
        MA extends ModuleAuthentication>
        extends AbstractModuleFactory<MT, MA> {

    private static final Trace LOGGER = TraceManager.getTrace(AbstractCredentialModuleFactory.class);

    @Override
    public abstract boolean match(AbstractAuthenticationModuleType moduleType, AuthenticationChannel authenticationChannel);

    @Override
    public AuthModule<MA> createModuleFilter(
            MT moduleType,
            String sequenceSuffix,
            ServletRequest request,
            Map<Class<?>, Object> sharedObjects,
            AuthenticationModulesType authenticationsPolicy,
            CredentialsPolicyType credentialPolicy,
            AuthenticationChannel authenticationChannel,
            AuthenticationSequenceModuleType necessity)

            throws Exception {

        if (!(moduleType instanceof AbstractCredentialAuthenticationModuleType)) {
            LOGGER.error("This factory supports only AbstractCredentialAuthenticationModuleType, but moduleType is " + moduleType);
            return null;
        }

        isSupportedChannel(authenticationChannel);

//        C configuration = createConfiguration().init();

        C configuration = createConfiguration(moduleType, sequenceSuffix, authenticationChannel);

        configuration.addAuthenticationProvider(
                getProvider((AbstractCredentialAuthenticationModuleType) moduleType, credentialPolicy));

        MA moduleAuthentication = createEmptyModuleAuthentication(moduleType, configuration, necessity);
        moduleAuthentication.setFocusType(moduleType.getFocusType());


        CA module = createModule(configuration);
        module.setObjectPostProcessor(getObjectObjectPostProcessor());

        HttpSecurity http = getNewHttpSecurity(module);
        setSharedObjects(http, sharedObjects);
        SecurityFilterChain filter = http.build();


        return AuthModuleImpl.build(filter, configuration, moduleAuthentication);
    }



    protected AuthenticationProvider getProvider(
            AbstractCredentialAuthenticationModuleType moduleType,
            CredentialsPolicyType credentialsPolicy) {
        CredentialPolicyType usedPolicy = null;
        String credentialName = moduleType.getCredentialName();

        List<CredentialPolicyType> credentialPolicies = new ArrayList<>();
        if (credentialsPolicy != null) {
            credentialPolicies.add(credentialsPolicy.getPassword());
            credentialPolicies.add(credentialsPolicy.getSecurityQuestions());
            credentialPolicies.addAll(credentialsPolicy.getNonce());
        }

        for (CredentialPolicyType processedPolicy : credentialPolicies) {
            if (processedPolicy != null) {
                if (StringUtils.isNotBlank(credentialName)) {
                    if (credentialName.equals(processedPolicy.getName())) {
                        usedPolicy = processedPolicy;
                    }
                } else if (supportedClass() != null && processedPolicy.getClass().isAssignableFrom(supportedClass())) {
                    usedPolicy = processedPolicy;
                }
            }
        }
        if (usedPolicy == null && (PasswordCredentialsPolicyType.class.equals(supportedClass()) || supportedClass() == null)) {
            return getObjectObjectPostProcessor().postProcess(createProvider(null));
        }
        if (usedPolicy == null) {
            String message = StringUtils.isBlank(credentialName)
                    ? ("Couldn't find credential for module " + moduleType)
                    : ("Couldn't find credential with name " + credentialName);
            IllegalArgumentException e = new IllegalArgumentException(message);
            LOGGER.error(message);
            throw e;
        }

        if (!usedPolicy.getClass().equals(supportedClass())) {
            String moduleIdentifier = StringUtils.isNotEmpty(moduleType.getIdentifier()) ? moduleType.getIdentifier() : moduleType.getName();
            String message = "Module " + moduleIdentifier + "support only " + supportedClass() + " type of credential";
            IllegalArgumentException e = new IllegalArgumentException(message);
            LOGGER.error(message);
            throw e;
        }

        return getObjectObjectPostProcessor().postProcess(createProvider(usedPolicy));
    }

    private String getCredentialAuthModuleIdentifier(AbstractCredentialAuthenticationModuleType module) {
        return StringUtils.isNotEmpty(module.getIdentifier()) ? module.getIdentifier() : module.getName();
    }

    protected abstract MA createEmptyModuleAuthentication(
            MT moduleType, C configuration, AuthenticationSequenceModuleType sequenceModule);

    @Deprecated
    protected abstract C createConfiguration(MT moduleType, String prefixOfSequence, AuthenticationChannel authenticationChannel);

//    protected abstract C createConfiguration();

    protected abstract CA createModule(C configuration);

    protected abstract AuthenticationProvider createProvider(CredentialPolicyType usedPolicy);

    protected abstract Class<? extends CredentialPolicyType> supportedClass();
}
