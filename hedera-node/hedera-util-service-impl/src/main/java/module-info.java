import com.hedera.node.app.service.util.impl.UtilServiceImpl;

module com.hedera.node.app.service.util.impl {
    requires com.hedera.node.app.service.util;
    requires com.github.spotbugs.annotations;
    requires javax.inject;
    requires dagger;

    provides com.hedera.node.app.service.util.UtilService with
            UtilServiceImpl;

    exports com.hedera.node.app.service.util.impl to
            com.hedera.node.app,
            com.hedera.node.app.service.util.impl.test;
    exports com.hedera.node.app.service.util.impl.handlers;
    exports com.hedera.node.app.service.util.impl.components;
}
