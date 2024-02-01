/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
 *
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
 */

package com.swirlds.merkledb.files.hashmap;

import static com.swirlds.merkledb.files.hashmap.HalfDiskHashMap.SPECIAL_DELETE_ME_VALUE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.test.fixtures.ExampleLongKeyFixedSize;
import com.swirlds.merkledb.test.fixtures.ExampleLongKeyVariableSize;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings({"RedundantCast", "unchecked", "rawtypes"})
class BucketTest {

    private enum KeyType {
        fixed(ExampleLongKeyFixedSize::new, new ExampleLongKeyFixedSize.Serializer()),
        variable(ExampleLongKeyVariableSize::new, new ExampleLongKeyVariableSize.Serializer());

        final Function<Long, VirtualLongKey> keyConstructor;
        final KeySerializer<VirtualLongKey> keySerializer;

        KeyType(Function<Long, VirtualLongKey> keyConstructor, final KeySerializer keySerializer) {
            this.keyConstructor = keyConstructor;
            this.keySerializer = (KeySerializer<VirtualLongKey>) ((Object) keySerializer);
        }
    }

    @ParameterizedTest
    @EnumSource(KeyType.class)
    void canSetAndGetBucketIndex(KeyType keyType) {
        final Bucket<VirtualLongKey> subject = new Bucket<>(keyType.keySerializer);
        final int pretendIndex = 123;
        subject.setBucketIndex(pretendIndex);
        assertEquals(pretendIndex, subject.getBucketIndex(), "Should be able to get the set index");
    }

    @ParameterizedTest
    @EnumSource(KeyType.class)
    void returnsNotFoundValueFromEmptyBucket(KeyType keyType) throws IOException {
        final Bucket<VirtualLongKey> subject = new Bucket<>(keyType.keySerializer);
        final long notFoundValue = 123;
        final ExampleLongKeyFixedSize missingKey = new ExampleLongKeyFixedSize(321);

        assertEquals(
                notFoundValue,
                subject.findValue(missingKey.hashCode(), missingKey, notFoundValue),
                "Should not find a value in an empty bucket");
    }

    @ParameterizedTest
    @EnumSource(KeyType.class)
    void testBucketAddAndDelete(KeyType keyType) throws IOException {
        // create some keys to test with
        final VirtualLongKey[] testKeys = new VirtualLongKey[20];
        for (int i = 0; i < testKeys.length; i++) {
            //			testKeys[i] = new ExampleLongKeyFixedSize(i+10);
            testKeys[i] = keyType.keyConstructor.apply((long) (i + 10));
        }
        // create a bucket
        final Bucket<VirtualLongKey> bucket = new Bucket<>(keyType.keySerializer);
        assertEquals(0, bucket.getBucketEntryCount(), "Check we start with empty bucket");
        // insert keys and check
        for (int i = 0; i < 10; i++) {
            final VirtualLongKey key = testKeys[i];
            bucket.putValue(key, key.getKeyAsLong() + 100);
            assertEquals(i + 1, bucket.getBucketEntryCount(), "Check we have correct count");
            // check that all keys added so far are there
            for (int j = 0; j <= i; j++) {
                checkKey(bucket, testKeys[j]);
            }
        }
        assertEquals(10, bucket.getBucketEntryCount(), "Check we have correct count");
        // now update, check and put back
        bucket.putValue(testKeys[5], 1234);
        assertEquals(
                1234, bucket.findValue(testKeys[5].hashCode(), testKeys[5], -1), "Should get expected value of 1234");
        bucket.putValue(testKeys[5], 115);
        for (int j = 0; j < 10; j++) {
            checkKey(bucket, testKeys[j]);
        }
        // now delete last key and check
        bucket.putValue(testKeys[9], SPECIAL_DELETE_ME_VALUE);
        assertEquals(9, bucket.getBucketEntryCount(), "Check we have correct count");
        for (int j = 0; j < 9; j++) {
            checkKey(bucket, testKeys[j]);
        }
        assertEquals(
                -1,
                bucket.findValue(testKeys[9].hashCode(), testKeys[9], -1),
                "Should not find entry 10 any more we deleted it");
        // now delete a middle, index 5
        bucket.putValue(testKeys[5], SPECIAL_DELETE_ME_VALUE);
        assertEquals(8, bucket.getBucketEntryCount(), "Check we have correct count");
        for (int j = 0; j < 5; j++) {
            checkKey(bucket, testKeys[j]);
        }
        for (int j = 6; j < 9; j++) {
            checkKey(bucket, testKeys[j]);
        }
        assertEquals(
                -1,
                bucket.findValue(testKeys[5].hashCode(), testKeys[5], -1),
                "Should not find entry 5 any more we deleted it");
        // now delete first, index 0
        bucket.putValue(testKeys[0], SPECIAL_DELETE_ME_VALUE);
        assertEquals(7, bucket.getBucketEntryCount(), "Check we have correct count");
        for (int j = 1; j < 5; j++) {
            checkKey(bucket, testKeys[j]);
        }
        for (int j = 6; j < 9; j++) {
            checkKey(bucket, testKeys[j]);
        }
        assertEquals(
                -1,
                bucket.findValue(testKeys[0].hashCode(), testKeys[0], -1),
                "Should not find entry 0 any more we deleted it");
        // add two more entries and check
        bucket.putValue(testKeys[10], 120);
        bucket.putValue(testKeys[11], 121);
        assertEquals(9, bucket.getBucketEntryCount(), "Check we have correct count");
        for (int j = 1; j < 5; j++) {
            checkKey(bucket, testKeys[j]);
        }
        for (int j = 6; j < 9; j++) {
            checkKey(bucket, testKeys[j]);
        }
        for (int j = 10; j < 12; j++) {
            checkKey(bucket, testKeys[j]);
        }
        // put 0, 5 and 9 back in, and check we have full range
        bucket.putValue(testKeys[0], 110);
        bucket.putValue(testKeys[5], 115);
        bucket.putValue(testKeys[9], 119);
        assertEquals(12, bucket.getBucketEntryCount(), "Check we have correct count");
        for (int j = 0; j < 12; j++) {
            checkKey(bucket, testKeys[j]);
        }
    }

