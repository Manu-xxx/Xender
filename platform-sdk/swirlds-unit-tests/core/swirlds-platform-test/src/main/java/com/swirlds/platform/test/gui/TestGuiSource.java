/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.gui;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.gui.hashgraph.HashgraphGuiSource;
import com.swirlds.platform.gui.hashgraph.internal.FinalShadowgraphGuiSource;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import com.swirlds.platform.test.consensus.ConsensusUtils;
import com.swirlds.platform.test.consensus.TestIntake;
import com.swirlds.platform.test.fixtures.event.EventUtils;
import com.swirlds.platform.test.fixtures.event.generator.GraphGenerator;
import com.swirlds.platform.test.fixtures.event.generator.StandardGraphGenerator;
import com.swirlds.platform.test.fixtures.event.source.EventSource;
import com.swirlds.platform.test.fixtures.event.source.StandardEventSource;
import com.swirlds.test.framework.ResourceLoader;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.awt.FlowLayout;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public class TestGuiSource {
    private final GraphGenerator<?> graphGenerator;
    private final TestIntake intake;
    private final HashgraphGuiSource guiSource;

    public TestGuiSource(final long seed, final int numNodes) {
        graphGenerator = new StandardGraphGenerator(seed, generateSources(numNodes));
        graphGenerator.reset();

        intake = new TestIntake(graphGenerator.getAddressBook());

        guiSource = new FinalShadowgraphGuiSource(intake.getShadowGraph(), graphGenerator.getAddressBook());
    }

    public TestGuiSource(@NonNull final GraphGenerator<?> graphGenerator, @NonNull final TestIntake intake) {
        this.graphGenerator = graphGenerator;
        this.intake = intake;
        this.guiSource = new FinalShadowgraphGuiSource(intake.getShadowGraph(), graphGenerator.getAddressBook());
    }

    public void runGui() {
        HashgraphGuiRunner.runHashgraphGui(guiSource, controls());
    }

    public void generateEvents(final int numEvents) {
        intake.addEvents(graphGenerator.generateEvents(numEvents));
    }

    public @NonNull JPanel controls() {

        // Fame decided below
        final JLabel fameDecidedBelow = new JLabel("N/A");
        final Runnable updateFameDecidedBelow = () -> fameDecidedBelow.setText(
                "fame decided below: " + intake.getConsensus().getFameDecidedBelow());
        updateFameDecidedBelow.run();
        // Next events
        final JButton nextEvent = new JButton("Next events");
        final int defaultNumEvents = 10;
        final int numEventsMinimum = 1;
        final int numEventsStep = 1;
        final JSpinner numEvents = new JSpinner(new SpinnerNumberModel(
                Integer.valueOf(defaultNumEvents),
                Integer.valueOf(numEventsMinimum),
                Integer.valueOf(Integer.MAX_VALUE),
                Integer.valueOf(numEventsStep)));
        nextEvent.addActionListener(e -> {
            intake.addEvents(graphGenerator.generateEvents(
                    numEvents.getValue() instanceof Integer value ? value : defaultNumEvents));
            updateFameDecidedBelow.run();
        });
        // Reset
        final JButton reset = new JButton("Reset");
        reset.addActionListener(e -> {
            graphGenerator.reset();
            intake.reset();
            updateFameDecidedBelow.run();
        });

        // create JPanel
        final JPanel controls = new JPanel(new FlowLayout());
        controls.add(nextEvent);
        controls.add(numEvents);
        controls.add(reset);
        controls.add(fameDecidedBelow);

        return controls;
    }

    public static @NonNull List<EventSource<?>> generateSources(final int numNetworkNodes) {
        final List<EventSource<?>> list = new LinkedList<>();
        for (long i = 0; i < numNetworkNodes; i++) {
            list.add(new StandardEventSource(true));
        }
        return list;
    }
}
