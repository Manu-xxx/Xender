package com.swirlds.common.test.metrics.extensions;

import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.extensions.BusyTime;
import com.swirlds.common.metrics.extensions.DefaultMetricConfig;
import com.swirlds.common.metrics.platform.DefaultIntegerPairAccumulator;
import com.swirlds.common.metrics.platform.DefaultMetric;
import com.swirlds.common.metrics.platform.Snapshot;
import com.swirlds.common.test.fixtures.FakeTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BusyTimeTest {
	private final FakeTime clock = new FakeTime(Instant.EPOCH, Duration.ZERO);
	private BusyTime metric;

	@BeforeEach
	void reset() {
		clock.reset();
		final Metrics metrics = mock(Metrics.class);
		when(metrics.getOrCreate(any())).thenAnswer((Answer<IntegerPairAccumulator<Double>>) invocation -> {
			final IntegerPairAccumulator.Config<Double> config = invocation.getArgument(0);
			return new DefaultIntegerPairAccumulator<>(config);
		});
		metric = new BusyTime(metrics, new DefaultMetricConfig("a", "b", "c"), clock);
	}

	/**
	 * Example from the diagram in the documentation
	 */
	@Test
	void diagramExample() {
		// time == 1
		assertEquals(0.0, metric.getBusyFraction(), "initially, the busy fraction should be 0");

		clock.tick(Duration.ofSeconds(1)); // time == 2
		assertEquals(0.0, metric.getBusyFraction(), "no work has started yet, so the busy fraction should be 0");
		metric.startingWork();
		assertEquals(0.0, metric.getBusyFraction(),
				"work has started, but not time has elapsed yet, so the busy fraction should be 0");

		clock.tick(Duration.ofSeconds(1)); // time == 3
		assertEquals(0.5, metric.getBusyFraction(),
				"1 second of work, 1 second of idle, so the busy fraction should be 0.5");
		metric.finishedWork();
		assertEquals(0.5, metric.getBusyFraction(), "no time has elapsed, so the value should still be 0.5");

		clock.tick(Duration.ofSeconds(1)); // time == 4
		assertEquals(0.33, metric.getBusyFraction(), 0.01,
				"1 second of work, 2 seconds of idle, so the busy fraction should be 0.33");

		clock.tick(Duration.ofSeconds(1)); // time == 5
		assertEquals(0.25, metric.getBusyFraction(), 0.01,
				"1 second of work, 3 seconds of idle, so the busy fraction should be 0.25");

		clock.tick(Duration.ofSeconds(1)); // time == 6
		assertEquals(0.20, metric.getBusyFraction(), 0.01,
				"1 second of work, 4 seconds of idle, so the busy fraction should be 0.20");
		metric.startingWork();
		assertEquals(0.20, metric.getBusyFraction(), "no time has elapsed, so the value should still be 0.2");

		clock.tick(Duration.ofSeconds(1)); // time == 7
		assertEquals(0.33, metric.getBusyFraction(), 0.01,
				"2 second of work, 4 seconds of idle, so the busy fraction should be 0.33");

		clock.tick(Duration.ofSeconds(1)); // time == 8
		assertEquals(0.43, metric.getBusyFraction(), 0.01,
				"3 second of work, 4 seconds of idle, so the busy fraction should be 0.43");
		assertEquals(0.43, snapshot(), 0.01,
				"the snapshot should contain the same value returned by getBusyFraction()");
		assertEquals(0.0, metric.getBusyFraction(), "the snapshotting should reset the value");

		clock.tick(Duration.ofSeconds(1)); // time == 9
		assertEquals(1.0, metric.getBusyFraction(),
				"work has been ongoing since the reset, so the busy fraction should be 1");
	}

	@Test
	void noUpdateBetweenSnapshots() {
		clock.tick(Duration.ofSeconds(10));
		assertEquals(0.0, snapshot(), "no work has been done, expect 0");
		clock.tick(Duration.ofSeconds(5));
		metric.startingWork();
		clock.tick(Duration.ofSeconds(5));
		assertEquals(0.5, snapshot(), "half the time was spend doing work, expect 0.5");
		clock.tick(Duration.ofSeconds(10));
		assertEquals(1.0, snapshot(), "all the time was spent doing work, expect 1");
	}

	@Test
	void badUpdates() {
		clock.tick(Duration.ofSeconds(2));
		metric.startingWork();
		clock.tick(Duration.ofSeconds(1));
		// starting work again without finishing the previous work
		metric.startingWork();
		clock.tick(Duration.ofSeconds(1));
		assertEquals(0.5, snapshot(), "the second startingWork() should be ignored, so 0.5 is expected");
		clock.tick(Duration.ofSeconds(1));
		metric.finishedWork();
		clock.tick(Duration.ofSeconds(1));
		metric.finishedWork();
		clock.tick(Duration.ofSeconds(1));
		assertEquals(0.33, snapshot(), 0.01, "the second finishedWork() should be ignored, so 0.33 is expected");
	}

	private double snapshot() {
		final List<Snapshot.SnapshotEntry> snapshotEntries = ((DefaultMetric) metric.getAccumulator()).takeSnapshot();
		assertEquals(1, snapshotEntries.size(), "there should be 1 entry in the snapshot");
		final Snapshot.SnapshotEntry snapshotEntry = snapshotEntries.get(0);
		assertEquals(Metric.ValueType.VALUE, snapshotEntry.valueType());
		assertTrue(snapshotEntry.value() instanceof Double, "the snapshot should contain a double");
		return (Double) snapshotEntry.value();
	}
}
