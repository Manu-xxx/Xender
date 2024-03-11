package com.swirlds.platform.core.jmh;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 10)
public class EventSerialization {

    @Param({"0"})
    public long seed;

    private GossipEvent event;
    private MerkleDataOutputStream outStream;
    private MerkleDataInputStream inStream;

    @Setup
    public void setup() throws IOException, ConstructableRegistryException {
        event = TestingEventBuilder.builder().setNumberOfSystemTransactions(1).setSeed(seed).buildEvent();
        StaticSoftwareVersion.setSoftwareVersion(event.getHashedData().getSoftwareVersion());
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds.platform.system");
        final PipedInputStream inputStream = new PipedInputStream();
        final PipedOutputStream outputStream = new PipedOutputStream(inputStream);
        outStream = new MerkleDataOutputStream(outputStream);
        inStream = new MerkleDataInputStream(inputStream);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void serializeDeserialize(final Blackhole bh) throws IOException {
        // results on Lazar's M1 Max MacBook Pro:
        //
        // Benchmark                                (seed)   Mode  Cnt    Score    Error   Units
        // EventSerialization.serializeDeserialize       0  thrpt    3  962.486 ± 29.252  ops/ms
        outStream.writeSerializable(event, false);
        bh.consume(inStream.readSerializable(false, GossipEvent::new));
    }
}
