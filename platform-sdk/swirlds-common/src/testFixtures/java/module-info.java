open module com.swirlds.common.test.fixtures {
    exports com.swirlds.common.test.fixtures;
    exports com.swirlds.common.test.fixtures.benchmark;
    exports com.swirlds.common.test.fixtures.crypto;
    exports com.swirlds.common.test.fixtures.dummy;
    exports com.swirlds.common.test.fixtures.io;
    exports com.swirlds.common.test.fixtures.junit.tags;
    exports com.swirlds.common.test.fixtures.map;
    exports com.swirlds.common.test.fixtures.merkle.util;
    exports com.swirlds.common.test.fixtures.threading;
    exports com.swirlds.common.test.fixtures.set;
    exports com.swirlds.common.test.fixtures.stream;
    exports com.swirlds.common.test.fixtures.fcqueue;
    exports com.swirlds.common.test.fixtures.platform;

    requires transitive com.swirlds.base;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.metrics.api;
    requires transitive com.swirlds.platform.core;
    requires transitive org.junit.jupiter.api;
    requires com.swirlds.logging;
    requires lazysodium.java;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires static com.github.spotbugs.annotations;
}
