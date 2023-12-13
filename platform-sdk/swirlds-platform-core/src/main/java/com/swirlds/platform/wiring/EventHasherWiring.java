package com.swirlds.platform.wiring;

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.hashing.EventHasher;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link EventHasher}.
 *
 * @param eventInput    the input wire for events to be hashed
 * @param eventOutput   the output wire for hashed events
 * @param flushRunnable the runnable to flush the hasher
 */
public record EventHasherWiring(@NonNull InputWire<GossipEvent> eventInput,
                                @NonNull OutputWire<GossipEvent> eventOutput, @NonNull Runnable flushRunnable) {
    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    public static EventHasherWiring create(@NonNull final TaskScheduler<GossipEvent> taskScheduler) {
        return new EventHasherWiring(taskScheduler.buildInputWire("events to hash"), taskScheduler.getOutputWire(), taskScheduler::flush);
    }

    /**
     * Bind an event hasher to this wiring.
     *
     * @param hasher the event hasher to bind
     */
    public void bind(@NonNull final EventHasher hasher) {
        ((BindableInputWire<GossipEvent, GossipEvent>) eventInput).bind(hasher::hashEvent);
    }
}
