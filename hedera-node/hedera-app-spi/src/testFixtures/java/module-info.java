module com.hedera.node.app.spi.fixtures {
    exports com.hedera.node.app.spi.fixtures;
    exports com.hedera.node.app.spi.fixtures.state;
    exports com.hedera.node.app.spi.fixtures.workflows;

    requires com.hedera.node.app.spi;
    requires com.hedera.node.hapi;
    requires com.hedera.pbj.runtime;
    requires org.assertj.core;
    requires com.github.spotbugs.annotations;

    // Temporarily needed until FakePreHandleContext can be removed
    requires com.hedera.node.app.service.token;
    requires com.swirlds.config;
}
