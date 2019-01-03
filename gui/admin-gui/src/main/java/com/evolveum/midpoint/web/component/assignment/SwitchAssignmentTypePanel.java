/*
 * Copyright (c) 2018 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.web.component.assignment;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.web.component.prism.ContainerValueWrapper;
import com.evolveum.midpoint.web.component.prism.ContainerWrapper;
import com.evolveum.midpoint.web.component.util.EnableBehaviour;
import com.evolveum.midpoint.web.component.util.VisibleBehaviour;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.model.IModel;

import javax.xml.namespace.QName;
import java.util.List;

/**
 * Created by honchar
 */
public class SwitchAssignmentTypePanel extends BasePanel<ContainerWrapper<AssignmentType>> {
    private static final long serialVersionUID = 1L;

    private static final String ID_ASSIGNMENT_TYPE_BUTTONS = "assignmentTypeButtons";
    private static final String ID_ALL_ASSIGNMENTS = "allAssignments";
    private static final String ID_ROLE_TYPE_ASSIGNMENTS = "roleTypeAssignments";
    private static final String ID_ORG_TYPE_ASSIGNMENTS = "orgTypeAssignments";
    private static final String ID_SERVICE_TYPE_ASSIGNMENTS = "serviceTypeAssignments";
    private static final String ID_RESOURCE_TYPE_ASSIGNMENTS = "resourceTypeAssignments";
    private static final String ID_POLICY_RULE_TYPE_ASSIGNMENTS = "policyRuleTypeAssignments";
    private static final String ID_ENTITLEMENT_ASSIGNMENTS = "entitlementAssignments";
    private static final String ID_CONSENT_ASSIGNMENTS = "consentAssignments";
    private static final String ID_ASSIGNMENTS = "assignmentsPanel";

    private String activeButtonId = ID_ALL_ASSIGNMENTS;

    public SwitchAssignmentTypePanel(String id, IModel<ContainerWrapper<AssignmentType>> assignmentContainerWrapperModel) {
        super(id, assignmentContainerWrapperModel);
    }

    @Override
    protected void onInitialize(){
        super.onInitialize();
        initButtonsPanel();
        initAssignmentsPanel();

        setOutputMarkupId(true);
    }

    private void initButtonsPanel(){
        WebMarkupContainer buttonsContainer = new WebMarkupContainer(ID_ASSIGNMENT_TYPE_BUTTONS);
        buttonsContainer.setOutputMarkupId(true);
        buttonsContainer.add(new VisibleBehaviour(() -> getButtonsContainerVisibilityModel().getObject()));
        add(buttonsContainer);

        AjaxButton allAssignmentsButton = new AjaxButton(ID_ALL_ASSIGNMENTS, createStringResource("AssignmentPanel.allLabel")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                AssignmentPanel assignmentPanel =
                        new AssignmentPanel(ID_ASSIGNMENTS, SwitchAssignmentTypePanel.this.getModel()) {
                            private static final long serialVersionUID = 1L;

                            @Override
                            protected void assignmentDetailsPerformed(AjaxRequestTarget target) {
                                target.add(SwitchAssignmentTypePanel.this);
                            }

                            @Override
                            protected void cancelAssignmentDetailsPerformed(AjaxRequestTarget target) {
                                target.add(SwitchAssignmentTypePanel.this);
                            }
                        };
                assignmentPanel.setOutputMarkupId(true);
                switchAssignmentTypePerformed(target, assignmentPanel, ID_ALL_ASSIGNMENTS);
            }
        };
        allAssignmentsButton.add(AttributeAppender.append("class", getButtonStyleModel(ID_ALL_ASSIGNMENTS)));
        allAssignmentsButton.setOutputMarkupId(true);
        buttonsContainer.add(allAssignmentsButton);

