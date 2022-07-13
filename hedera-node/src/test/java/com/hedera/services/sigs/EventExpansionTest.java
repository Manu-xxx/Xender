package com.hedera.services.sigs;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.sigs.order.SigReqsManager;
import com.hedera.services.txns.prefetch.PrefetchProcessor;
import com.hedera.services.txns.span.ExpandHandleSpan;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


@ExtendWith({ MockitoExtension.class, LogCaptureExtension.class })
class EventExpansionTest {
	@Mock
	private Event event;
	@Mock
	private PlatformTxnAccessor txnAccessor;
	@Mock
	private Cryptography engine;
	@Mock
	private SigReqsManager sigReqsManager;
	@Mock
	private ExpandHandleSpan expandHandleSpan;
	@Mock
	private PrefetchProcessor prefetchProcessor;

	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private EventExpansion subject;

	@BeforeEach
	void setUp() {
		subject = new EventExpansion(engine, sigReqsManager, expandHandleSpan, prefetchProcessor);
	}

	@Test
	void expandsAndSubmitsSigsForEachTransaction() throws InvalidProtocolBufferException {
		final var n = 3;
		givenNTransactions(n);
		given(expandHandleSpan.track(any())).willReturn(txnAccessor);

		subject.expandAllSigs(event);

		verify(prefetchProcessor, times(n)).submit(txnAccessor);
		verify(sigReqsManager, times(n)).expandSigsInto(txnAccessor);
		verify(engine, times(n)).verifyAsync(Collections.emptyList());
	}

	@Test
	void warnsOfNonGrpcTransaction() throws InvalidProtocolBufferException {
		givenNTransactions(1);

		willThrow(InvalidProtocolBufferException.class).given(expandHandleSpan).track(any());

		subject.expandAllSigs(event);

		assertThat(logCaptor.warnLogs(), contains(startsWith("Event contained a non-GRPC transaction")));
	}

	@Test
	void warnsOfExpansionFailure() throws InvalidProtocolBufferException {
		givenNTransactions(1);
		given(expandHandleSpan.track(any())).willReturn(txnAccessor);

		willThrow(IllegalStateException.class).given(sigReqsManager).expandSigsInto(any());

		subject.expandAllSigs(event);

		assertThat(logCaptor.warnLogs(), contains(startsWith("Unable to expand signatures, will be verified " +
				"synchronously in handleTransaction")));
	}

	@SuppressWarnings("unchecked")
	private void givenNTransactions(final int n) {
		Mockito.doAnswer(invocationOnMock -> {
			final var consumer = (Consumer<Transaction>) invocationOnMock.getArgument(0);
			for (int i = 0; i < n; i++) {
				consumer.accept(new SwirldTransaction());
			}
			return null;
		}).when(event).forEachTransaction(any());
	}
}