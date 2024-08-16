/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.yahcli.commands.nodes;

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.services.bdd.spec.HapiPropertySource.asCsServiceEndpoints;
import static com.hedera.services.yahcli.config.ConfigUtils.keyFileFor;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.inventory.AccessoryUtils;
import com.hedera.services.yahcli.config.ConfigUtils;
import com.hedera.services.yahcli.suites.CreateNodeSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
        name = "create",
        subcommands = {CommandLine.HelpCommand.class},
        description = "Creates a new node")
public class CreateCommand implements Callable<Integer> {
    @CommandLine.ParentCommand
    NodesCommand nodesCommand;

    @CommandLine.Option(
            names = {"-a", "--accountNum"},
            paramLabel = "the number of the node's fee collection account id, e.g. 3 for 0.0.3")
    String accountNum;

    @CommandLine.Option(
            names = {"-d", "--description"},
            paramLabel = "description for the new node")
    @Nullable
    String description;

    @CommandLine.Option(
            names = {"-g", "--gossipEndpoints"},
            paramLabel = "comma-delimited gossip endpoints, e.g. 10.0.0.1:50070,my.fqdn.com:50070")
    String gossipEndpoints;

    @CommandLine.Option(
            names = {"-s", "--serviceEndpoints"},
            paramLabel = "comma-delimited gossip endpoints, e.g. 10.0.0.1:50211,my.fqdn.com:50211")
    String serviceEndpoints;

    @CommandLine.Option(
            names = {"-c", "--gossipCaCertificate"},
            paramLabel = "path to the X.509 CA certificate for node's gossip key")
    String gossipCaCertificatePath;

    @CommandLine.Option(
            names = {"-h", "--hapiCertificate"},
            paramLabel = "path to the self-signed X.509 certificate for node's HAPI TLS endpoint")
    String hapiCertificatePath;

    @CommandLine.Option(
            names = {"-k", "--adminKey"},
            paramLabel = "path to the admin key to use")
    String adminKeyPath;

    @Override
    public Integer call() throws Exception {
        final var yahcli = nodesCommand.getYahcli();
        var config = ConfigUtils.configFrom(yahcli);

        validateAdminKeyLoc(adminKeyPath);
        final var accountId = validatedAccountId(accountNum);
        final var feeAccountKeyFile = keyFileFor(config.keysLoc(), "account" + accountId.getAccountNum());
        final var maybeFeeAccountKeyPath = feeAccountKeyFile.map(File::getPath).orElse(null);
        if (maybeFeeAccountKeyPath == null) {
            COMMON_MESSAGES.warn("No key on disk for account 0.0." + accountId.getAccountNum()
                    + ", payer and admin key signatures must meet its signing requirements");
        }

        final var delegate = new CreateNodeSuite(
                config.asSpecConfig(),
                accountId,
                Optional.ofNullable(description).orElse(""),
                asCsServiceEndpoints(gossipEndpoints),
                asCsServiceEndpoints(serviceEndpoints),
                NodesCommand.validatedX509Cert(gossipCaCertificatePath, yahcli),
                noThrowSha384HashOf(NodesCommand.validatedX509Cert(hapiCertificatePath, yahcli)),
                adminKeyPath,
                maybeFeeAccountKeyPath);
        delegate.runSuiteSync();

        if (delegate.getFinalSpecs().getFirst().getStatus() == HapiSpec.SpecStatus.PASSED) {
            COMMON_MESSAGES.info("SUCCESS - created node" + delegate.createdIdOrThrow());
        } else {
            COMMON_MESSAGES.warn("FAILED to create node");
            return 1;
        }

        return 0;
    }

    private void validateAdminKeyLoc(@NonNull final String adminKeyPath) {
        final Optional<File> adminKeyFile;
        try {
            adminKeyFile = AccessoryUtils.keyFileAt(adminKeyPath.substring(0, adminKeyPath.lastIndexOf('.')));
        } catch (Exception e) {
            throw new CommandLine.ParameterException(
                    nodesCommand.getYahcli().getSpec().commandLine(),
                    "Could not load a key from '" + adminKeyPath + "' (" + e.getMessage() + ")");
        }
        if (adminKeyFile.isEmpty()) {
            throw new CommandLine.ParameterException(
                    nodesCommand.getYahcli().getSpec().commandLine(),
                    "Could not load a key from '" + adminKeyPath + "'");
        }
    }

    private AccountID validatedAccountId(@NonNull final String accountNum) {
        try {
            return AccountID.newBuilder()
                    .setAccountNum(Long.parseLong(accountNum))
                    .build();
        } catch (NumberFormatException e) {
            throw new CommandLine.ParameterException(
                    nodesCommand.getYahcli().getSpec().commandLine(), "Invalid account number '" + accountNum + "'");
        }
    }
}
