module com.hedera.node.test.clients {
    exports com.hedera.services.bdd.spec.utilops.records;
    exports com.hedera.services.bdd.spec.utilops.inventory;
    exports com.hedera.services.bdd.suites;
    exports com.hedera.services.bdd.suites.utils.sysfiles.serdes;
    exports com.hedera.services.bdd.spec;
    exports com.hedera.services.bdd.spec.persistence;
    exports com.hedera.services.bdd.spec.infrastructure;
    exports com.hedera.services.bdd.spec.props;
    exports com.hedera.services.bdd.spec.queries;
    exports com.hedera.services.bdd.spec.queries.file;
    exports com.hedera.services.bdd.spec.queries.meta;
    exports com.hedera.services.bdd.spec.queries.token;
    exports com.hedera.services.bdd.spec.queries.crypto;
    exports com.hedera.services.bdd.spec.queries.schedule;
    exports com.hedera.services.bdd.spec.queries.consensus;
    exports com.hedera.services.bdd.spec.queries.contract;
    exports com.hedera.services.bdd.spec.transactions;
    exports com.hedera.services.bdd.spec.utilops;
    exports com.hedera.services.bdd.suites.meta;
    exports com.hedera.services.bdd.spec.keys.deterministic;
    exports com.hedera.services.bdd.spec.transactions.file;
    exports com.hedera.services.bdd.spec.transactions.token;
    exports com.hedera.services.bdd.spec.keys;
    exports com.hedera.services.bdd.spec.transactions.crypto;
    exports com.hedera.services.bdd.spec.transactions.schedule;
    exports com.hedera.services.bdd.spec.transactions.consensus;
    exports com.hedera.services.bdd.spec.transactions.contract;
    exports com.hedera.services.bdd.spec.transactions.system;
    exports com.hedera.services.bdd.suites.crypto.staking;
    exports com.hedera.services.bdd.spec.fees;
    exports com.hedera.services.bdd.spec.verification.traceability;
    exports com.hedera.services.bdd.spec.assertions;
    exports com.hedera.services.bdd.spec.assertions.matchers;

    requires transitive com.hedera.node.app.hapi.fees;
    requires transitive com.hedera.node.app.hapi.utils;
    requires transitive com.hedera.node.hapi;
    requires transitive com.fasterxml.jackson.annotation;
    requires transitive com.google.common;
    requires transitive com.google.protobuf;
    requires transitive com.swirlds.common;
    requires transitive headlong;
    requires transitive info.picocli;
    requires transitive io.grpc;
    requires transitive net.i2p.crypto.eddsa;
    requires transitive org.apache.commons.io;
    requires transitive org.apache.logging.log4j;
    requires transitive org.junit.jupiter.api;
    requires transitive org.junit.platform.commons;
    requires transitive org.junit.platform.engine;
    requires transitive org.testcontainers;
    requires transitive org.yaml.snakeyaml;
    requires transitive tuweni.bytes;
    requires com.hedera.node.app.service.contract.impl;
    requires com.hedera.node.app.service.evm;
    requires com.hedera.node.app;
    requires com.hedera.node.config;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.github.docker.java.api;
    requires com.hedera.pbj.runtime;
    requires com.swirlds.base;
    requires com.swirlds.config.api;
    requires com.swirlds.config.extensions.test.fixtures;
    requires com.swirlds.platform.core;
    requires grpc.netty;
    requires grpc.stub;
    requires io.netty.handler;
    requires java.desktop;
    requires java.net.http;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j.core;
    requires org.bouncycastle.provider;
    requires org.hyperledger.besu.datatypes;
    requires org.hyperledger.besu.internal.crypto;
    requires org.json;
    requires org.opentest4j;
    requires tuweni.units;
    requires static com.github.spotbugs.annotations;
}
