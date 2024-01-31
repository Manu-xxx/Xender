module com.hedera.node.test.clients {
    exports com.hedera.services.bdd.spec.utilops.records;

    requires transitive com.hedera.node.hapi;
    requires transitive com.fasterxml.jackson.annotation;
    requires transitive com.google.protobuf;
    requires transitive com.swirlds.common;
    requires transitive info.picocli;
    requires transitive org.apache.commons.io;
    requires transitive org.junit.jupiter.api;
    requires transitive org.junit.platform.commons;
    requires transitive org.junit.platform.engine;
    requires transitive org.testcontainers;
    requires transitive org.yaml.snakeyaml;
    requires com.hedera.node.app.hapi.fees;
    requires com.hedera.node.app.hapi.utils;
    requires com.hedera.node.app.service.contract.impl;
    requires com.hedera.node.app.service.evm;
    requires com.hedera.node.app;
    requires com.hedera.node.config;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.github.docker.java.api;
    requires com.google.common;
    requires com.hedera.pbj.runtime;
    requires com.swirlds.base;
    requires com.swirlds.config.api;
    requires com.swirlds.platform.core;
    requires com.swirlds.test.framework;
    requires grpc.netty;
    requires grpc.stub;
    requires headlong;
    requires io.grpc;
    requires io.netty.handler;
    requires java.net.http;
    requires net.i2p.crypto.eddsa;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires org.bouncycastle.provider;
    requires org.hyperledger.besu.datatypes;
    requires org.hyperledger.besu.evm;
    requires org.hyperledger.besu.internal.crypto;
    requires org.json;
    requires org.opentest4j;
    requires tuweni.bytes;
    requires tuweni.units;
    requires static com.github.spotbugs.annotations;
}
