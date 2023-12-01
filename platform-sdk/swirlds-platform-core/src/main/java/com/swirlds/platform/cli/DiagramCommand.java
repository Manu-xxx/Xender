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

package com.swirlds.platform.cli;

import com.swirlds.base.time.Time;
import com.swirlds.cli.PlatformCli;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.system.status.PlatformStatusManager;
import com.swirlds.common.wiring.model.ModelGroup;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.config.DefaultConfiguration;
import com.swirlds.platform.wiring.PlatformWiring;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import picocli.CommandLine;

@CommandLine.Command(
        name = "diagram",
        mixinStandardHelpOptions = true,
        description = "Generate a mermaid style diagram of platform wiring.")
@SubcommandOf(PlatformCli.class)
public final class DiagramCommand extends AbstractCommand {

    private List<String> groupStrings = List.of();
    private List<String> collapsedGroupStrings = List.of();

    private DiagramCommand() {}

    @CommandLine.Option(
            names = {"-g", "--group"},
            description = "Specify an un-collapsed grouping. Format is 'GROUP_NAME:COMPONENT_NAME[,COMPONENT_NAME]*'.")
    private void setGroupStrings(@NonNull final List<String> groupStrings) {
        this.groupStrings = groupStrings;
    }

    @CommandLine.Option(
            names = {"-c", "--collapsed-group"},
            description = "Specify a collapsed grouping. Format is 'GROUP_NAME:COMPONENT_NAME[,COMPONENT_NAME]*'.")
    private void setCollapsedGroupStrings(@NonNull final List<String> collapsedGroupStrings) {
        this.collapsedGroupStrings = collapsedGroupStrings;
    }

    /**
     * Entry point.
     */
    @Override
    public Integer call() throws IOException {
        final Configuration configuration = DefaultConfiguration.buildBasicConfiguration();
        final PlatformContext platformContext =
                new DefaultPlatformContext(configuration, new NoOpMetrics(), CryptographyHolder.get());

        final PlatformWiring platformWiring = new PlatformWiring(platformContext, Time.getCurrent());

        final String diagramString = platformWiring.getModel().generateWiringDiagram(parseGroups());
        final String encodedDiagramString = Base64.getEncoder().encodeToString(diagramString.getBytes());

        final String editorUrl = "https://mermaid.ink/svg/" + encodedDiagramString + "?bgColor=e8e8e8";

        System.out.println(diagramString);
        System.out.println();
        System.out.println(editorUrl);
        return 0;
    }

    /**
     * Parse groups from the command line arguments.
     *
     * @return a set of zero or more groups
     */
    @NonNull
    private Set<ModelGroup> parseGroups() {
        final Set<ModelGroup> groups = new HashSet<>();

        for (final String group : groupStrings) {
            final String[] parts = group.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid group string: " + group);
            }
            final String groupName = parts[0];
            final String[] elements = parts[1].split(",");
            groups.add(new ModelGroup(groupName, Set.of(elements), false));
        }

        for (final String group : collapsedGroupStrings) {
            final String[] parts = group.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid group string: " + group);
            }
            final String groupName = parts[0];
            final String[] elements = parts[1].split(",");
            groups.add(new ModelGroup(groupName, Set.of(elements), true));
        }

        return groups;
    }
}
