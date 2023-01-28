import com.hedera.node.app.service.file.impl.FileServiceImpl;

module com.hedera.node.app.service.file.impl {
    requires com.hedera.node.app.service.file;
    requires com.hedera.node.app.service.mono;
    requires com.swirlds.common;
    requires com.swirlds.virtualmap;
    requires com.swirlds.jasperdb;

    provides com.hedera.node.app.service.file.FileService with
            FileServiceImpl;

    exports com.hedera.node.app.service.file.impl to
            com.hedera.node.app,
            com.hedera.node.app.service.file.impl.test;
    exports com.hedera.node.app.service.file.impl.handlers;
}
