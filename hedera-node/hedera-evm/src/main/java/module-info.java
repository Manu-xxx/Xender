/** Provides the core interfaces for the Hedera EVM implementation. */
module com.hedera.node.app.service.evm {
    requires static transitive com.github.spotbugs.annotations;
    requires transitive com.swirlds.common;
    requires transitive org.hyperledger.besu.evm;
    requires transitive org.hyperledger.besu.datatypes;
    requires transitive org.hyperledger.besu.secp256k1;
    requires transitive com.hedera.hashgraph.protobuf.java.api;
    requires tuweni.bytes;
    requires tuweni.units;
    requires com.github.benmanes.caffeine;
    requires org.bouncycastle.provider;
    requires com.google.common;
    requires com.google.protobuf;
    requires org.apache.commons.lang3;
    requires com.sun.jna;

    exports com.hedera.node.app.service.evm.store.contracts.utils;
    exports com.hedera.node.app.service.evm.contracts.execution;
    exports com.hedera.node.app.service.evm.store.contracts;
    exports com.hedera.node.app.service.evm.store.models;
    exports com.hedera.node.app.service.evm;
    exports com.hedera.node.app.service.evm.accounts;
    exports com.hedera.node.app.service.evm.contracts.operations;
    exports com.hedera.node.app.service.evm.contracts.execution.traceability;
    exports com.hedera.node.app.service.evm.utils;
}
