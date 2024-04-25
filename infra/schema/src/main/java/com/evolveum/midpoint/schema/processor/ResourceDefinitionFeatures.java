/*
 * Copyright (C) 2010-2024 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.schema.processor;

import com.evolveum.midpoint.prism.impl.schema.features.DefinitionFeatures.XsdSerializers;
import com.evolveum.midpoint.prism.impl.schema.features.DefinitionFeatures.XsomParsers;
import com.evolveum.midpoint.prism.schema.DefinitionFeature;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.processor.NativeObjectClassDefinition.NativeObjectClassDefinitionBuilder;
import com.evolveum.midpoint.schema.processor.NativeShadowItemDefinition.NativeShadowItemDefinitionBuilder;

import com.sun.xml.xsom.XSComplexType;

import javax.xml.namespace.QName;

/**
 * Features specific to [native] resource object classes and attributes.
 */
class ResourceDefinitionFeatures {

    static class ForClass {

        static final DefinitionFeature<String, NativeObjectClassDefinitionBuilder, Object, ?> DF_NATIVE_OBJECT_CLASS_NAME =
                DefinitionFeature.of(
                        String.class,
                        NativeObjectClassDefinitionBuilder.class,
                        NativeObjectClassDefinitionBuilder::setNativeObjectClassName,
                        XsomParsers.string(MidPointConstants.RA_NATIVE_OBJECT_CLASS),
                        NativeObjectClassUcfDefinition.class,
                        NativeObjectClassUcfDefinition::getNativeObjectClassName,
                        XsdSerializers.string(MidPointConstants.RA_NATIVE_OBJECT_CLASS));

        static final DefinitionFeature<Boolean, NativeObjectClassDefinitionBuilder, Object, ?> DF_DEFAULT_ACCOUNT_DEFINITION =
                DefinitionFeature.of(
                        Boolean.class,
                        NativeObjectClassDefinitionBuilder.class,
                        NativeObjectClassDefinitionBuilder::setDefaultAccountDefinition,
                        XsomParsers.marker(MidPointConstants.RA_DEFAULT),
                        NativeObjectClassUcfDefinition.class,
                        NativeObjectClassUcfDefinition::isDefaultAccountDefinition,
                        XsdSerializers.aBoolean(MidPointConstants.RA_DEFAULT));

        static final DefinitionFeature<Boolean, NativeObjectClassDefinitionBuilder, Object, ?> DF_AUXILIARY =
                DefinitionFeature.of(
                        Boolean.class,
                        NativeObjectClassDefinitionBuilder.class,
                        NativeObjectClassDefinitionBuilder::setAuxiliary,
                        XsomParsers.marker(MidPointConstants.RA_AUXILIARY),
                        NativeObjectClassUcfDefinition.class,
                        NativeObjectClassUcfDefinition::isAuxiliary,
                        XsdSerializers.aBoolean(MidPointConstants.RA_AUXILIARY));

        static final DefinitionFeature<QName, NativeObjectClassDefinitionBuilder, Object, ?> DF_NAMING_ATTRIBUTE_NAME =
                DefinitionFeature.of(
                        QName.class,
                        NativeObjectClassDefinitionBuilder.class,
                        NativeObjectClassDefinitionBuilder::setNamingAttributeName,
                        XsomParsers.qName(MidPointConstants.RA_NAMING_ATTRIBUTE),
                        NativeObjectClassUcfDefinition.class,
                        NativeObjectClassUcfDefinition::getNamingAttributeName,
                        XsdSerializers.qName(MidPointConstants.RA_NAMING_ATTRIBUTE));

        static final DefinitionFeature<QName, NativeObjectClassDefinitionBuilder, Object, ?> DF_DISPLAY_NAME_ATTRIBUTE_NAME =
                DefinitionFeature.of(
                        QName.class,
                        NativeObjectClassDefinitionBuilder.class,
                        NativeObjectClassDefinitionBuilder::setDisplayNameAttributeName,
                        XsomParsers.qName(MidPointConstants.RA_DISPLAY_NAME_ATTRIBUTE),
                        NativeObjectClassUcfDefinition.class,
                        NativeObjectClassUcfDefinition::getDisplayNameAttributeName,
                        XsdSerializers.qName(MidPointConstants.RA_DISPLAY_NAME_ATTRIBUTE));

        static final DefinitionFeature<QName, NativeObjectClassDefinitionBuilder, Object, ?> DF_DESCRIPTION_ATTRIBUTE_NAME =
                DefinitionFeature.of(
                        QName.class,
                        NativeObjectClassDefinitionBuilder.class,
                        NativeObjectClassDefinitionBuilder::setDescriptionAttributeName,
                        XsomParsers.qName(MidPointConstants.RA_DESCRIPTION_ATTRIBUTE),
                        NativeObjectClassUcfDefinition.class,
                        NativeObjectClassUcfDefinition::getDescriptionAttributeName,
                        XsdSerializers.qName(MidPointConstants.RA_DESCRIPTION_ATTRIBUTE));

