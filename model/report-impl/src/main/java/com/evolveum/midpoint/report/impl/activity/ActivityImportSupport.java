/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.report.impl.activity;

import com.evolveum.midpoint.repo.common.activity.Activity;
import com.evolveum.midpoint.repo.common.activity.ActivityExecutionException;
import com.evolveum.midpoint.repo.common.activity.execution.ExecutionInstantiationContext;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ReportDataType;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.evolveum.midpoint.report.impl.ReportUtils.getDirection;
import static com.evolveum.midpoint.util.MiscUtil.stateCheck;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.DirectionTypeType.IMPORT;

import static java.util.Objects.requireNonNull;

/**
 * Contains common functionality for import activity executions.
 * This is an experiment - using object composition instead of inheritance.
 */
class ActivityImportSupport extends AbstractActivityReportSupport {

    private static final Trace LOGGER = TraceManager.getTrace(ActivityImportSupport.class);

    @NotNull private final Activity<ClassicReportImportWorkDefinition, ClassicReportImportActivityHandler> activity;

    /**
     * Resolved report data object.
     */
    private ReportDataType reportData;

    ActivityImportSupport(
            ExecutionInstantiationContext<ClassicReportImportWorkDefinition, ClassicReportImportActivityHandler> context) {
        super(context,
                context.getActivity().getHandler().reportService,
                context.getActivity().getHandler().objectResolver,
                context.getActivity().getWorkDefinition());
        activity = context.getActivity();
    }

    @Override
    void initializeExecution(OperationResult result) throws CommonException, ActivityExecutionException {
        super.initializeExecution(result);
        setupReportDataObject(result);
    }

    private void setupReportDataObject(OperationResult result) throws CommonException {
        @NotNull ClassicReportImportWorkDefinition workDefinition = activity.getWorkDefinition();
        reportData = resolver.resolve(workDefinition.getReportDataRef(), ReportDataType.class,
                null, "resolving report data", runningTask, result);
    }

    public ReportDataType getReportData() {
        return reportData;
    }

    private boolean existImportScript() {
        return getReport().getBehavior() != null && getReport().getBehavior().getImportScript() != null;
    }

    boolean existCollectionConfiguration() {
        return getReport().getObjectCollection() != null;
    }

    @Override
    public void stateCheck(OperationResult result) throws CommonException {
        MiscUtil.stateCheck(getDirection(report) == IMPORT, "Only report import are supported here");
        MiscUtil.stateCheck(existCollectionConfiguration() || existImportScript(), "Report of 'import' direction without import script support only object collection engine."
                + " Please define ObjectCollectionReportEngineConfigurationType in report type.");

        if (!reportService.isAuthorizedToImportReport(report.asPrismContainer(), runningTask, result)) {
            LOGGER.error("User is not authorized to import report {}", report);
            throw new SecurityViolationException("Not authorized");
        }

        String pathToFile = getReportData().getFilePath();
        MiscUtil.stateCheck(StringUtils.isNotEmpty(pathToFile), "Path to file for import report is empty.");
        MiscUtil.stateCheck(new File(pathToFile).exists(), "File " + pathToFile + " for import report not exist.");
        MiscUtil.stateCheck(new File(pathToFile).isFile(), "Object " + pathToFile + " for import report isn't file.");
    }
}
