package com.swirlds.platform.recovery.emergencyfile;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.swirlds.common.jackson.InstantDeserializer;

import java.time.Instant;

/**
 * Data about the bootstrap state loaded during event recovery (the starting state)
 *
 * @param timestamp
 * 		the consensus timestamp of the bootstrap state
 */
public record Boostrap(
		@JsonSerialize(using = ToStringSerializer.class) @JsonDeserialize(using = InstantDeserializer.class)
		Instant timestamp) {
}