    @ParameterizedTest
    @EnumSource(KeyType.class)
    void testBucketGrowing(KeyType keyType) {
        // create some keys to test with
        final VirtualLongKey[] testKeys = new VirtualLongKey[420];
        for (int i = 0; i < testKeys.length; i++) {
            //			testKeys[i] = new ExampleLongKeyFixedSize(i+10);
            testKeys[i] = keyType.keyConstructor.apply((long) (i + 10));
        }
        // create a bucket
        final Bucket<VirtualLongKey> bucket = new Bucket<>(keyType.keySerializer);
        assertEquals(0, bucket.getBucketEntryCount(), "Check we start with empty bucket");
        // insert keys and check
        for (int i = 0; i < testKeys.length; i++) {
            final VirtualLongKey key = testKeys[i];
            bucket.putValue(key, key.getKeyAsLong() + 100);
            assertEquals(i + 1, bucket.getBucketEntryCount(), "Check we have correct count");
            // check that all keys added so far are there
            for (int j = 0; j <= i; j++) {
                checkKey(bucket, testKeys[j]);
            }
        }
        assertEquals(testKeys.length, bucket.getBucketEntryCount(), "Check we have correct count");
    }

    @ParameterizedTest
    @EnumSource(KeyType.class)
    void testBucketImportExportClear(KeyType keyType) throws IOException {
        // create some keys to test with
        final VirtualLongKey[] testKeys = new VirtualLongKey[50];
        for (int i = 0; i < testKeys.length; i++) {
            //			testKeys[i] = new ExampleLongKeyFixedSize(i+10);
            testKeys[i] = keyType.keyConstructor.apply((long) (i + 10));
        }
        // create a bucket
        final Bucket<VirtualLongKey> bucket = new Bucket<>(keyType.keySerializer);
        assertEquals(0, bucket.getBucketEntryCount(), "Check we start with empty bucket");
        // insert keys and check
        for (int i = 0; i < testKeys.length; i++) {
            final VirtualLongKey key = testKeys[i];
            bucket.putValue(key, key.getKeyAsLong() + 100);
            assertEquals(i + 1, bucket.getBucketEntryCount(), "Check we have correct count");
            // check that all keys added so far are there
            for (int j = 0; j <= i; j++) {
                checkKey(bucket, testKeys[j]);
            }
        }
        assertEquals(testKeys.length, bucket.getBucketEntryCount(), "Check we have correct count");
        // get raw bytes first to compare to
        final int size = bucket.sizeInBytes();
        final byte[] goodBytes = new byte[size];
        final BufferedData bucketData = BufferedData.wrap(goodBytes);
        bucket.writeTo(bucketData);
        final String goodBytesStr = Arrays.toString(goodBytes);
        // now test write to buffer
        final BufferedData bbuf = BufferedData.allocate(size);
        bucket.writeTo(bbuf);
        bbuf.flip();
        assertEquals(
                goodBytesStr, Arrays.toString(bbuf.getBytes(0, bbuf.length()).toByteArray()), "Expect bytes to match");

        // create new bucket with good bytes and check it is the same
        final Bucket<VirtualLongKey> bucket2 = new Bucket<>(keyType.keySerializer);
        bucket2.readFrom(BufferedData.wrap(goodBytes));
        assertEquals(bucket.toString(), bucket2.toString(), "Expect bucket toStrings to match");

        // test clear
        final Bucket<VirtualLongKey> bucket3 = new Bucket<>(keyType.keySerializer);
        bucket.clear();
        assertEquals(bucket3.toString(), bucket.toString(), "Expect bucket toStrings to match");
    }

