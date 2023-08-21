package com.hedera.node.app.throttle;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.ThrottleBucket;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.hapi.node.transaction.ThrottleGroup;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(LogCaptureExtension.class)
class ThrottleManagerTest {

    ThrottleGroup throttleGroup = ThrottleGroup.newBuilder()
        .operations(List.of(HederaFunctionality.CRYPTO_CREATE, HederaFunctionality.FREEZE))
        .milliOpsPerSec(100)
        .build();

    ThrottleBucket throttleBucket = ThrottleBucket.newBuilder()
        .name("throttle1")
        .burstPeriodMs(100L)
        .throttleGroups(throttleGroup)
        .build();

    ThrottleGroup throttleGroup2 = ThrottleGroup.newBuilder()
        .operations(List.of(HederaFunctionality.CONTRACT_CREATE))
        .milliOpsPerSec(120)
        .build();

    ThrottleBucket throttleBucket2 = ThrottleBucket.newBuilder()
        .name("throttle2")
        .burstPeriodMs(120L)
        .throttleGroups(throttleGroup2)
        .build();

    ThrottleDefinitions throttleDefinitions = ThrottleDefinitions.newBuilder()
        .throttleBuckets(throttleBucket, throttleBucket2)
        .build();
    Bytes throttleDefinitionsByes = ThrottleDefinitions.PROTOBUF.toBytes(throttleDefinitions);

    @LoggingSubject
    ThrottleManager subject;

    @LoggingTarget
    private LogCaptor logCaptor;

    @BeforeEach
    void setUp() {
        subject = new ThrottleManager();
    }

    @Test
    void onUpdatedHasExpectedFields() {
        // when
        subject.update(throttleDefinitionsByes);

        // expect
        assertEquals(throttleDefinitions, subject.throttleDefinitions());
        assertEquals(throttleDefinitions.throttleBuckets(), subject.throttleBuckets());
    }

    @Test
    void defaultExpectedFields() {
        assertEquals(ThrottleDefinitions.DEFAULT, subject.throttleDefinitions());
        assertEquals(ThrottleDefinitions.DEFAULT.throttleBuckets(), subject.throttleBuckets());
    }

    @Test
    void passingNullOnUpdate() {
        // when
        subject.update(null);

        // expect
        assertThat(logCaptor.warnLogs(), hasItems(startsWith("Unable to parse the throttle file")));

        // default values are applied
        assertEquals(ThrottleDefinitions.DEFAULT, subject.throttleDefinitions());
        assertEquals(ThrottleDefinitions.DEFAULT.throttleBuckets(), subject.throttleBuckets());
    }

    @Test
    void passingInvalidBytesOnUpdate() {
        // when
        subject.update(Bytes.wrap(new byte[]{0x01}));

        // expect
        assertThat(logCaptor.warnLogs(), hasItems(startsWith("Unable to parse the throttle file")));

        // default values are applied
        assertEquals(ThrottleDefinitions.DEFAULT, subject.throttleDefinitions());
        assertEquals(ThrottleDefinitions.DEFAULT.throttleBuckets(), subject.throttleBuckets());
    }

}
