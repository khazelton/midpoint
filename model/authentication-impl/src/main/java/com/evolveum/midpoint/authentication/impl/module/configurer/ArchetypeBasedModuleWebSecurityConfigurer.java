/*
 * Copyright (c) 2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.authentication.impl.module.configurer;

import com.evolveum.midpoint.authentication.api.util.AuthUtil;
import com.evolveum.midpoint.authentication.impl.filter.CorrelationAttributesVerificationAuthenticationFilter;
import com.evolveum.midpoint.authentication.impl.filter.configurers.MidpointAttributeConfigurer;
import com.evolveum.midpoint.authentication.impl.handler.MidPointAuthenticationSuccessHandler;
import com.evolveum.midpoint.authentication.impl.handler.MidpointAuthenticationFailureHandler;
import com.evolveum.midpoint.authentication.impl.module.configuration.ArchetypeBasedModuleWebSecurityConfiguration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

public class ArchetypeBasedModuleWebSecurityConfigurer<C extends ArchetypeBasedModuleWebSecurityConfiguration>
        extends ModuleWebSecurityConfigurer<C> {

    public ArchetypeBasedModuleWebSecurityConfigurer(C configuration) {
        super(configuration);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        CorrelationAttributesVerificationAuthenticationFilter verificationFilter =
                new CorrelationAttributesVerificationAuthenticationFilter();
        http.securityMatcher(AuthUtil.stripEndingSlashes(getPrefix()) + "/**");
        getOrApply(http, new MidpointAttributeConfigurer<>(verificationFilter))
                .loginPage("/loginRecovery")
                .loginProcessingUrl(AuthUtil.stripEndingSlashes(getPrefix()) + "/spring_security_login")
                .failureHandler(new MidpointAuthenticationFailureHandler())
                .successHandler(getObjectPostProcessor().postProcess(
                        new MidPointAuthenticationSuccessHandler())).permitAll();
//        getOrApply(http, new MidpointExceptionHandlingConfigurer<>())
//                .authenticationEntryPoint(new WicketLoginUrlAuthenticationEntryPoint("/loginRecovery"));

        http.logout().clearAuthentication(true)
                .logoutRequestMatcher(getLogoutMatcher(http, getPrefix() +"/logout"))
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(createLogoutHandler());

        http.addFilterBefore(verificationFilter, CorrelationAttributesVerificationAuthenticationFilter.class);
    }

}