    @Test
    void toStringAsExpectedForBucket() {
        final ExampleLongKeyFixedSize.Serializer keySerializer = new ExampleLongKeyFixedSize.Serializer();
        final Bucket<ExampleLongKeyFixedSize> bucket = new Bucket<>(keySerializer);

        final String emptyBucketRepr = "Bucket{bucketIndex=0, entryCount=0, size=5}";
        assertEquals(emptyBucketRepr, bucket.toString(), "Empty bucket should represent as expected");

        final String bucketWithIndex0Repr = "Bucket{bucketIndex=0, entryCount=0, size=5}";
        bucket.setBucketIndex(0);
        assertEquals(bucketWithIndex0Repr, bucket.toString(), "Empty bucket should represent as expected");

        final String bucketWithIndex1Repr = "Bucket{bucketIndex=1, entryCount=0, size=5}";
        bucket.setBucketIndex(1);
        assertEquals(bucketWithIndex1Repr, bucket.toString(), "Empty bucket should represent as expected");

        final ExampleLongKeyFixedSize key = new ExampleLongKeyFixedSize(2056);
        bucket.putValue(key, 5124);
        bucket.setBucketIndex(0);
        final String nonEmptyBucketRepr = "Bucket{bucketIndex=0, entryCount=1, size=31}";
        assertEquals(nonEmptyBucketRepr, bucket.toString(), "Non-empty bucket represent as expected");
    }

    @ParameterizedTest
    @EnumSource(KeyType.class)
    void emptyParsedBucketToBucketIndexZero(final KeyType keyType) throws IOException {
        final Bucket<VirtualLongKey> inBucket = new ParsedBucket<>(keyType.keySerializer);
        final VirtualLongKey key1 = keyType.keyConstructor.apply(1L);
        final VirtualLongKey key2 = keyType.keyConstructor.apply(2L);
        inBucket.setBucketIndex(0);
        inBucket.putValue(key1, 2);
        inBucket.putValue(key2, 1);
        final BufferedData buf = BufferedData.allocate(inBucket.sizeInBytes());
        inBucket.writeTo(buf);
        buf.reset();
        final Bucket<VirtualLongKey> outBucket = new Bucket<>(keyType.keySerializer);
        outBucket.readFrom(buf);
        outBucket.putValue(key1, SPECIAL_DELETE_ME_VALUE);
        outBucket.putValue(key2, SPECIAL_DELETE_ME_VALUE);
        assertDoesNotThrow(outBucket::getBucketIndex);
        assertDoesNotThrow(outBucket::getBucketEntryCount);
        assertEquals(0, outBucket.getBucketEntryCount());
        assertEquals(0, outBucket.getBucketIndex());
    }

    private void checkKey(Bucket<VirtualLongKey> bucket, VirtualLongKey key) {
        var findResult =
                assertDoesNotThrow(() -> bucket.findValue(key.hashCode(), key, -1), "No exception should be thrown");
        assertEquals(key.getKeyAsLong() + 100, findResult, "Should get expected value");
    }
}
