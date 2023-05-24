module com.hedera.node.app.service.token.impl.test {
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.app.service.token.impl;
    requires org.junit.jupiter.api;
    requires com.google.protobuf;
    requires com.hedera.node.app.service.mono;
    requires org.mockito;
    requires org.mockito.junit.jupiter;
    requires org.apache.commons.lang3;
    requires com.hedera.node.app.service.mono.testFixtures;
    requires org.hyperledger.besu.datatypes;
    requires org.assertj.core;
    requires org.hamcrest;
    requires com.swirlds.common;
    requires org.bouncycastle.provider;
    requires com.hedera.node.app.service.evm;
    requires com.hedera.node.hapi;
    requires com.hedera.pbj.runtime;
    requires com.hedera.node.app.spi.fixtures;
    requires static com.github.spotbugs.annotations;
    requires com.swirlds.merkle;
    requires com.hedera.node.app;
    requires com.swirlds.config;
    requires com.hedera.node.config;

    opens com.hedera.node.app.service.token.impl.test.util to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.token.impl.test.codec to
            org.junit.platform.commons;
    opens com.hedera.node.app.service.token.impl.test to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.token.impl.test.handlers to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.token.impl.test.records to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.token.impl.test.config to
            org.junit.platform.commons,
            org.mockito;
    opens com.hedera.node.app.service.token.impl.test.validators to
            org.junit.platform.commons,
            org.mockito;
}
