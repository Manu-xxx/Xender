open module com.swirlds.common.testing {
  
    requires transitive com.swirlds.common;
    requires transitive org.apache.logging.log4j.core;
    requires com.swirlds.base;
    requires java.scripting;
    requires org.apache.logging.log4j;
    requires org.junit.jupiter.api;
    requires static com.github.spotbugs.annotations;
}
