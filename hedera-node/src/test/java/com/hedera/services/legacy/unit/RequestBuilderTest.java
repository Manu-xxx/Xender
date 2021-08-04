package com.hedera.services.legacy.unit;

/*-
 * ‌
 * Hedera Services Node
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

import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.builder.RequestBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

/**
 * @author Akshay
 * @Date : 8/10/2018
 */
public class RequestBuilderTest {

  @Test
  public void testExpirationTime() {
    Duration duration = RequestBuilder.getDuration(500);
    Timestamp expirationTime = RequestBuilder.getExpirationTime(Instant.now(), duration);
    Assertions.assertNotNull(expirationTime);
    Instant timeStamp = RequestBuilder.convertProtoTimeStamp(expirationTime);
  }
}
