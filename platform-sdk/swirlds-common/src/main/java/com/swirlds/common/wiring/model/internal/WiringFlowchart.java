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

import com.swirlds.common.wiring.model.ModelEdgeSubstitution;
import com.swirlds.common.wiring.model.ModelGroup;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A utility for drawing mermaid style flowcharts of wiring models.
 */
public final class WiringFlowchart {

    private WiringFlowchart() {}

    private static final String INDENTATION = "    ";
    private static final String SCHEDULER_COLOR = "ff9";
    private static final String DIRECT_SCHEDULER_COLOR = "ccc";
    private static final String TEXT_COLOR = "000";
    private static final String GROUP_COLOR = "9cf";
    private static final String SUBSTITUTION_COLOR = "f88";

    /**
     * Check if an edge is being substituted. If it is, return the name of the destination vertex that should be
     * substituted in.
     *
     * @param edge          the edge to check
     * @param substitutions the edge substitutions to use when generating the wiring diagram
     * @return the name of the destination vertex that should be substituted in, or null if no substitution is
     */
    @Nullable
    private static String findEdgeDestinationSubstitution(
            @NonNull final ModelEdge edge,
            @NonNull final List<ModelEdgeSubstitution> substitutions) {

        // FUTURE WORK: this while loop is inefficient, but until it becomes a problem this brute force
        // approach is good enough.
        for (final ModelEdgeSubstitution substitution : substitutions) {
            if (substitution.source().equals(edge.source().getName()) && substitution.edge().equals(edge.label())) {
                return substitution.substitution();
            }
        }

        return null;
    }

    /**
     * Draw an edge.
     *
     * @param sb                 a string builder where the mermaid file is being assembled
     * @param edge               the edge to draw
     * @param collapsedVertexMap a map from vertices that are in collapsed groups to the group name that they should be
     *                           replaced with
     * @param substitutions      the edge substitutions to use when generating the wiring diagram
     * @param arrowsDrawn        a set of arrows that have already been drawn, used to prevent the drawing of duplicate
     *                           arrows (this is possible if groups of vertices are collapsed)
     */
    private static void drawEdge(
            @NonNull final StringBuilder sb,
            @NonNull final ModelEdge edge,
            @NonNull final Map<ModelVertex, String> collapsedVertexMap,
            @NonNull final List<ModelEdgeSubstitution> substitutions,
            @NonNull final Set<WiringFlowchartArrow> arrowsDrawn) {

        // First, figure out where this arrow should start.
        final String source;
        if (collapsedVertexMap.containsKey(edge.source())) {
            // The edge starts at a vertex inside a collapsed group, and so the vertex will not be drawn.
            // Instead, we draw this edge starting at the collapsed group.
            source = collapsedVertexMap.get(edge.source());
        } else {
            source = edge.source().getName();
        }

        // Next, figure out where this arrow should point.
        final String destination;

        final String substitutionDestination = findEdgeDestinationSubstitution(edge, substitutions);
        if (substitutionDestination != null) {
            destination = substitutionDestination;
        } else if (collapsedVertexMap.containsKey(edge.destination())) {
            destination = collapsedVertexMap.get(edge.destination());
        } else {
            destination = edge.destination().getName();
        }

        // Finally, check if there is a reason to skip drawing this edge.

        if (source.equals(destination)) {
            // Don't draw arrows where the source and the destination are the same.
            return;
        }

        final WiringFlowchartArrow arrow = new WiringFlowchartArrow(source, destination, edge.label(),
                edge.insertionIsBlocking());
        if (!arrowsDrawn.add(arrow)) {
            // Don't draw duplicate arrows.
            return;
        }

        sb.append(INDENTATION).append(source);

        if (edge.insertionIsBlocking()) {
            if (edge.label().isEmpty()) {
                sb.append(" --> ");
            } else {
                sb.append(" -- \"").append(edge.label()).append("\" --> ");
            }
        } else {
            if (edge.label().isEmpty()) {
                sb.append(" -.-> ");
            } else {
                sb.append(" -. \"").append(edge.label()).append("\" .-> ");
            }
        }
        sb.append(destination).append("\n");
    }

    /**
     * Based on the type of vertex, determine the appropriate color.
     *
     * @param vertex the vertex to get the color for
     * @return the color
     */
    private static String getVertexColor(@NonNull final ModelVertex vertex) {
        final TaskSchedulerType type = vertex.getType();

        return switch (type) {
            case SEQUENTIAL:
            case SEQUENTIAL_THREAD:
            case CONCURRENT:
                yield SCHEDULER_COLOR;
            case DIRECT:
            case DIRECT_STATELESS:
                yield DIRECT_SCHEDULER_COLOR;
        };
    }

