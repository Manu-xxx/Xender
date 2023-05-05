module com.hedera.node.app.service.util {
    exports com.hedera.node.app.service.util;
    exports com.hedera.node.app.service.util.records;

    uses com.hedera.node.app.service.util.UtilService;

    requires transitive com.hedera.node.app.spi;
    requires com.github.spotbugs.annotations;
    requires com.hedera.pbj.runtime;
}
