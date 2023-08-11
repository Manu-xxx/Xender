/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.editor;

import com.swirlds.cli.utility.SubcommandOf;
import picocli.CommandLine;

/**
 * Meow.
 */
@CommandLine.Command(name = "cat", mixinStandardHelpOptions = true, description = "Print the toString() of a node")
@SubcommandOf(StateEditorRoot.class)
public class StateEditorCat extends StateEditorOperation {

    private String path = "";

    @CommandLine.Parameters(arity = "0..1", description = "The target route.")
    private void setPath(final String path) {
        this.path = path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("java:S106")
    public void run() {
        System.out.println(getStateEditor().getRelativeNode(path));
    }
}
