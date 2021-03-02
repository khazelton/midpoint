/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.page.admin.server.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.xml.datatype.XMLGregorianCalendar;

import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.util.MiscUtil;
import com.evolveum.midpoint.web.component.box.InfoBoxType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

public class TaskIterativeProgressType implements Serializable {

    public static final String F_SUCCESS_BOX = "successBox";
    public static final String F_FAILED_BOX = "failedBox";
    public static final String F_SKIP_BOX = "skipBox";
    public static final String F_CURRENT_ITEMS = "currentItems";
    public static final String F_PROGRESS = "progress";

    private ProcessedItemSetType successProcessedItemSetType;
    private ProcessedItemSetType failureProcessedItemSetType;
    private ProcessedItemSetType skippedProcessedItemSetType;

    private List<InfoBoxType> currentItems = new ArrayList<>();

    private InfoBoxType progress;

    public TaskIterativeProgressType(IterativeTaskPartItemsProcessingInformationType processingInfoType) {
        for (ProcessedItemSetType processedItem : processingInfoType.getProcessed()) {
            QualifiedItemProcessingOutcomeType outcome = processedItem.getOutcome();
            if (outcome == null) {
                continue;
            }
            parseItemForOutcome(outcome.getOutcome(), processedItem);
        }
        for (ProcessedItemType currentItem : processingInfoType.getCurrent()) {
            currentItems.add(createInfoBoxType(currentItem, "bg-grey", "fa fa-question"));
        }

        createProgressInfo();
    }

    private void parseItemForOutcome(ItemProcessingOutcomeType outcome, ProcessedItemSetType processedItem) {
        switch (outcome) {
            case SUCCESS:
                this.successProcessedItemSetType = processedItem;
                break;
            case FAILURE:
                this.failureProcessedItemSetType = processedItem;
                break;
            case SKIP:
                this.skippedProcessedItemSetType = processedItem;
                break;
        }
    }

    public InfoBoxType getSuccessBox() {
        return createInfoBoxType(successProcessedItemSetType, "bg-green", "fa fa-check");
    }

    public InfoBoxType getFailedBox() {
        return createInfoBoxType(failureProcessedItemSetType, "bg-red", "fa fa-close");
    }

    public InfoBoxType getSkipBox() {
        return createInfoBoxType(skippedProcessedItemSetType, "bg-orange", "fa fa-warning");
    }

    private InfoBoxType createInfoBoxType(ProcessedItemSetType processedsetType, String background, String icon) {
        if (processedsetType == null || processedsetType.getLastItem() == null) {
            return null;
        }
        ProcessedItemType processedItem = processedsetType.getLastItem();
        return createInfoBoxType(processedItem, background, icon);
    }

    private InfoBoxType createInfoBoxType(ProcessedItemType processedItem, String background, String icon) {
        InfoBoxType infoBoxType = new InfoBoxType(background, icon, processedItem.getDisplayName());
        infoBoxType.setDescription(processedItem.getMessage());
        Long end = getTimestampAsLong(processedItem.getEndTimestamp());
        Long start = getTimestampAsLong(processedItem.getStartTimestamp());

        if (end != null) {
            infoBoxType.setNumber("Took: " + (end - start) + "ms");
        } else {
            infoBoxType.setNumber("Started at: " + WebComponentUtil.formatDate(processedItem.getStartTimestamp()));
        }
        return infoBoxType;
    }

    private Long getTimestampAsLong(XMLGregorianCalendar cal) {
        Long calAsLong = MiscUtil.asLong(cal);
        if (calAsLong == null) {
            return 0L;
        }
        return calAsLong;
    }
    private void createProgressInfo() {
        int success = getCount(successProcessedItemSetType);
        int failure = getCount(failureProcessedItemSetType);
        int skipped = getCount(skippedProcessedItemSetType);

        progress = new InfoBoxType("bg-black", "fa fa-pie-chart", "Success / Failure / Skip");
        progress.setNumber(success + " / " + failure + " / " + skipped);

    }

    private int getCount(ProcessedItemSetType item) {
        if (item == null) {
            return 0;
        }

        Integer count = item.getCount();
        if (count == null) {
            return 0;
        }

        return count;
    }

}