module com.hedera.node.config.test.fixtures {
    exports com.hedera.node.config.testfixtures;

    requires transitive com.swirlds.config.api;
    requires transitive com.swirlds.test.framework;
    requires com.hedera.node.app.hapi.utils;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.config;
    requires com.hedera.node.hapi;
    requires com.swirlds.common;
    requires com.swirlds.fchashmap;
    requires com.swirlds.merkledb;
    requires com.swirlds.platform.core;
    requires com.swirlds.virtualmap;
    requires static com.github.spotbugs.annotations;
}
