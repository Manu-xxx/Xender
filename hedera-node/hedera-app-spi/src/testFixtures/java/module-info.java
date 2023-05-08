module com.hedera.node.app.spi.fixtures {
    exports com.hedera.node.app.spi.fixtures;
    exports com.hedera.node.app.spi.fixtures.state;
    exports com.hedera.node.app.spi.fixtures.workflows;

    requires com.hedera.node.app.spi;
    requires com.hedera.node.hapi;
    requires com.hedera.pbj.runtime;
    requires com.swirlds.test.framework;
    requires com.swirlds.config;
    requires com.swirlds.common;
    requires org.assertj.core;
    requires static com.github.spotbugs.annotations;

    // Temporarily needed until FakePreHandleContext can be removed
    requires com.hedera.node.app.service.token;
}
