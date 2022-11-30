module com.hedera.node.app.service.mono {
    requires com.hedera.hashgraph.protobuf.java.api;
    requires com.swirlds.common;
    requires dagger;
    requires javax.inject;
    requires com.hedera.node.app.spi;
    requires com.google.protobuf;
    requires com.google.common;
    requires org.apache.logging.log4j;
    requires com.hedera.node.app.hapi.utils;
    requires com.swirlds.merkle;
    requires com.swirlds.virtualmap;
    requires tuweni.bytes;
    requires org.hyperledger.besu.datatypes;
    requires org.hyperledger.besu.evm;
    requires static com.github.spotbugs.annotations;
    requires org.apache.commons.codec;
    requires com.swirlds.fchashmap;
    requires com.swirlds.jasperdb;
    requires com.swirlds.platform;
    requires org.apache.commons.lang3;
    requires com.hedera.node.app.service.token;
    requires com.hedera.node.app.service.evm;
    requires com.swirlds.fcqueue;
    requires com.hedera.node.app.hapi.fees;
    requires headlong;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.swirlds.logging;
    requires org.bouncycastle.provider;
    requires tuweni.units;
}
