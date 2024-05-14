package com.swirlds.logging.log4j.factory;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import com.swirlds.logging.api.extensions.event.LogEventConsumer;
import com.swirlds.logging.api.extensions.event.LogEventFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerRegistry;


/**
 * Implementation of Log4j {@link LoggerContext} SPI.
 * This is a factory to produce {@link BaseLogger} instances.
 * <p>
 * This implementation is inspired by {@code org.apache.logging.log4j.tojul.JULLoggerContext}.
 */
public class BaseLoggerContext implements LoggerContext {
    /**
     * The log event factory to create log events.
     * This is set by the swirlds-logging API.
     */
    private static volatile LogEventFactory logEventFactory;
    /**
     * The log event consumer to consume log events.
     * This is set by the swirlds-logging API.
     */
    private static volatile LogEventConsumer logEventConsumer;

    /**
     * The swirlds emergency logger.
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    /**
     * A flag to ensure that the initialisation error is only printed once.
     */
    private final AtomicBoolean initialisationErrorPrinted = new AtomicBoolean(false);

    /**
     * The logger registry to store the loggers.
     */
    private static LoggerRegistry<BaseLogger> loggerRegistry = new LoggerRegistry<>();

    /**
     * Returns the external context. This will always return {@code null}.
     *
     * @return {@code null}
     */
    @Override
    public Object getExternalContext() {
        return null;
    }

    /**
     * Returns the logger with the given name.
     *
     * @param name the name of the logger
     * @return the logger
     */
    @Override
    public ExtendedLogger getLogger(final String name) {
        if (!loggerRegistry.hasLogger(name)) {
            if(logEventFactory == null || logEventConsumer == null) {
                if (initialisationErrorPrinted.compareAndSet(false, true)) {
                    EMERGENCY_LOGGER.log(Level.ERROR, "LogEventFactory and LogEventConsumer must be set before using the logger context.");
                }
            } else {
                loggerRegistry.putIfAbsent(name, null, new BaseLogger(name, logEventConsumer, logEventFactory));
            }
        }
        return loggerRegistry.getLogger(name);
    }

    /**
     * Returns the logger with the given name.
     *
     * @param name The name of the Logger to return.
     * @param messageFactory The message factory is ignored since it is not used in the base logger.
     *
     * @return the logger
     */
    @Override
    public ExtendedLogger getLogger(final String name, final MessageFactory messageFactory) {
        return getLogger(name);
    }

    /**
     * Checks if the logger with the given name exists.
     *
     * @param name The name of the Logger to return.
     *
     * @return if the logger exists
     */
    @Override
    public boolean hasLogger(final String name) {
        return loggerRegistry.hasLogger(name);
    }

    /**
     * Checks if the logger with the given name exists.
     *
     * @param name The name of the Logger to return.
     * @param messageFactory The message factory is ignored since it is not used in the base logger.
     *
     * @return if the logger exists
     */
    @Override
    public boolean hasLogger(final String name, final MessageFactory messageFactory) {
        return loggerRegistry.hasLogger(name);
    }

    /**
     * Checks if the logger with the given name exists.
     *
     * @param name The name of the Logger to return.
     * @param messageFactoryClass The message factory class is ignored since it is not used in the base logger.
     *
     * @return if the logger exists
     */
    @Override
    public boolean hasLogger(final String name, final Class<? extends MessageFactory> messageFactoryClass) {
        return loggerRegistry.hasLogger(name);
    }


    /**
     * Sets the log event factory and log event consumer to create log events.
     *
     * @param logEventFactory the log event factory from the base logger.
     * @param logEventConsumer the log event consumer from the base logger.
     */
    public static void initBaseLogging(@NonNull final LogEventFactory logEventFactory, @NonNull final LogEventConsumer logEventConsumer) {
        if (logEventFactory == null) {
            EMERGENCY_LOGGER.logNPE("logEventFactory");
            return;
        }
        if (logEventConsumer == null) {
            EMERGENCY_LOGGER.logNPE("logEventConsumer");
            return;
        }

        BaseLoggerContext.loggerRegistry = new LoggerRegistry<>();
        BaseLoggerContext.logEventFactory = logEventFactory;
        BaseLoggerContext.logEventConsumer = logEventConsumer;
    }
}