package com.evolveum.midpoint.ninja.impl;

/**
 * Represents different levels of Spring application context initialization for midpoint.
 * Each ninja action requires different levels of midpoint being initialized, starting from {@link NinjaApplicationContextLevel#NONE}
 * up to {@link  NinjaApplicationContextLevel#FULL_REPOSITORY} with full repository/audit initialization.
 *
 * @author Viliam Repan
 */
public enum NinjaApplicationContextLevel {

    NONE(new String[0]),

    STARTUP_CONFIGURATION(new String[] {
            "classpath:ctx-configuration-10-system-init.xml"
    }),

    NO_REPOSITORY(new String[] {
            "classpath:ctx-common.xml",
            "classpath:ctx-configuration-no-repo.xml"
    }),

    FULL_REPOSITORY(new String[] {
            "classpath:ctx-common.xml",
            "classpath:ctx-configuration.xml",
            "classpath*:ctx-repository.xml",
            "classpath:ctx-repo-cache.xml",
            "classpath:ctx-audit.xml"
    });

    public final String[] contexts;

    NinjaApplicationContextLevel(String[] contexts) {
        this.contexts = contexts;
    }

    public boolean containsPrismInitialization() {
        return NO_REPOSITORY.equals(this) || FULL_REPOSITORY.equals(this);
    }
}
