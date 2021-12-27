/*
 * Copyright (C) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.search.refactored;

import org.apache.wicket.Component;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import com.evolveum.midpoint.web.component.input.TextPanel;

public abstract class PropertySearchItemPanel<P extends PropertySearchItemWrapper> extends AbstractSearchItemPanel<P> {

    private static final long serialVersionUID = 1L;

    protected static final String ID_SEARCH_ITEM_FIELD = "searchItemField";

    public PropertySearchItemPanel(String id, IModel<P> model) {
        super(id, model);
    }

    @Override
    protected void onConfigure() {
        super.onConfigure();
        PropertySearchItemWrapper item = getModelObject();
//        if (!item.isEditWhenVisible()) {
//            return;
//        }
//        item.setEditWhenVisible(false);
    }
}
