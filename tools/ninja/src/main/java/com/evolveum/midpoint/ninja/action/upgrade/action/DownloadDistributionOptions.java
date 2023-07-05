package com.evolveum.midpoint.ninja.action.upgrade.action;

import java.io.File;

import com.beust.jcommander.Parameter;

import com.evolveum.midpoint.ninja.action.upgrade.UpgradeCommonOptions;

// todo options
public class DownloadDistributionOptions extends UpgradeCommonOptions {

    public static final String P_DISTRIBUTION_ARCHIVE = "--distribution-archive";

    @Parameter(names = { P_DISTRIBUTION_ARCHIVE }, descriptionKey = "upgrade.distributionArchive")
    private File distributionArchive;

    public File getDistributionArchive() {
        return distributionArchive;
    }

    public void setDistributionArchive(File distributionArchive) {
        this.distributionArchive = distributionArchive;
    }
}
