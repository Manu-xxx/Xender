module com.hedera.node.app.service.scheduled.impl {
    requires com.hedera.node.app.service.scheduled;

    provides com.hedera.node.app.service.scheduled.ScheduleService with
            com.hedera.node.app.service.scheduled.impl.StandardScheduledService;

    exports com.hedera.node.app.service.scheduled.impl to
            com.hedera.node.app.service.scheduled.impl.test;
}