    /**
     * Get a string representing the inputs to a vertex that are being substituted.
     *
     * @return a string representing the inputs to a vertex that are being substituted, or null if no substitution is
     * taking place for this vertex
     */
    @Nullable
    static String getSubstitutedInputsForVertex(
            @NonNull final ModelVertex vertex,
            @NonNull final Set<ModelEdge> edges,
            @NonNull final List<ModelEdgeSubstitution> substitutions) {

        final Set<String> substitutedInputs = new HashSet<>();

        // TODO do we really have to be this inefficient?
        // FUTURE WORK: this while loop is inefficient, but until it becomes a problem this brute force
        // approach is good enough.

        for (final ModelEdge edge : edges) {
            if (!edge.destination().equals(vertex)) {
                continue;
            }

            for (final ModelEdgeSubstitution substitution : substitutions) {
                if (substitution.source().equals(edge.source().getName()) && substitution.edge().equals(edge.label())) {
                    substitutedInputs.add(substitution.substitution());
                }
            }
        }

        if (substitutedInputs.isEmpty()) {
            return null;
        }

        final List<String> sortedSubstitutedInputs = new ArrayList<>(substitutedInputs);
        sortedSubstitutedInputs.sort(String::compareTo);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sortedSubstitutedInputs.size(); i++) {
            sb.append(sortedSubstitutedInputs.get(i));
            if (i < sortedSubstitutedInputs.size() - 1) {
                sb.append(" ");
            }
        }

