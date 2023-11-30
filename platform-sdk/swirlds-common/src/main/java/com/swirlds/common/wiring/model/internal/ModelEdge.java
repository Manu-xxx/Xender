/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.wiring.model.internal;

import static com.swirlds.common.utility.NonCryptographicHashing.hash32;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * A directed edge between to vertices.
 */
public class ModelEdge
        implements Comparable<ModelEdge> {

    private final ModelVertex source;
    private ModelVertex destination;
    private final String label;
    private final boolean insertionIsBlocking;

    /**
     * Constructor.
     *
     * @param source              the source vertex
     * @param destination         the destination vertex
     * @param label               the label of the edge, if a label is not needed for an edge then holds the value ""
     * @param insertionIsBlocking true if the insertion of this edge may block until capacity is available
     */
    public ModelEdge(
            @NonNull final ModelVertex source,
            @NonNull final ModelVertex destination,
            @NonNull final String label,
            final boolean insertionIsBlocking) {

        this.source = Objects.requireNonNull(source);
        this.destination = Objects.requireNonNull(destination);
        this.label = Objects.requireNonNull(label);
        this.insertionIsBlocking = insertionIsBlocking;
    }

    /**
     * Get the source vertex.
     *
     * @return the source vertex
     */
    @NonNull
    public ModelVertex getSource() {
        return source;
    }

    /**
     * Get the destination vertex.
     *
     * @return the destination vertex
     */
    @NonNull
    public ModelVertex getDestination() {
        return destination;
    }

    /**
     * Set the destination vertex.
     *
     * @param destination the destination vertex
     */
    public void setDestination(@NonNull final StandardVertex destination) {
        this.destination = Objects.requireNonNull(destination);
    }

    /**
     * Get the label of the edge.
     *
     * @return the label of the edge
     */
    @NonNull
    public String getLabel() {
        return label;
    }

    /**
     * Get whether or not the insertion of this edge may block until capacity is available.
     *
     * @return true if the insertion of this edge may block until capacity is available
     */
    public boolean isInsertionIsBlocking() {
        return insertionIsBlocking;
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (obj instanceof final ModelEdge that) {
            return this.source.equals(that.source)
                    && this.destination.equals(that.destination)
                    && this.label.equals(that.label);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hash32(source.hashCode(), destination.hashCode(), label.hashCode());
    }

    /**
     * Useful for looking at a model in a debugger.
     */
    @Override
    public String toString() {
        return source + " --" + label + "-->" + (insertionIsBlocking ? "" : ">") + " " + destination;
    }

    /**
     * Sorts first by source, then by destination, then by label.
     */
    @Override
    public int compareTo(@NonNull final ModelEdge that) {
        if (!this.source.equals(that.source)) {
            return this.source.compareTo(that.source);
        }
        if (!this.destination.equals(that.destination)) {
            return this.destination.compareTo(that.destination);
        }
        return this.label.compareTo(that.label);
    }

    /**
     * Render this edge to a string builder.
     *
     * @param sb           the string builder to render to
     * @param nameProvider provides short names for vertices
     */
    public void render(@NonNull final StringBuilder sb, @NonNull final MermaidNameProvider nameProvider) {

        final String sourceName = nameProvider.getShortenedName(source.getName());
        sb.append(sourceName);

        if (insertionIsBlocking) {
            if (label.isEmpty()) {
                sb.append(" --> ");
            } else {
                sb.append(" -- \"").append(label).append("\" --> ");
            }
        } else {
            if (label.isEmpty()) {
                sb.append(" -.-> ");
            } else {
                sb.append(" -. \"").append(label).append("\" .-> ");
            }
        }

        final String destinationName = nameProvider.getShortenedName(destination.getName());
        sb.append(destinationName).append("\n");
    }
}
