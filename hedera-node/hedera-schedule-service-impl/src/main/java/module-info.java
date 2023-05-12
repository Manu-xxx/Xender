import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;

module com.hedera.node.app.service.schedule.impl {
    requires transitive com.hedera.node.app.service.scheduled;
    requires org.apache.commons.lang3;
    requires com.hedera.node.app.service.mono;
    requires com.hedera.pbj.runtime;
    requires com.swirlds.virtualmap;
    requires dagger;
    requires javax.inject;
    requires com.swirlds.common;
    requires com.github.spotbugs.annotations;

    exports com.hedera.node.app.service.schedule.impl to
            com.hedera.node.app.service.schedule.impl.test,
            com.hedera.node.app;
    exports com.hedera.node.app.service.schedule.impl.handlers to
            com.hedera.node.app.service.schedule.impl.test,
            com.hedera.node.app;
    exports com.hedera.node.app.service.schedule.impl.serdes;

    provides com.hedera.node.app.service.schedule.ScheduleService with
            ScheduleServiceImpl;
}