        return sb.toString();
    }

    /**
     * Draw a vertex.
     *
     * @param sb                 a string builder where the mermaid file is being assembled
     * @param vertex             the vertex to draw
     * @param edges              the edges in the wiring model
     * @param collapsedVertexMap a map from vertices that are in collapsed groups to the group name that they should be
     *                           replaced with
     * @param indentLevel        the level of indentation
     */
    private static void drawVertex(
            @NonNull final StringBuilder sb,
            @NonNull final ModelVertex vertex,
            @NonNull final Set<ModelEdge> edges,
            @NonNull final Map<ModelVertex, String> collapsedVertexMap,
            @NonNull final List<ModelEdgeSubstitution> substitutions,
            final int indentLevel) {

        if (collapsedVertexMap.containsKey(vertex)) {
            return;
        }

        final String substitutedInputs = getSubstitutedInputsForVertex(vertex, edges, substitutions);

        sb.append(INDENTATION.repeat(indentLevel)).append(vertex.getName());

        if (vertex.getType() == TaskSchedulerType.CONCURRENT) {
            sb.append("[[");
        } else if (vertex.getType() == TaskSchedulerType.DIRECT) {
            sb.append("[/");
        } else if (vertex.getType() == TaskSchedulerType.DIRECT_STATELESS) {
            sb.append("{{");
        } else {
            sb.append("[");
        }

        sb.append("\"");
        sb.append(vertex.getName());
        if (substitutedInputs != null) {
            sb.append("<br />" + substitutedInputs); // TODO
        }
        sb.append("\"");

        if (vertex.getType() == TaskSchedulerType.CONCURRENT) {
            sb.append("]]");
        } else if (vertex.getType() == TaskSchedulerType.DIRECT) {
            sb.append("/]");
        } else if (vertex.getType() == TaskSchedulerType.DIRECT_STATELESS) {
            sb.append("}}");
        } else {
            sb.append("]");
        }

        sb.append("\n");

        sb.append(INDENTATION.repeat(indentLevel))
                .append("style ")
                .append(vertex.getName())
                .append(" fill:#")
                .append(getVertexColor(vertex))
                .append(",stroke:#")
                .append(TEXT_COLOR)
                .append(",stroke-width:2px\n");
    }

    /**
     * Draw a substitution vertex.
     *
     * @param sb           a string builder where the mermaid file is being assembled
     * @param substitution the substitution to draw
     */
    private static void drawSubstitution(@NonNull final StringBuilder sb,
            @NonNull final ModelEdgeSubstitution substitution) {

        sb.append(INDENTATION).append(substitution.substitution()).append("((").append(substitution.substitution())
                .append("))\n");
        sb.append(INDENTATION).append("style ").append(substitution.substitution()).append(" fill:#")
                .append(SUBSTITUTION_COLOR).append(",stroke:#").append(TEXT_COLOR).append(",stroke-width:2px\n");

    }

    /**
     * Draw a group.
     *
     * @param sb                 a string builder where the mermaid file is being assembled
     * @param group              the group to draw
     * @param vertices           the vertices in the group
     * @param edges              the edges in the wiring model
     * @param collapsedVertexMap a map from vertices that are in collapsed groups to the group name that they should be
     * @param substitutions      the edge substitutions to use when generating the wiring diagram
     */
    private static void drawGroup(
            @NonNull final StringBuilder sb,
            @NonNull final ModelGroup group,
            @NonNull final Set<ModelVertex> vertices,
            @NonNull final Set<ModelEdge> edges,
            @NonNull final Map<ModelVertex, String> collapsedVertexMap,
            @NonNull final List<ModelEdgeSubstitution> substitutions) {

        // TODO groups don't show substituted inputs

        sb.append(INDENTATION).append("subgraph ").append(group.name()).append("\n");

        final String color;
        if (group.collapse()) {
            color = SCHEDULER_COLOR;
        } else {
            color = GROUP_COLOR;
        }

        sb.append(INDENTATION.repeat(2))
                .append("style ")
                .append(group.name())
                .append(" fill:#")
                .append(color)
                .append(",stroke:#")
                .append(TEXT_COLOR)
                .append(",stroke-width:2px\n");

        vertices.stream().sorted()
                .forEachOrdered(vertex -> drawVertex(sb, vertex, edges, collapsedVertexMap, substitutions, 2));
        sb.append(INDENTATION).append("end\n");
    }

    /**
     * Get the actual list of vertices for each group (as opposed to just the names of the vertices in the groups).
     *
     * @return the map from group name to the vertices in that group
     */
    @NonNull
    private static Map<String, Set<ModelVertex>> buildGroupMap(
            @NonNull final Map<String, ModelVertex> vertices, @NonNull final List<ModelGroup> groups) {

        final Map<String, Set<ModelVertex>> groupMap = new HashMap<>();

        for (final ModelGroup group : groups) {
            groupMap.put(group.name(), new HashSet<>());
            for (final String vertexName : group.elements()) {
                groupMap.get(group.name()).add(vertices.get(vertexName));
            }
        }

        return groupMap;
    }

    /**
     * Get the list of vertices that are not in any group.
     *
     * @param vertices a map from vertex names to vertices
     * @param groupMap a map of group names to the vertices in those groups
     * @return the list of vertices that are not in any group
     */
    private static List<ModelVertex> getUngroupedVertices(
            @NonNull final Map<String, ModelVertex> vertices,
            @NonNull Map<String /* the name of the group */, Set<ModelVertex>> groupMap) {

        final Set<ModelVertex> uniqueVertices = new HashSet<>(vertices.values());

        for (final Set<ModelVertex> group : groupMap.values()) {
            for (final ModelVertex vertex : group) {
                final boolean removed = uniqueVertices.remove(vertex);
                if (!removed) {
                    throw new IllegalStateException("Vertex " + vertex.getName() + " is in multiple groups.");
                }
            }
        }

        return new ArrayList<>(uniqueVertices);
    }

    /**
     * For all vertices that are in collapsed groups, we want to draw edges to the collapsed group instead of to the
     * individual vertices in the group. This method builds a map from the collapsed vertices to the group name that
     * they should be replaced with.
     *
     * @param groups   the groups
     * @param vertices a map from vertex names to vertices
     * @return a map from collapsed vertices to the group name that they should be replaced with
     */
    @NonNull
    private static Map<ModelVertex, String> getCollapsedVertexMap(
            @NonNull final List<ModelGroup> groups, @NonNull final Map<String, ModelVertex> vertices) {

        final HashMap<ModelVertex, String> collapsedVertexMap = new HashMap<>();

        for (final ModelGroup group : groups) {
            if (!group.collapse()) {
                continue;
            }

            for (final String vertexName : group.elements()) {
                collapsedVertexMap.put(vertices.get(vertexName), group.name());
            }
        }

        return collapsedVertexMap;
    }

    /**
     * Generate a mermaid flowchart of the wiring model.
     *
     * @param vertices      the vertices in the wiring model
     * @param edges         the edges in the wiring model
     * @param groups        the grouping to use when generating the wiring diagram
     * @param substitutions the edge substitutions to use when generating the wiring diagram
     * @return a mermaid flowchart of the wiring model, in string form
     */
    @NonNull
    public static String generateWiringDiagram(
            @NonNull final Map<String, ModelVertex> vertices,
            @NonNull final Set<ModelEdge> edges,
            @NonNull final List<ModelGroup> groups,
            @NonNull final List<ModelEdgeSubstitution> substitutions) {

        final StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR\n");

        final Map<String, Set<ModelVertex>> groupMap = buildGroupMap(vertices, groups);
        final List<ModelVertex> ungroupedVertices = getUngroupedVertices(vertices, groupMap);
        final Map<ModelVertex, String> collapsedVertexMap = getCollapsedVertexMap(groups, vertices);

        final Set<WiringFlowchartArrow> arrowsDrawn = new HashSet<>();

        substitutions.stream().sorted().forEachOrdered(substitution -> drawSubstitution(sb, substitution));
        groups.stream()
                .sorted()
                .forEachOrdered(group -> drawGroup(sb, group, groupMap.get(group.name()), edges, collapsedVertexMap,
                        substitutions));
        ungroupedVertices.stream().sorted()
                .forEachOrdered(vertex -> drawVertex(sb, vertex, edges, collapsedVertexMap, substitutions, 1));
        edges.stream().sorted()
                .forEachOrdered(edge -> drawEdge(sb, edge, collapsedVertexMap, substitutions, arrowsDrawn));

        return sb.toString();
    }
}