        static final DefinitionFeature<QName, NativeObjectClassDefinitionBuilder, Object, ?> DF_PRIMARY_IDENTIFIER_NAME =
                DefinitionFeature.of(
                        QName.class,
                        NativeObjectClassDefinitionBuilder.class,
                        NativeObjectClassDefinitionBuilder::setPrimaryIdentifierName,
                        XsomParsers.qName(MidPointConstants.RA_IDENTIFIER),
                        NativeObjectClassUcfDefinition.class,
                        NativeObjectClassUcfDefinition::getPrimaryIdentifierName,
                        XsdSerializers.qName(MidPointConstants.RA_IDENTIFIER));

        static final DefinitionFeature<QName, NativeObjectClassDefinitionBuilder, Object, ?> DF_SECONDARY_IDENTIFIER_NAME =
                DefinitionFeature.of(
                        QName.class,
                        NativeObjectClassDefinitionBuilder.class,
                        NativeObjectClassDefinitionBuilder::setSecondaryIdentifierName,
                        XsomParsers.qName(MidPointConstants.RA_SECONDARY_IDENTIFIER),
                        NativeObjectClassUcfDefinition.class,
                        NativeObjectClassUcfDefinition::getSecondaryIdentifierName,
                        XsdSerializers.qName(MidPointConstants.RA_SECONDARY_IDENTIFIER));

        /**
         * `true` denotes resource object class, `false` denotes resource association class. Not very nice but practical.
         */
        static final DefinitionFeature<Boolean, NativeObjectClassDefinitionBuilder, XSComplexType, ?> DF_RESOURCE_OBJECT =
                DefinitionFeature.of(
                        Boolean.class,
                        NativeObjectClassDefinitionBuilder.class,
                        NativeObjectClassDefinitionBuilder::setResourceObject,
                        XsomParsers.markerForComplexType(MidPointConstants.RA_RESOURCE_OBJECT),
                        NativeComplexTypeDefinitionImpl.class,
                        NativeComplexTypeDefinitionImpl::isResourceObjectClass,
                        XsdSerializers.aBoolean(MidPointConstants.RA_RESOURCE_OBJECT));
    }

    static class ForItem {

        static final DefinitionFeature<String, NativeShadowItemDefinitionBuilder, Object, ?> DF_NATIVE_ATTRIBUTE_NAME =
                DefinitionFeature.of(
                        String.class,
                        NativeShadowItemDefinitionBuilder.class,
                        NativeShadowItemDefinitionBuilder::setNativeAttributeName,
                        XsomParsers.string(MidPointConstants.RA_NATIVE_ATTRIBUTE_NAME),
                        NativeShadowItemDefinition.class,
                        NativeShadowItemDefinition::getNativeAttributeName,
                        XsdSerializers.string(MidPointConstants.RA_NATIVE_ATTRIBUTE_NAME));

        static final DefinitionFeature<String, NativeShadowItemDefinitionBuilder, Object, ?> DF_FRAMEWORK_ATTRIBUTE_NAME =
                DefinitionFeature.of(
                        String.class,
                        NativeShadowItemDefinitionBuilder.class,
                        NativeShadowItemDefinitionBuilder::setFrameworkAttributeName,
                        XsomParsers.string(MidPointConstants.RA_FRAMEWORK_ATTRIBUTE_NAME),
                        NativeShadowItemDefinition.class,
                        NativeShadowItemDefinition::getFrameworkAttributeName,
                        XsdSerializers.string(MidPointConstants.RA_FRAMEWORK_ATTRIBUTE_NAME));

        static final DefinitionFeature<Boolean, NativeShadowItemDefinitionBuilder, Object, ?> DF_RETURNED_BY_DEFAULT =
                DefinitionFeature.of(
                        Boolean.class,
                        NativeShadowItemDefinitionBuilder.class,
                        NativeShadowItemDefinitionBuilder::setReturnedByDefault,
                        XsomParsers.marker(MidPointConstants.RA_RETURNED_BY_DEFAULT_NAME),
                        NativeShadowItemDefinition.class,
                        NativeShadowItemDefinition::getReturnedByDefault,
                        XsdSerializers.aBoolean(MidPointConstants.RA_RETURNED_BY_DEFAULT_NAME));

        static final DefinitionFeature<ShadowAssociationParticipantRole, NativeShadowItemDefinitionBuilder, Object, ?> DF_ASSOCIATION_PARTICIPANT_ROLE =
                DefinitionFeature.of(
                        ShadowAssociationParticipantRole.class,
                        NativeShadowItemDefinitionBuilder.class,
                        NativeShadowItemDefinitionBuilder::setAssociationParticipantRole,
                        XsomParsers.enumBased(ShadowAssociationParticipantRole.class, MidPointConstants.RA_ASSOCIATION_PARTICIPANT_ROLE, ShadowAssociationParticipantRole::getValue),
                        NativeShadowItemDefinition.class,
                        NativeShadowItemDefinition::getAssociationParticipantRoleIfPresent,
                        XsdSerializers.enumBased(ShadowAssociationParticipantRole.class, MidPointConstants.RA_ASSOCIATION_PARTICIPANT_ROLE, ShadowAssociationParticipantRole::getValue));
    }
}