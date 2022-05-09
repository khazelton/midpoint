/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.schema.processor;

import static com.evolveum.midpoint.util.MiscUtil.getFirstNonNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.namespace.QName;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.QNameUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.midpoint.xml.ns._public.resource.capabilities_3.CapabilityType;

/**
 * Default implementation of {@link ResourceObjectTypeDefinition}.
 *
 * Definition of a type of resource objects, as defined in the `schemaHandling` section.
 * (The concept of object type is not present in the "raw" view, presented by a connector.
 * The connector sees only object classes.)
 *
 * The list of attributes is stored in the {@link AbstractResourceObjectDefinitionImpl#attributeDefinitions}.
 * They contain definitions from original object class, enriched with schema handling data.
 *
 * Associations and auxiliary object classes are stored here, as they have no meaning
 * for {@link ResourceObjectClassDefinition}.
 *
 * @author semancik
 */
public final class ResourceObjectTypeDefinitionImpl
        extends AbstractResourceObjectDefinitionImpl
        implements ResourceObjectTypeDefinition {

    /**
     * Kind + intent.
     */
    @NotNull private final ResourceObjectTypeIdentification identification;

    /**
     * Kind of objects covered by this type.
     */
    @NotNull private final ShadowKindType kind;

    /**
     * Intent of objects covered by this type.
     */
    @NotNull private final String intent;

    /**
     * Definition of associations.
     *
     * They are not present in the "raw" object class definition, as they do not exist in this form on the resource.
     *
     * Immutable.
     */
    @NotNull private final DeeplyFreezableList<ResourceAssociationDefinition> associationDefinitions =
            new DeeplyFreezableList<>();

    /**
     * Definition of auxiliary object classes. They originate from
     * {@link ResourceObjectTypeDefinitionType#getAuxiliaryObjectClass()} and are resolved during parsing.
     *
     * However, they are _not_ used by default for attribute resolution!
     * A {@link CompositeObjectDefinition} must be created in order to "activate" them.
     */
    @NotNull private final DeeplyFreezableList<ResourceObjectDefinition> auxiliaryObjectClassDefinitions =
            new DeeplyFreezableList<>();

    /**
     * Definition of the "raw" resource object class.
     *
     * Note that one object class can correspond to multitude of object types (refined object class definitions).
     *
     * Immutable.
     */
    @NotNull private final ResourceObjectClassDefinition rawObjectClassDefinition;

    /**
     * The "source" bean for this definition.
     *
     * - For object type definition, this is relevant `objectType` value in `schemaHandling`.
     * - For object class definition, this is (currently) an empty value. It is here to avoid writing two
     * variants of various getter methods like {@link #getDisplayName()}.
     *
     * Immutable.
     */
    @NotNull private final ResourceObjectTypeDefinitionType definitionBean;

    /**
     * Parent source beans for this definition.
     *
     * They are here to implement resource object type inheritance.
     *
     * Frozen after parsing.
     *
     * TODO remove
     */
    @NotNull private final DeeplyFreezableList<ResourceObjectTypeDefinitionType> parentBeans = new DeeplyFreezableList<>();

    /**
     * Compiled patterns denoting protected objects.
     *
     * @see ResourceObjectTypeDefinitionType#getProtected()
     * @see ResourceObjectPatternType
     *
     * Frozen after parsing. (TODO)
     */
    @NotNull private final FreezableList<ResourceObjectPattern> protectedObjectPatterns = new FreezableList<>();

    /**
     * "Compiled" object set delineation.
     */
    @NotNull private final DeeplyFreezableReference<ResourceObjectTypeDelineation> delineation = new DeeplyFreezableReference<>();

    /**
     * Name of "display name" attribute. May override the value obtained from the resource.
     */
    private QName displayNameAttributeName;

    /**
     * OID of the resource. Usually not null.
     *
     * TODO keep this? If so, shouldn't we have resource OID also in {@link ResourceObjectClassDefinitionImpl} ?
     */
    private final String resourceOid;

    ResourceObjectTypeDefinitionImpl(
            @NotNull ResourceObjectTypeIdentification identification,
            @NotNull ResourceObjectClassDefinition rawDefinition,
            @NotNull ResourceObjectTypeDefinitionType definitionBean,
            String resourceOid) {
        this(DEFAULT_LAYER, identification, rawDefinition, definitionBean, resourceOid);
    }

    private ResourceObjectTypeDefinitionImpl(
            @NotNull LayerType layer,
            @NotNull ResourceObjectTypeIdentification identification,
            @NotNull ResourceObjectClassDefinition rawDefinition,
            @NotNull ResourceObjectTypeDefinitionType definitionBean,
            String resourceOid) {
        super(layer);
        this.identification = identification;
        this.kind = identification.getKind();
        this.intent = identification.getIntent();
        this.rawObjectClassDefinition = rawDefinition;
        this.definitionBean = definitionBean;
        this.resourceOid = resourceOid;

        // TODO parse?

        definitionBean.asPrismContainerValue().checkImmutable();
    }

    @Override
    public @NotNull Collection<ResourceAssociationDefinition> getAssociationDefinitions() {
        return associationDefinitions;
    }

    @Override
    public @NotNull ResourceObjectClassDefinition getObjectClassDefinition() {
        return rawObjectClassDefinition;
    }

    @Override
    public @NotNull QName getObjectClassName() {
        return rawObjectClassDefinition.getObjectClassName();
    }

    @Override
    public @Nullable String getDisplayName() {
        return getFirstNonNull(
                extractFeature(
                        ResourceObjectTypeDefinitionType::getDisplayName),
                rawObjectClassDefinition.getDisplayName());
    }

    @Override
    public String getDescription() {
        return extractFeature(
                ResourceObjectTypeDefinitionType::getDescription);
    }

    @Override
    public boolean isDefaultForObjectClass() {
        // Note that this value cannot be defined on a parent.
        if (definitionBean.isDefaultForObjectClass() != null) {
            return definitionBean.isDefaultForObjectClass();
        } else if (definitionBean.isDefault() != null) {
            return definitionBean.isDefault();
        } else {
            return false;
        }
    }

    @Override
    public boolean isDefaultForKind() {
        // Note that this value cannot be defined on a parent.
        if (definitionBean.isDefaultForKind() != null) {
            return definitionBean.isDefaultForKind();
        } else if (definitionBean.isDefault() != null) {
            return definitionBean.isDefault();
        } else {
            return false;
        }
    }

    /** Sets the delineation and freezes the holder. */
    public void setDelineation(@NotNull ResourceObjectTypeDelineation value) {
        delineation.setValue(value);
        delineation.freeze();
    }

    @Override
    public @NotNull ResourceObjectTypeDelineation getDelineation() {
        return Objects.requireNonNull(
                delineation.getValue(),
                () -> "no delineation in " + this);
    }

    @Override
    public ResourceObjectReferenceType getBaseContext() {
        return getDelineation()
                .getBaseContext();
    }

    @Override
    public SearchHierarchyScope getSearchHierarchyScope() {
        return getDelineation()
                .getSearchHierarchyScope();
    }

    @Override
    public @NotNull String getIntent() {
        return intent;
    }

    @Override
    public @NotNull ShadowKindType getKind() {
        return kind;
    }

    @Override
    public @NotNull ResourceObjectVolatilityType getVolatility() {
        return Objects.requireNonNullElse(
                extractFeature(
                        ResourceObjectTypeDefinitionType::getVolatility),
                ResourceObjectVolatilityType.NONE);
    }

    @Override
    public @Nullable DefaultInboundMappingEvaluationPhasesType getDefaultInboundMappingEvaluationPhases() {
        // In the future we may define the value also on resource or even global system level
        ResourceMappingsEvaluationConfigurationType definition = // TODO consider merging the values
                extractFeature(ResourceObjectTypeDefinitionType::getMappingsEvaluation);
        if (definition == null) {
            return null;
        }
        if (definition.getInbound() == null) {
            return null;
        }
        return definition.getInbound().getDefaultEvaluationPhases();
    }

    @Override
    public ResourceObjectMultiplicityType getObjectMultiplicity() {
        return extractFeature(
                ResourceObjectTypeDefinitionType::getMultiplicity);
    }

    @Override
    public ProjectionPolicyType getProjectionPolicy() {
        return extractFeature(
                ResourceObjectTypeDefinitionType::getProjection);
    }
    //endregion

    //region Accessing parts of schema handling ========================================================
    @NotNull
    @Override
    public Collection<ResourceObjectDefinition> getAuxiliaryDefinitions() {
        return auxiliaryObjectClassDefinitions;
    }

    @Override
    public boolean hasAuxiliaryObjectClass(QName expectedObjectClassName) {
        return auxiliaryObjectClassDefinitions.stream()
                .anyMatch(def -> QNameUtil.match(def.getTypeName(), expectedObjectClassName));
    }

    @Override
    public ResourceBidirectionalMappingAndDefinitionType getAuxiliaryObjectClassMappings() {
        return definitionBean.getAuxiliaryObjectClassMappings();
    }

    @Override
    public @NotNull Collection<ResourceObjectPattern> getProtectedObjectPatterns() {
        return protectedObjectPatterns;
    }

    void addProtectedObjectPattern(ResourceObjectPattern pattern) {
        protectedObjectPatterns.add(pattern);
    }

    @Override
    public ResourcePasswordDefinitionType getPasswordDefinition() {
        ResourceCredentialsDefinitionType credentials = definitionBean.getCredentials();
        if (credentials == null) {
            return null;
        }
        return credentials.getPassword();
    }

    @Override
    public ObjectReferenceType getSecurityPolicyRef() {
        return definitionBean.getSecurityPolicyRef();
    }

    @Override
    public ResourceActivationDefinitionType getActivationSchemaHandling() {
        return definitionBean.getActivation();
    }

    //endregion

    //region Capabilities ========================================================
    @Override
    public @Nullable CapabilitiesType getConfiguredCapabilities() {
        CapabilityCollectionType configuredCapabilities = definitionBean.getConfiguredCapabilities();
        if (configuredCapabilities == null) {
            return null;
        }
        CapabilitiesType capabilitiesType = new CapabilitiesType();
        capabilitiesType.setConfigured(configuredCapabilities);
        return capabilitiesType;
    }

    @Override
    public <T extends CapabilityType> T getEffectiveCapability(Class<T> capabilityClass, ResourceType resource) {
        return ResourceTypeUtil.getEffectiveCapability(resource, definitionBean, capabilityClass);
    }
    //endregion

    @Override
    public void accept(Visitor<Definition> visitor) {
        super.accept(visitor);
        rawObjectClassDefinition.accept(visitor);
        auxiliaryObjectClassDefinitions.forEach(def -> def.accept(visitor));
        associationDefinitions.forEach(def -> def.accept(visitor));
    }

    @Override
    public boolean accept(Visitor<Definition> visitor, SmartVisitation<Definition> visitation) {
        if (!super.accept(visitor, visitation)) {
            return false;
        } else {
            rawObjectClassDefinition.accept(visitor, visitation);
            auxiliaryObjectClassDefinitions.forEach(def -> def.accept(visitor, visitation));
            associationDefinitions.forEach(def -> def.accept(visitor));
            return true;
        }
    }

    //region Cloning ========================================================
    @NotNull
    @Override
    public ResourceObjectTypeDefinitionImpl clone() {
        return cloneInLayer(currentLayer);
    }

    @Override
    public ResourceObjectTypeDefinition forLayer(@NotNull LayerType layerType) {
        return (ResourceObjectTypeDefinition) super.forLayer(layerType);
    }

    @Override
    protected ResourceObjectTypeDefinitionImpl cloneInLayer(@NotNull LayerType layer) {
        ResourceObjectTypeDefinitionImpl clone = new ResourceObjectTypeDefinitionImpl(
                layer, identification, rawObjectClassDefinition, definitionBean, resourceOid);
        clone.copyDefinitionDataFrom(layer, this);
        return clone;
    }

    private void copyDefinitionDataFrom(
            @NotNull LayerType layer,
            @NotNull ResourceObjectTypeDefinition source) {
        super.copyDefinitionDataFrom(layer, source);
        associationDefinitions.addAll(source.getAssociationDefinitions());
        auxiliaryObjectClassDefinitions.addAll(source.getAuxiliaryDefinitions());
        protectedObjectPatterns.addAll(source.getProtectedObjectPatterns());
        displayNameAttributeName = source.getDisplayNameAttributeName();
    }

    /**
     * TODO should we really clone the definitions?
     */
    @NotNull
    @Override
    public ResourceObjectTypeDefinition deepClone(@NotNull DeepCloneOperation operation) {
        return clone(); // TODO ok?
    }
    //endregion

    //region Delegations ========================================================

    @Override
    public boolean isEmpty() {
        return super.isEmpty()
                && associationDefinitions.isEmpty()
                && auxiliaryObjectClassDefinitions.isEmpty();
    }

    //endregion

    //region ==== Parsing =================================================================================
    void add(ResourceAttributeDefinition<?> refinedAttributeDefinition) {
        attributeDefinitions.add(refinedAttributeDefinition);
    }
    //endregion

    //region Diagnostic output, hashCode/equals =========================================================

    @Override
    public String getDebugDumpClassName() {
        return "ROTD";
    }

    @Override
    public String getHumanReadableName() {
        if (getDisplayName() != null) {
            return getDisplayName();
        } else {
            return getKind() + ":" + getIntent();
        }
    }

    @Override
    public String toString() {
        return getDebugDumpClassName() + getMutabilityFlag() + "(" + getKind() + ":" + getIntent()
                + "=" + PrettyPrinter.prettyPrint(getTypeName()) + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ResourceObjectTypeDefinitionImpl that = (ResourceObjectTypeDefinitionImpl) o;
        return attributeDefinitions.equals(that.attributeDefinitions)
                && associationDefinitions.equals(that.associationDefinitions)
                && primaryIdentifiersNames.equals(that.primaryIdentifiersNames)
                && secondaryIdentifiersNames.equals(that.secondaryIdentifiersNames)
                && rawObjectClassDefinition.equals(that.rawObjectClassDefinition)
                && auxiliaryObjectClassDefinitions.equals(that.auxiliaryObjectClassDefinitions)
                && Objects.equals(resourceOid, that.resourceOid)
                && definitionBean.equals(that.definitionBean)
                && kind == that.kind
                && intent.equals(that.intent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), resourceOid, definitionBean, kind, intent);
    }

    //endregion

    @Override
    public void trimTo(@NotNull Collection<ItemPath> paths) {
        if (isImmutable()) {
            return; // This would fail anyway
        }
        rawObjectClassDefinition.trimTo(paths);
        List<QName> names = paths.stream()
                .filter(ItemPath::isSingleName)
                .map(ItemPath::asSingleName)
                .collect(Collectors.toList());
        attributeDefinitions.removeIf(itemDefinition -> !QNameUtil.contains(names, itemDefinition.getItemName()));
        associationDefinitions.removeIf(itemDefinition -> !QNameUtil.contains(names, itemDefinition.getName()));
    }

    @Override
    public MutableResourceObjectClassDefinition toMutable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void performFreeze() {
        super.performFreeze();
        associationDefinitions.freeze();
        rawObjectClassDefinition.freeze(); // TODO really?
        auxiliaryObjectClassDefinitions.freeze();
    }

    @Override
    public boolean hasSubstitutions() {
        return false;
    }

    @Override
    public Optional<ItemDefinition<?>> substitution(QName name) {
        return Optional.empty();
    }

    public @NotNull ResourceObjectTypeDefinitionType getDefinitionBean() {
        return definitionBean;
    }

    @Override
    public @Nullable CorrelationDefinitionType getCorrelationDefinitionBean() {
        return definitionBean.getCorrelation(); // TODO: inheritance
    }

    @Override
    public Boolean isSynchronizationEnabled() {
        return definitionBean.getSynchronization() != null ? true : null; // TODO FIXME
    }

    @Override
    public Boolean isSynchronizationOpportunistic() {
        SynchronizationReactionsType synchronization = definitionBean.getSynchronization(); // TODO: inheritance
        return synchronization != null ? synchronization.isOpportunistic() : null;
    }

    @Override
    public QName getFocusTypeName() {
        return definitionBean.getFocusType(); // TODO: inheritance
    }

    @Override
    public ExpressionType getClassificationCondition() {
        ResourceObjectTypeDelineationType delineation = definitionBean.getObjectsSetDelineation();
        return delineation != null ? delineation.getClassificationCondition() : null;
    }

    @Override
    public boolean hasSynchronizationReactionsDefinition() {
        return definitionBean.getSynchronization() != null;
    }

    @Override
    public @NotNull Collection<SynchronizationReactionDefinition> getSynchronizationReactions() {
        SynchronizationReactionsType reactions = definitionBean.getSynchronization(); // TODO: inheritance
        if (reactions == null) {
            return List.of();
        } else {
            SynchronizationReactionsDefaultSettingsType defaultSettings = reactions.getDefaultSettings();
            ClockworkSettings reactionLevelSettings = ClockworkSettings.of(defaultSettings);
            return reactions.getReaction().stream()
                    .map(bean -> SynchronizationReactionDefinition.of(bean, reactionLevelSettings))
                    .collect(Collectors.toList());
        }
    }

    void addAssociationDefinition(@NotNull ResourceAssociationDefinition associationDef) {
        associationDefinitions.add(associationDef);
    }

    void addAuxiliaryObjectClassDefinition(@NotNull ResourceObjectDefinition definition) {
        auxiliaryObjectClassDefinitions.add(definition);
    }

    @Override
    public @Nullable QName getDescriptionAttributeName() {
        return rawObjectClassDefinition.getDescriptionAttributeName();
    }

    @Override
    public @Nullable QName getNamingAttributeName() {
        return rawObjectClassDefinition.getNamingAttributeName();
    }

    @Override
    public @Nullable QName getDisplayNameAttributeName() {
        if (displayNameAttributeName != null) {
            return displayNameAttributeName;
        } else {
            return rawObjectClassDefinition.getDisplayNameAttributeName();
        }
    }

    public void setDisplayNameAttributeName(QName name) {
        this.displayNameAttributeName = name;
    }

    @Override
    public PrismObject<ShadowType> createBlankShadow(String resourceOid, String tag) {
        return super.createBlankShadow(resourceOid, tag)
                .asObjectable()
                .kind(getKind())
                .intent(getIntent())
                .asPrismObject();
    }

    @Override
    public String getResourceOid() {
        return resourceOid;
    }

    @Override
    protected void addDebugDumpHeaderExtension(StringBuilder sb) {
        if (isDefaultForKind()) {
            sb.append(",default-for-kind");
        }
        if (isDefaultForObjectClass()) {
            sb.append(",default-for-class");
        }
        sb.append(",kind=").append(getKind().value());
        sb.append(",intent=").append(getIntent());
    }

    /** Extracts a feature (e.g. `description`) from the main definition bean or one of the parents. */
    private <X> @Nullable X extractFeature(@NotNull FeatureExtractor<X> extractor) {
        X fromMain = extractor.apply(definitionBean);
        if (fromMain != null) {
            return fromMain;
        }
        for (ResourceObjectTypeDefinitionType parentBean : parentBeans) {
            X fromParent = extractor.apply(parentBean);
            if (fromParent != null) {
                return fromParent;
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface FeatureExtractor<X> extends Function<ResourceObjectTypeDefinitionType, X> {
    }
}