        AjaxButton roleTypeAssignmentsButton = new AjaxButton(ID_ROLE_TYPE_ASSIGNMENTS, createStringResource("ObjectType.RoleType")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                AbstractRoleAssignmentPanel assignmentPanel =
                        new AbstractRoleAssignmentPanel(ID_ASSIGNMENTS, SwitchAssignmentTypePanel.this.getModel()) {
                            private static final long serialVersionUID = 1L;

                            @Override
                            protected QName getAssignmentType() {
                                return RoleType.COMPLEX_TYPE;
                            }

                            @Override
                            protected void assignmentDetailsPerformed(AjaxRequestTarget target) {
                                target.add(SwitchAssignmentTypePanel.this);
                            }

                            @Override
                            protected void cancelAssignmentDetailsPerformed(AjaxRequestTarget target) {
                                target.add(SwitchAssignmentTypePanel.this);
                            }
                        };
                assignmentPanel.setOutputMarkupId(true);
                switchAssignmentTypePerformed(target, assignmentPanel, ID_ROLE_TYPE_ASSIGNMENTS);
            }
        };
        roleTypeAssignmentsButton.add(AttributeAppender.append("class", getButtonStyleModel(ID_ROLE_TYPE_ASSIGNMENTS)));
        roleTypeAssignmentsButton.setOutputMarkupId(true);
        buttonsContainer.add(roleTypeAssignmentsButton);

        AjaxButton orgTypeAssignmentsButton = new AjaxButton(ID_ORG_TYPE_ASSIGNMENTS, createStringResource("ObjectType.OrgType")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                AbstractRoleAssignmentPanel assignmentPanel =
                        new AbstractRoleAssignmentPanel(ID_ASSIGNMENTS, SwitchAssignmentTypePanel.this.getModel()) {
                            private static final long serialVersionUID = 1L;

                            @Override
                            protected QName getAssignmentType() {
                                return OrgType.COMPLEX_TYPE;
                            }

                            @Override
                            protected void assignmentDetailsPerformed(AjaxRequestTarget target) {
                                target.add(SwitchAssignmentTypePanel.this);
                            }

                            @Override
                            protected void cancelAssignmentDetailsPerformed(AjaxRequestTarget target) {
                                target.add(SwitchAssignmentTypePanel.this);
                            }
                        };
                assignmentPanel.setOutputMarkupId(true);
                switchAssignmentTypePerformed(target, assignmentPanel, ID_ORG_TYPE_ASSIGNMENTS);
            }
        };
        orgTypeAssignmentsButton.add(AttributeAppender.append("class", getButtonStyleModel(ID_ORG_TYPE_ASSIGNMENTS)));
        orgTypeAssignmentsButton.setOutputMarkupId(true);
        buttonsContainer.add(orgTypeAssignmentsButton);

        AjaxButton serviceTypeAssignmentsButton = new AjaxButton(ID_SERVICE_TYPE_ASSIGNMENTS, createStringResource("ObjectType.ServiceType")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                AbstractRoleAssignmentPanel assignmentPanel =
                        new AbstractRoleAssignmentPanel(ID_ASSIGNMENTS, SwitchAssignmentTypePanel.this.getModel()) {
                            private static final long serialVersionUID = 1L;

                            @Override
                            protected QName getAssignmentType() {
                                return ServiceType.COMPLEX_TYPE;
                            }

                            @Override
                            protected void assignmentDetailsPerformed(AjaxRequestTarget target) {
                                target.add(SwitchAssignmentTypePanel.this);
                            }

                            @Override
                            protected void cancelAssignmentDetailsPerformed(AjaxRequestTarget target) {
                                target.add(SwitchAssignmentTypePanel.this);
                            }
                        };
                assignmentPanel.setOutputMarkupId(true);
                switchAssignmentTypePerformed(target, assignmentPanel, ID_SERVICE_TYPE_ASSIGNMENTS);

            }
        };
        serviceTypeAssignmentsButton.add(AttributeAppender.append("class", getButtonStyleModel(ID_SERVICE_TYPE_ASSIGNMENTS)));
        serviceTypeAssignmentsButton.setOutputMarkupId(true);
        buttonsContainer.add(serviceTypeAssignmentsButton);

        AjaxButton resourceTypeAssignmentsButton = new AjaxButton(ID_RESOURCE_TYPE_ASSIGNMENTS, createStringResource("ObjectType.ResourceType")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                ConstructionAssignmentPanel constructionsPanel =
                        new ConstructionAssignmentPanel(ID_ASSIGNMENTS, SwitchAssignmentTypePanel.this.getModel()){
                            private static final long serialVersionUID = 1L;

                            @Override
                            protected void assignmentDetailsPerformed(AjaxRequestTarget target) {
                                target.add(SwitchAssignmentTypePanel.this);
                            }

                            @Override
                            protected void cancelAssignmentDetailsPerformed(AjaxRequestTarget target) {
                                target.add(SwitchAssignmentTypePanel.this);
                            }
                        };
                constructionsPanel.setOutputMarkupId(true);
                switchAssignmentTypePerformed(target, constructionsPanel, ID_RESOURCE_TYPE_ASSIGNMENTS);
            }
        };
        resourceTypeAssignmentsButton.add(AttributeAppender.append("class", getButtonStyleModel(ID_RESOURCE_TYPE_ASSIGNMENTS)));
        resourceTypeAssignmentsButton.setOutputMarkupId(true);
        buttonsContainer.add(resourceTypeAssignmentsButton);

        AjaxButton policyRuleTypeAssignmentsButton = new AjaxButton(ID_POLICY_RULE_TYPE_ASSIGNMENTS, createStringResource("AssignmentType.policyRule")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                PolicyRulesPanel policyRulesPanel =
                        new PolicyRulesPanel(ID_ASSIGNMENTS, SwitchAssignmentTypePanel.this.getModel()){
                            private static final long serialVersionUID = 1L;

                            @Override
                            protected void assignmentDetailsPerformed(AjaxRequestTarget target) {
                                target.add(SwitchAssignmentTypePanel.this);
                            }

                            @Override
                            protected void cancelAssignmentDetailsPerformed(AjaxRequestTarget target) {
                                target.add(SwitchAssignmentTypePanel.this);
                            }
                        } ;
                policyRulesPanel.setOutputMarkupId(true);
                switchAssignmentTypePerformed(target, policyRulesPanel, ID_POLICY_RULE_TYPE_ASSIGNMENTS);

            }
        };
        policyRuleTypeAssignmentsButton.add(AttributeAppender.append("class", getButtonStyleModel(ID_POLICY_RULE_TYPE_ASSIGNMENTS)));
        policyRuleTypeAssignmentsButton.setOutputMarkupId(true);
        policyRuleTypeAssignmentsButton.add(new VisibleBehaviour(()  ->
                getModelObject().getObjectWrapper().getObject().asObjectable() instanceof AbstractRoleType));
        buttonsContainer.add(policyRuleTypeAssignmentsButton);

        AjaxButton entitlementAssignmentsButton = new AjaxButton(ID_ENTITLEMENT_ASSIGNMENTS, createStringResource("AbstractRoleMainPanel.inducedEntitlements")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                InducedEntitlementsPanel entitlementAssignments =
                        new InducedEntitlementsPanel(ID_ASSIGNMENTS, SwitchAssignmentTypePanel.this.getModel()) {
                            private static final long serialVersionUID = 1L;

                            @Override
                            protected void assignmentDetailsPerformed(AjaxRequestTarget target) {
                                target.add(SwitchAssignmentTypePanel.this);
                            }

                            @Override
                            protected void cancelAssignmentDetailsPerformed(AjaxRequestTarget target) {
                                target.add(SwitchAssignmentTypePanel.this);
                            }
                        };
                entitlementAssignments.setOutputMarkupId(true);
                switchAssignmentTypePerformed(target, entitlementAssignments, ID_ENTITLEMENT_ASSIGNMENTS);

            }
        };
        entitlementAssignmentsButton.add(AttributeAppender.append("class", getButtonStyleModel(ID_ENTITLEMENT_ASSIGNMENTS)));
        entitlementAssignmentsButton.setOutputMarkupId(true);
        entitlementAssignmentsButton.add(new VisibleBehaviour(()  ->
                (getModelObject().getObjectWrapper().getObject().asObjectable() instanceof AbstractRoleType) && isInducement()));
        buttonsContainer.add(entitlementAssignmentsButton);

        //GDPR feature.. temporary disabled MID-4281
