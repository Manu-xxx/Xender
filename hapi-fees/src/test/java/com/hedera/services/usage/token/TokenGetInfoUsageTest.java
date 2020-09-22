package com.hedera.services.usage.token;

import com.hederahashgraph.api.proto.java.Query;

import static org.junit.Assert.*;
import com.hedera.services.test.IdUtils;
import com.hedera.services.test.KeyUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenGetInfoQuery;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Optional;

import static com.hedera.services.usage.token.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

@RunWith(JUnitPlatform.class)
public class TokenGetInfoUsageTest {
	Optional<Key> aKey = Optional.of(KeyUtils.A_COMPLEX_KEY);
	String name = "WhyWhyWhyWHY";
	String symbol = "OKITSFINE";

	TokenGetInfoUsage subject;

	@BeforeEach
	public void setup() {
		subject = TokenGetInfoUsage.newEstimate(tokenQuery());
	}

	@Test
	public void assessesEverything() {
		// given:
		subject.givenCurrentAdminKey(aKey)
				.givenCurrentFreezeKey(aKey)
				.givenCurrentWipeKey(aKey)
				.givenCurrentKycKey(aKey)
				.givenCurrentSupplyKey(aKey)
				.givenCurrentlyUsingAutoRenewAccount()
				.givenCurrentName(name)
				.givenCurrentSymbol(symbol);
		// and:
		var expectedKeyBytes = 5 * FeeBuilder.getAccountKeyStorageSize(aKey.get());
		var expectedBytes = expectedKeyBytes + TOKEN_ENTITY_SIZES.baseBytesUsed(symbol, name) + BASIC_ENTITY_ID_SIZE;

		// when:
		var usage = subject.get();

		// then:
		var node = usage.getNodedata();
		assertEquals(FeeBuilder.BASIC_QUERY_HEADER + symbol.length(), node.getBpt());
		assertEquals(expectedBytes, node.getBpr());
	}

	private Query tokenQuery() {
		var op = TokenGetInfoQuery.newBuilder()
				.setToken(IdUtils.asSymbolRef(symbol))
				.build();
		return Query.newBuilder().setTokenGetInfo(op).build();
	}
}