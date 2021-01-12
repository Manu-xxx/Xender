package com.hedera.services.bdd.suites.perf;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiPropertySource;

public class PerfTestLoadSettings {
	public static final int DEFAULT_TPS = 500;
	public static final int DEFAULT_TOLERANCE_PERCENTAGE = 5;
	public static final int DEFAULT_MINS = 1;
	public static final int DEFAULT_ALLOWED_SECS_BELOW = 60;
	public static final int DEFAULT_BURST_SIZE = 5;
	public static final int DEFAULT_THREADS = 50;
	public static final int DEFAULT_SUBMIT_MESSAGE_SIZE = 256;
	public static final int DEFAULT_SUBMIT_MESSAGE_SIZE_VAR = 64;
	// By default, it will fall back to original test scenarios
	public static final int DEFAULT_TOTAL_TEST_ACCOUNTS = 2;
	public static final int DEFAULT_TOTAL_TEST_TOPICS = 1;
	public static final int DEFAULT_TOTAL_TEST_TOKENS = 1;
	public static final int DEFAULT_TOTAL_TEST_TOKEN_ACCOUNTS = 2;
	public static final int DEFAULT_TEST_TREASURE_START_ACCOUNT = 1001;
	public static final int DEFAULT_TOTAL_CLIENTS = 1;

	private int tps = DEFAULT_TPS;
	private int tolerancePercentage = DEFAULT_TOLERANCE_PERCENTAGE;
	private int mins = DEFAULT_MINS;
	private int allowedSecsBelow = DEFAULT_ALLOWED_SECS_BELOW;
	private int burstSize = DEFAULT_BURST_SIZE;
	private int threads = DEFAULT_THREADS;
	private int hcsSubmitMessageSize = DEFAULT_SUBMIT_MESSAGE_SIZE;
	private int hcsSubmitMessageSizeVar = DEFAULT_SUBMIT_MESSAGE_SIZE_VAR;
	private int totalTestAccounts = DEFAULT_TOTAL_TEST_ACCOUNTS;
	private int totalTestTopics = DEFAULT_TOTAL_TEST_TOPICS;
	private int totalTestTokens = DEFAULT_TOTAL_TEST_TOKENS;
	private int totalTestTokenAccounts = DEFAULT_TOTAL_TEST_TOKEN_ACCOUNTS;
	private int testTreasureStartAccount = DEFAULT_TEST_TREASURE_START_ACCOUNT;
	// This is only needed for running HTS performance regression tests to setup the context
	private int totalClients = DEFAULT_TOTAL_CLIENTS;

	private HapiPropertySource ciProps = null;

	public PerfTestLoadSettings() {
	}

	public PerfTestLoadSettings(int tps, int mins, int threads) {
		this.tps = tps;
		this.mins = mins;
		this.threads = threads;
	}

	public int getTps() {
		return tps;
	}

	public int getTotalClients() {
		return totalClients;
	}

	public int getTolerancePercentage() {
		return tolerancePercentage;
	}

	public int getMins() {
		return mins;
	}

	public int getAllowedSecsBelow() {
		return allowedSecsBelow;
	}

	public int getBurstSize() {
		return burstSize;
	}

	public int getThreads() {
		return threads;
	}

	public int getHcsSubmitMessageSize() {
		return hcsSubmitMessageSize;
	}

	public int getHcsSubmitMessageSizeVar() {
		return hcsSubmitMessageSizeVar;
	}

	public int getTotalAccounts() {
		return totalTestAccounts;
	}
	public int getTotalTopics() {
		return totalTestTopics;
	}
	public int getTotalTokens() {
		return totalTestTokens;
	}
	public int getTotalTestTokenAccounts() { return totalTestTokenAccounts; }
	public int getTestTreasureStartAccount() { return testTreasureStartAccount; }
	public int getIntProperty(String property, int defaultValue) {
		if (null != ciProps && ciProps.has(property)) {
			return ciProps.getInteger(property);
		}
		return defaultValue;
	}

	public boolean getBooleanProperty(String property, boolean defaultValue) {
		if (null != ciProps && ciProps.has(property)) {
			return ciProps.getBoolean(property);
		}
		return defaultValue;
	}

	public void setFrom(HapiPropertySource ciProps) {
		this.ciProps = ciProps;
		if (ciProps.has("tps")) {
			tps = ciProps.getInteger("tps");
		}
		if (ciProps.has("totalClients")) {
			totalClients = ciProps.getInteger("totalClients");
		}
		if (ciProps.has("mins")) {
			mins = ciProps.getInteger("mins");
		}
		if (ciProps.has("tolerance")) {
			tolerancePercentage = ciProps.getInteger("tolerancePercentage");
		}
		if (ciProps.has("burstSize")) {
			burstSize = ciProps.getInteger("burstSize");
		}
		if (ciProps.has("allowedSecsBelow")) {
			allowedSecsBelow = ciProps.getInteger("allowedSecsBelow");
		}
		if (ciProps.has("threads")) {
			threads = ciProps.getInteger("threads");
		}
		if (ciProps.has("totalTestAccounts")) {
			totalTestAccounts = ciProps.getInteger("totalTestAccounts");
		}
		if (ciProps.has("totalTestTopics")) {
			totalTestTopics = ciProps.getInteger("totalTestTopics");
		}
		if (ciProps.has("totalTestTokens")) {
			totalTestTokens = ciProps.getInteger("totalTestTokens");
		}
		if (ciProps.has("totalTestTokenAccounts")) {
			totalTestTokenAccounts = ciProps.getInteger("totalTestTokenAccounts");
		}
		if (ciProps.has("testTreasureStartAccount")) {
			testTreasureStartAccount = ciProps.getInteger("testTreasureStartAccount");
		}
		if (ciProps.has("messageSize")) {
			hcsSubmitMessageSize = ciProps.getInteger("messageSize");
		}
		if (ciProps.has("messageSizeVar")) {
			hcsSubmitMessageSize = ciProps.getInteger("messageSizeVar");
		}
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("tps", tps)
				.add("totalClients", totalClients)
				.add("mins", mins)
				.add("tolerance", tolerancePercentage)
				.add("burstSize", burstSize)
				.add("allowedSecsBelow", allowedSecsBelow)
				.add("threads", threads)
				.add("totalTestAccounts", totalTestAccounts)
				.add("totalTestTopics", totalTestTopics)
				.add("totalTestTokens", totalTestTokens)
				.add("testActiveTokenAccounts", totalTestTokenAccounts)
				.add("testTreasureStartAccount", testTreasureStartAccount)
				.add("submitMessageSize", hcsSubmitMessageSize)
				.add("submitMessageSizeVar", hcsSubmitMessageSizeVar)
				.toString();
	}
}