//        AjaxButton consentsButton = new AjaxButton(ID_CONSENT_ASSIGNMENTS, createStringResource("FocusType.consents")) {
//                            private static final long serialVersionUID = 1L;
//
//            @Override
//            public void onClick(AjaxRequestTarget target) {
//                GdprAssignmentPanel gdprAssignmentPanel =
//                        new GdprAssignmentPanel(ID_ASSIGNMENTS, SwitchAssignmentTypePanel.this.getModel()){
// private static final long serialVersionUID = 1L;
//
//        @Override
//        protected void assignmentDetailsPerformed(AjaxRequestTarget target) {
//            target.add(SwitchAssignmentTypePanel.this);
//        }
//
//        @Override
//        protected void cancelAssignmentDetailsPerformed(AjaxRequestTarget target) {
//            target.add(SwitchAssignmentTypePanel.this);
//        }
// };
//                gdprAssignmentPanel.setOutputMarkupId(true);
//                SwitchAssignmentTypePanel.this.addOrReplace(gdprAssignmentPanel);
//                target.add(SwitchAssignmentTypePanel.this);
//            }
//        };
//        consentsButton.setOutputMarkupId(true);
//        buttonsContainer.add(consentsButton);
    }

    private LoadableModel<Boolean> getButtonsContainerVisibilityModel(){
        return new LoadableModel<Boolean>() {
            @Override
            protected Boolean load() {
                return getAssignmentsPanel().getMultivalueContainerListPanel().isListPanelVisible();
            }
        };
    }

    private void switchAssignmentTypePerformed(AjaxRequestTarget target, AssignmentPanel assignmentsPanel, String buttonId){
        activeButtonId = buttonId;
        addOrReplace(assignmentsPanel);
        target.add(SwitchAssignmentTypePanel.this);
    }

    private LoadableModel<String> getButtonStyleModel(String buttonId){
        return new LoadableModel<String>() {
            @Override
            protected String load() {
                if (activeButtonId.equals(buttonId)){
                    return "btn btn-primary";
                } else {
                    return "btn btn-default";
                }
            }
        };
    }

    private void initAssignmentsPanel(){
        AssignmentPanel assignmentsPanel = new AssignmentPanel(ID_ASSIGNMENTS, getModel()){
            private static final long serialVersionUID = 1L;

            @Override
            protected void assignmentDetailsPerformed(AjaxRequestTarget target) {
                target.add(SwitchAssignmentTypePanel.this);
            }

            @Override
            protected void cancelAssignmentDetailsPerformed(AjaxRequestTarget target){
                target.add(SwitchAssignmentTypePanel.this);
            }
        };
        assignmentsPanel.setOutputMarkupId(true);
        add(assignmentsPanel);
    }

    public AssignmentPanel getAssignmentsPanel(){
        return (AssignmentPanel) get(ID_ASSIGNMENTS);
    }

    protected boolean isInducement(){
        return false;
    }
}
