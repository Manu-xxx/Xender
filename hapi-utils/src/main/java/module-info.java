module com.hedera.services.hapi.utils {
    exports com.hederahashgraph.fee;
    exports com.hedera.services.contracts;
    exports com.hedera.services.ethereum;
    exports com.hedera.services.exports.recordstreaming;
    exports com.hedera.services.keys;
    exports com.hedera.services.legacy.proto.utils;
    exports com.hedera.services.sysfiles.serdes;
    exports com.hedera.services.sysfiles.domain.throttling;

    requires com.fasterxml.jackson.databind;
    requires com.google.protobuf;
    requires com.swirlds.common;
    requires org.apache.logging.log4j;
    requires jsr305;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.pkix;
    requires org.hyperledger.besu.secp256k1;
}
