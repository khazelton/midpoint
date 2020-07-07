/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.gui.impl.prism.panel;

import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerValueWrapper;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.util.exception.SchemaException;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.prism.wrapper.PrismContainerWrapper;
import com.evolveum.midpoint.prism.Containerable;

/**
 * @author katka
 *
 */
public class PrismContainerPanel<C extends Containerable> extends ItemPanel<PrismContainerValueWrapper<C>, PrismContainerWrapper<C>>{

    private static final long serialVersionUID = 1L;

    private static final String ID_HEADER = "header";

    /**
     * @param id
     * @param model
     */
    public PrismContainerPanel(String id, IModel<PrismContainerWrapper<C>> model, ItemPanelSettings settings) {
        super(id, model, settings);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();


        add(AttributeModifier.append("class", () -> {

            if (getModelObject() != null && getModelObject().isMultiValue()) {
                return " multivalue-container";
            }
            return "";
        }));

    }

    @Override
    protected Component createHeaderPanel() {
        return new PrismContainerHeaderPanel(ID_HEADER, getModel()) {
            @Override
            protected void onExpandClick(AjaxRequestTarget target) {
                PrismContainerWrapper<C> wrapper = PrismContainerPanel.this.getModelObject();
                wrapper.setExpanded(!wrapper.isExpanded());
                target.add(PrismContainerPanel.this);
            }

            @Override
            protected void refreshPanel(AjaxRequestTarget target) {
                target.add(PrismContainerPanel.this);
            }
        };
    }

    @Override
    protected boolean getHeaderVisibility() {
        if(!super.getHeaderVisibility()) {
            return false;
        }
        return getModelObject() != null && getModelObject().isMultiValue();
    }

    @Override
    protected Component createValuePanel(ListItem<PrismContainerValueWrapper<C>> item) {
        ItemPanelSettings settings = getSettings() != null ? getSettings().copy() : null;
        PrismContainerValuePanel<C, PrismContainerValueWrapper<C>> panel = new PrismContainerValuePanel<C, PrismContainerValueWrapper<C>>("value", item.getModel(), settings) {

            @Override
            protected void removeValue(PrismContainerValueWrapper<C> valueToRemove, AjaxRequestTarget target) throws SchemaException {
                PrismContainerPanel.this.removeValue(valueToRemove, target);
            }
        };
        item.add(panel);
        return panel;
    }

    @Override
    protected <PV extends PrismValue> PV createNewValue(PrismContainerWrapper<C> itemWrapper) {
        return (PV) itemWrapper.getItem().createNewValue();
    }
}
