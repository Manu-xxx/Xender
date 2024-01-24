open module com.swirlds.platform.test {
    requires transitive com.swirlds.base;
    requires transitive com.swirlds.common.test.fixtures;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.platform.core.test.fixtures;
    requires transitive com.swirlds.platform.core;
    requires com.swirlds.config.api.test.fixtures;
    requires com.swirlds.config.api;
    requires com.swirlds.logging;
    requires com.swirlds.metrics.api;
    requires com.swirlds.config.extensions.test.fixtures;
    requires java.desktop;
    requires org.apache.logging.log4j;
    requires org.junit.jupiter.api;
    requires org.mockito;
    requires static com.github.spotbugs.annotations;
}
