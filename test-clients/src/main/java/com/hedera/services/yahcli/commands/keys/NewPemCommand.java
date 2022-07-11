package com.hedera.services.yahcli.commands.keys;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.bdd.spec.keys.deterministic.Bip0032;
import com.hedera.services.bdd.spec.persistence.SpecKey;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.keys.Ed25519Utils;
import com.swirlds.common.utility.CommonUtils;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import static com.hedera.services.yahcli.config.ConfigUtils.ensureDir;
import static com.hedera.services.yahcli.config.ConfigUtils.setLogLevels;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

@CommandLine.Command(
		name = "gen-new",
		subcommands = { picocli.CommandLine.HelpCommand.class },
		description = "Generates a new key")
public class NewPemCommand implements Callable<Integer> {
	@CommandLine.ParentCommand
	private KeysCommand keysCommand;

	@CommandLine.Option(names = { "-p", "--path" },
			paramLabel = "path for new PEM",
			defaultValue = "keys/ed25519.pem")
	private String loc;

	@CommandLine.Option(names = { "-x", "--passphrase" },
			paramLabel = "passphrase for new PEM (will be randomly generated otherwise)")
	private String passphrase;

	@Override
	public Integer call() throws Exception {
		setLogLevels(keysCommand.getYahcli().getLogLevel());
		final var lastSepI = loc.lastIndexOf(File.separator);
		if (lastSepI != -1) {
			ensureDir(loc.substring(0, lastSepI));
		}

		final var mnemonic = SpecKey.randomMnemonic();
		final var seed = Bip0032.seedFrom(mnemonic);
		final var curvePoint = Bip0032.privateKeyFrom(seed);
		final EdDSAPrivateKey privateKey = Ed25519Utils.keyFrom(curvePoint);
		final var pubKey = privateKey.getAbyte();
		COMMON_MESSAGES.info("Generating a new key @ " + loc);
		final var hexedPubKey = CommonUtils.hex(pubKey);
		COMMON_MESSAGES.info(" - The public key is: " + hexedPubKey);
		if (passphrase == null) {
			passphrase = TxnUtils.randomAlphaNumeric(12);
		}
		Ed25519Utils.writeKeyTo(privateKey, loc, passphrase);
		final var passLoc = loc.replace(".pem", ".pass");
		Files.writeString(Paths.get(passLoc), passphrase + "\n");
		final var wordsLoc = loc.replace(".pem", ".words");
		Files.writeString(Paths.get(wordsLoc), mnemonic + "\n");
		COMMON_MESSAGES.info(" - Passphrase @ " + passLoc);
		COMMON_MESSAGES.info(" - Mnemonic form @ " + wordsLoc);

		return 0;
	}
}
