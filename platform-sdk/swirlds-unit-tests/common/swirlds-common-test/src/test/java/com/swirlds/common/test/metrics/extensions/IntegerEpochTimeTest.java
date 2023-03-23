package com.swirlds.common.test.metrics.extensions;

import com.swirlds.common.metrics.extensions.IntegerEpochTime;
import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.common.utility.Units;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class IntegerEpochTimeTest {
	private final FakeTime clock = new FakeTime(Instant.EPOCH, Duration.ZERO);
	private final IntegerEpochTime epochClock = new IntegerEpochTime(clock);

	@BeforeEach
	void reset() {
		clock.reset();
	}

	@Test
	void basic() {
		final int milliStart = epochClock.getMilliTime();
		final int microStart = epochClock.getMicroTime();
		assertEquals(0, epochClock.millisElapsed(milliStart), "the elapsed time should be 0");
		assertEquals(0, epochClock.microsElapsed(microStart), "the elapsed time should be 0");
		clock.tick(Duration.ofMillis(1));
		assertEquals(1, epochClock.millisElapsed(milliStart), "the elapsed time should be 1 millisecond");
		assertEquals(1000, epochClock.microsElapsed(microStart),
				"the elapsed time should be 1000 microseconds");
		clock.tick(Duration.ofMillis(10_000));
		assertEquals(10_001, epochClock.millisElapsed(milliStart), "the elapsed time should be 10_001 milliseconds");
		assertEquals(10_001_000, epochClock.microsElapsed(microStart),
				"the elapsed time should be 10_001_000 microseconds");
	}

	@Test
	void millisIntOverflow() {
		assertEquals(0, epochClock.getMilliTime(), "the clock should start at 0");
		// set the clock so that the milli epoch is just before the int overflow
		clock.tick(Duration.ofMillis(Integer.MAX_VALUE - 1));
		assertEquals(Integer.MAX_VALUE - 1, epochClock.getMilliTime(),
				"the metric clock should be just below max int");
		final int start = epochClock.getMilliTime();
		clock.tick(Duration.ofMillis(1));
		assertEquals(0, epochClock.getMilliTime(), "the clock should have overflown and should now be 0");
		assertEquals(1, epochClock.millisElapsed(start), "the elapsed time should be 1 millisecond");
		clock.tick(Duration.ofMillis(123));
		assertEquals(124, epochClock.millisElapsed(start), "the elapsed time should be 124 milliseconds");
	}

	/**
	 * Only about 24 days worth of milliseconds can be accurately represented by a signed int. This test
	 * verifies that the elapsed time is accurate for that period, and then that it is no longer accurate
	 */
	@Test
	void millisElapsedExceeded() {
		final int start = epochClock.getMilliTime();
		clock.tick(Duration.ofDays(24));
		assertEquals(Duration.ofDays(24).toMillis(), epochClock.millisElapsed(start),
				"the elapsed time should be accurate");
		clock.tick(Duration.ofDays(1));
		assertNotEquals(Duration.ofDays(25).toMillis(), epochClock.millisElapsed(start),
				"the elapsed time can no longer be accurate");
	}

	/**
	 * Only about 35 minutes worth of microseconds can be accurately represented by a signed int. This test
	 * verifies that the elapsed time is accurate for that period, and then that it is no longer accurate
	 */
	@Test
	void microsElapsedExceeded() {
		final int start = epochClock.getMicroTime();
		clock.tick(Duration.ofMinutes(35));
		assertEquals(Duration.ofMinutes(35).toNanos()/Units.MICROSECONDS_TO_NANOSECONDS,
				epochClock.microsElapsed(start),
				"the elapsed time should be accurate");
		clock.tick(Duration.ofMinutes(1));
		assertNotEquals(Duration.ofMinutes(36).toNanos()/Units.MICROSECONDS_TO_NANOSECONDS,
				epochClock.microsElapsed(start),
				"the elapsed time can no longer be accurate");
	}

	@Test
	void microsIntOverflow() {
		assertEquals(0, epochClock.getMicroTime(), "the clock should start at 0");
		// set the clock so that the micro epoch is just before the int overflow
		clock.tick((long) (Integer.MAX_VALUE - 1) * Units.MICROSECONDS_TO_NANOSECONDS);
		assertEquals(Integer.MAX_VALUE - 1, epochClock.getMicroTime(),
				"the metric clock should be just below max int");
		final int start = epochClock.getMicroTime();
		clock.tick(Units.MICROSECONDS_TO_NANOSECONDS);
		assertEquals(0, epochClock.getMicroTime(), "the clock should have overflown and should now be 0");
		assertEquals(1, epochClock.microsElapsed(start), "the elapsed time should be 1 microsecond");
		clock.tick(55 * Units.MICROSECONDS_TO_NANOSECONDS);
		assertEquals(56, epochClock.microsElapsed(start), "the elapsed time should be 56 microseconds");
	}
}
