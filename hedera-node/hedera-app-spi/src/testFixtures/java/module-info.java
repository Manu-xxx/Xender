module hedera.services.hedera.node.hedera.app.spi.testFixtures {
    exports com.hedera.node.app.spi.fixtures.state;

    requires com.hedera.node.app.spi;
    requires com.hedera.node.hapi;
    requires com.hedera.pbj.runtime;
    requires static com.github.spotbugs.annotations;
}
