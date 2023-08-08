open module com.swirlds.common.testing {
    exports com.swirlds.common.test.merkle.util;
    exports com.swirlds.common.test.merkle.dummy;
    exports com.swirlds.common.test.dummy;
    exports com.swirlds.common.test.benchmark;
    exports com.swirlds.common.test.set;
    exports com.swirlds.common.test.map;

    requires transitive com.swirlds.common;
    requires java.scripting;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j;
    requires org.junit.jupiter.api;
    requires static com.github.spotbugs.annotations;
}
