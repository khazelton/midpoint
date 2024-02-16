package com.evolveum.midpoint.model.impl.mining.algorithm.cluster.mechanism;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.evolveum.midpoint.common.mining.objects.handler.RoleAnalysisProgressIncrement;
import com.evolveum.midpoint.model.impl.mining.algorithm.cluster.object.ExtensionProperties;

/**
 * An abstract base class for role analysis clustering data points of a specific type using a distance measure.
 * Subclasses are responsible for implementing the actual clustering logic for a given data type.
 */
public abstract class Clusterer<T extends Clusterable> {
    private final DistanceMeasure measure;

    protected Clusterer(DistanceMeasure measure) {
        this.measure = measure;
    }

    public abstract List<? extends Cluster<T>> cluster(Collection<T> var1, RoleAnalysisProgressIncrement handler);

    protected double accessDistance(Clusterable p1, Clusterable p2) {
        return this.measure.compute(p1.getPoint(), p2.getPoint());
    }

    protected double rulesDistance(ExtensionProperties p1, ExtensionProperties p2, Set<ClusterExplanation> explanation) {
        return this.measure.compute(p1, p2, explanation);
    }

}
