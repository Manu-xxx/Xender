module com.swirlds.test.framework {
    exports com.swirlds.test.framework;
    exports com.swirlds.test.framework.config;
    exports com.swirlds.test.framework.context;

    /* Logging Libraries */
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires com.swirlds.common;
    requires com.swirlds.config;
    requires io.github.classgraph;
    requires static com.github.spotbugs.annotations;
}
