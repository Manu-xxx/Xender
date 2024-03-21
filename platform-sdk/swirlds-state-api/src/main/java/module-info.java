module com.swirlds.state.api {
    exports com.swirlds.state;
    exports com.swirlds.state.spi;
    exports com.swirlds.state.spi.metrics;
    exports com.swirlds.state.spi.info;
    exports com.swirlds.state.spi.worfklows.record;

    requires transitive com.swirlds.config.api;
    requires transitive com.hedera.node.hapi;
    requires com.hedera.pbj.runtime;
    requires static transitive com.github.spotbugs.annotations;
}
