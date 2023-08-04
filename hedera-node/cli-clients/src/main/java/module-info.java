module com.hedera.node.services.cli {
    exports com.hedera.services.cli.sign;
    exports com.hedera.services.cli.signedstate;

    requires transitive com.swirlds.cli;
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.platform;
    requires transitive info.picocli;
    requires com.google.common;
    requires com.google.protobuf;
    requires com.hedera.node.app.hapi.utils;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.node.hapi;
    requires com.swirlds.config;
    requires com.swirlds.virtualmap;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires tuweni.bytes;
    requires tuweni.units;
    requires static com.github.spotbugs.annotations;
}
