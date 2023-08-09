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

package com.swirlds.platform.cli;

import com.swirlds.cli.commands.StateCommand;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.logging.LogMarker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

@CommandLine.Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        description = "Validate data integrity of a state file.")
@SubcommandOf(StateCommand.class)
public final class ValidateStateCommand extends AbstractCommand {
    private static final Logger logger = LogManager.getLogger(ValidateStateCommand.class);

    private ValidateStateCommand() {}

    @Override
    public Integer call() {
        logger.info(LogMarker.CLI.getMarker(), "This command is a work in progress (Deepak)");
        return 0;
    }
}
