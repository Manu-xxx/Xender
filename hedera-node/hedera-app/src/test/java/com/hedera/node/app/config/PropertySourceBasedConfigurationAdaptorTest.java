package com.hedera.node.app.config;

import static com.hedera.node.app.spi.config.PropertyNames.DEV_DEFAULT_LISTENING_NODE_ACCOUNT;
import static com.hedera.node.app.spi.config.PropertyNames.DEV_ONLY_DEFAULT_NODE_LISTENS;
import static com.hedera.node.app.spi.config.PropertyNames.GRPC_PORT;
import static com.hedera.node.app.spi.config.PropertyNames.GRPC_TLS_PORT;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_ACCOUNTS_EXPORT_PATH;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_EXPORT_ACCOUNTS_ON_STARTUP;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_PREFETCH_CODE_CACHE_TTL_SECS;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_PREFETCH_QUEUE_CAPACITY;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_PREFETCH_THREAD_POOL_SIZE;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_PROFILES_ACTIVE;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_RECORD_STREAM_IS_ENABLED;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_RECORD_STREAM_LOG_DIR;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_RECORD_STREAM_LOG_PERIOD;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_RECORD_STREAM_QUEUE_CAPACITY;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_RECORD_STREAM_SIDE_CAR_DIR;
import static com.hedera.node.app.spi.config.PropertyNames.ISS_RESET_PERIOD;
import static com.hedera.node.app.spi.config.PropertyNames.ISS_ROUNDS_TO_LOG;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_MODE;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_FLOW_CONTROL_WINDOW;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_KEEP_ALIVE_TIME;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_KEEP_ALIVE_TIMEOUT;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_MAX_CONCURRENT_CALLS;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE_GRACE;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_PROD_MAX_CONNECTION_IDLE;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_START_RETRIES;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_START_RETRY_INTERVAL_MS;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_TLS_CERT_PATH;
import static com.hedera.node.app.spi.config.PropertyNames.NETTY_TLS_KEY_PATH;
import static com.hedera.node.app.spi.config.PropertyNames.QUERIES_BLOB_LOOK_UP_RETRIES;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_CONS_THROTTLES_TO_SAMPLE;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_EXECUTION_TIMES_TO_TRACK;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_HAPI_THROTTLES_TO_SAMPLE;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_RUNNING_AVG_HALF_LIFE_SECS;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_SPEEDOMETER_HALF_LIFE_SECS;
import static com.hedera.node.app.spi.config.PropertyNames.STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS;
import static com.hedera.node.app.spi.config.PropertyNames.WORKFLOWS_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.config.adaptor.PropertySourceBasedConfigurationAdaptor;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.spi.config.GlobalConfig;
import com.hedera.node.app.spi.config.NodeConfig;
import com.hedera.node.app.spi.config.Profile;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito.BDDMyOngoingStubbing;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertySourceBasedConfigurationAdaptorTest {

  @Mock(strictness = Strictness.LENIENT)
  private PropertySource propertySource;

  @BeforeEach
  void configureMockForConfigData() {
    final Function<String, BDDMyOngoingStubbing<Object>> createMock = name -> given(propertySource
        .getTypedProperty(ArgumentMatchers.any(), ArgumentMatchers.eq(name)));
    final Consumer<String> integerMockRule = name -> createMock.apply(name).willReturn(1);
    final Consumer<String> profileMockRule = name -> createMock.apply(name)
        .willReturn(Profile.TEST);
    final Consumer<String> stringMockRule = name -> createMock.apply(name).willReturn("");
    final Consumer<String> booleanMockRule = name -> createMock.apply(name).willReturn(false);
    final Consumer<String> listMockRule = name -> createMock.apply(name).willReturn(List.of());

    integerMockRule.accept(GRPC_PORT);
    integerMockRule.accept(GRPC_TLS_PORT);
    integerMockRule.accept(STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS);
    integerMockRule.accept(STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS);
    integerMockRule.accept(STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS);
    profileMockRule.accept(HEDERA_PROFILES_ACTIVE);
    integerMockRule.accept(STATS_SPEEDOMETER_HALF_LIFE_SECS);
    integerMockRule.accept(STATS_RUNNING_AVG_HALF_LIFE_SECS);
    stringMockRule.accept(HEDERA_RECORD_STREAM_LOG_DIR);
    integerMockRule.accept(HEDERA_RECORD_STREAM_LOG_PERIOD);
    booleanMockRule.accept(HEDERA_RECORD_STREAM_IS_ENABLED);
    integerMockRule.accept(HEDERA_RECORD_STREAM_QUEUE_CAPACITY);
    integerMockRule.accept(QUERIES_BLOB_LOOK_UP_RETRIES);
    integerMockRule.accept(NETTY_PROD_KEEP_ALIVE_TIME);
    stringMockRule.accept(NETTY_TLS_CERT_PATH);
    stringMockRule.accept(NETTY_TLS_KEY_PATH);
    integerMockRule.accept(NETTY_PROD_KEEP_ALIVE_TIMEOUT);
    integerMockRule.accept(NETTY_PROD_MAX_CONNECTION_AGE);
    integerMockRule.accept(NETTY_PROD_MAX_CONNECTION_AGE_GRACE);
    integerMockRule.accept(NETTY_PROD_MAX_CONNECTION_IDLE);
    integerMockRule.accept(NETTY_PROD_MAX_CONCURRENT_CALLS);
    integerMockRule.accept(NETTY_PROD_FLOW_CONTROL_WINDOW);
    stringMockRule.accept(DEV_DEFAULT_LISTENING_NODE_ACCOUNT);
    booleanMockRule.accept(DEV_ONLY_DEFAULT_NODE_LISTENS);
    stringMockRule.accept(HEDERA_ACCOUNTS_EXPORT_PATH);
    booleanMockRule.accept(HEDERA_EXPORT_ACCOUNTS_ON_STARTUP);
    profileMockRule.accept(NETTY_MODE);
    integerMockRule.accept(NETTY_START_RETRIES);
    integerMockRule.accept(NETTY_START_RETRY_INTERVAL_MS);
    integerMockRule.accept(STATS_EXECUTION_TIMES_TO_TRACK);
    integerMockRule.accept(ISS_RESET_PERIOD);
    integerMockRule.accept(ISS_ROUNDS_TO_LOG);
    integerMockRule.accept(HEDERA_PREFETCH_QUEUE_CAPACITY);
    integerMockRule.accept(HEDERA_PREFETCH_THREAD_POOL_SIZE);
    integerMockRule.accept(HEDERA_PREFETCH_CODE_CACHE_TTL_SECS);
    listMockRule.accept(STATS_CONS_THROTTLES_TO_SAMPLE);
    listMockRule.accept(STATS_HAPI_THROTTLES_TO_SAMPLE);
    stringMockRule.accept(HEDERA_RECORD_STREAM_SIDE_CAR_DIR);
    booleanMockRule.accept(WORKFLOWS_ENABLED);
  }

  @Test
  void createInvalidCreation() {
    Assertions.assertThrows(NullPointerException.class,
        () -> new PropertySourceBasedConfigurationAdaptor(null));
  }

  @Test
  void testNotExists() {
    //given
    given(propertySource.containsProperty("test")).willReturn(false);
    final PropertySourceBasedConfigurationAdaptor configurationAdapter = new PropertySourceBasedConfigurationAdaptor(
        propertySource);

    //when
    final boolean exists = configurationAdapter.exists("test");

    //then
    assertFalse(exists);
    verify(propertySource).containsProperty("test");
  }

  @Test
  void testExists() {
    //given
    given(propertySource.containsProperty("test")).willReturn(true);
    final PropertySourceBasedConfigurationAdaptor configurationAdapter = new PropertySourceBasedConfigurationAdaptor(
        propertySource);

    //when
    final boolean exists = configurationAdapter.exists("test");

    //then
    assertTrue(exists);
    verify(propertySource).containsProperty("test");
  }

  @Test
  void testGetNames() {
    //given
    given(propertySource.allPropertyNames()).willReturn(Set.of("foo", "bar"));
    final PropertySourceBasedConfigurationAdaptor configurationAdapter = new PropertySourceBasedConfigurationAdaptor(
        propertySource);

    //when
    final Set<String> names = configurationAdapter.getPropertyNames().collect(Collectors.toSet());

    //then
    assertEquals(Set.of("foo", "bar"), names);
    verify(propertySource).allPropertyNames();
  }

  @Test
  void testGetValue() {
    //given
    given(propertySource.containsProperty("test")).willReturn(true);
    given(propertySource.getRawValue("test")).willReturn("value");
    final PropertySourceBasedConfigurationAdaptor configurationAdapter = new PropertySourceBasedConfigurationAdaptor(
        propertySource);

    //when
    final String value = configurationAdapter.getValue("test");

    //then
    assertEquals("value", value);
    verify(propertySource).getRawValue("test");
  }

  @Test
  void testGetDefaultValue() {
    //given
    given(propertySource.containsProperty("test")).willReturn(false);
    final PropertySourceBasedConfigurationAdaptor configurationAdapter = new PropertySourceBasedConfigurationAdaptor(
        propertySource);

    //when
    final String value = configurationAdapter.getValue("test", "value");

    //then
    assertEquals("value", value);
    verify(propertySource).containsProperty("test");
  }

  @Test
  void testGetTypedValue() {
    //given
    given(propertySource.containsProperty("test")).willReturn(true);
    given(propertySource.getTypedProperty(Integer.class, "test")).willReturn(1);
    final PropertySourceBasedConfigurationAdaptor configurationAdapter = new PropertySourceBasedConfigurationAdaptor(
        propertySource);

    //when
    final int value = configurationAdapter.getValue("test", Integer.class);

    //then
    assertEquals(1, value);
    verify(propertySource).containsProperty("test");
    verify(propertySource).getTypedProperty(Integer.class, "test");
  }

  @Test
  void testGetTypedDefaultValue() {
    //given
    given(propertySource.containsProperty("test")).willReturn(false);
    final PropertySourceBasedConfigurationAdaptor configurationAdapter = new PropertySourceBasedConfigurationAdaptor(
        propertySource);

    //when
    final int value = configurationAdapter.getValue("test", Integer.class, 12);

    //then
    assertEquals(12, value);
    verify(propertySource).containsProperty("test");
  }

  @Test
  void testGetValues() {
    //given
    given(propertySource.containsProperty("test")).willReturn(true);
    given(propertySource.getTypedProperty(List.class, "test")).willReturn(List.of("A", "B", "C"));
    final PropertySourceBasedConfigurationAdaptor configurationAdapter = new PropertySourceBasedConfigurationAdaptor(
        propertySource);

    //when
    final List<String> values = configurationAdapter.getValues("test");

    //then
    assertEquals(List.of("A", "B", "C"), values);
    verify(propertySource).containsProperty("test");
    verify(propertySource).getTypedProperty(List.class, "test");
  }

  @Test
  void testGetDefaultValues() {
    //given
    given(propertySource.containsProperty("test")).willReturn(false);
    final PropertySourceBasedConfigurationAdaptor configurationAdapter = new PropertySourceBasedConfigurationAdaptor(
        propertySource);

    //when
    final List<String> values = configurationAdapter.getValues("test", List.of("A", "B", "C"));

    //then
    assertEquals(List.of("A", "B", "C"), values);
    verify(propertySource).containsProperty("test");
  }

  @Test
  void testGetTypedValues() {
    //given
    given(propertySource.containsProperty("test")).willReturn(true);
    given(propertySource.getTypedProperty(List.class, "test")).willReturn(List.of(1, 2, 3));
    final PropertySourceBasedConfigurationAdaptor configurationAdapter = new PropertySourceBasedConfigurationAdaptor(
        propertySource);

    //when
    final List<Integer> values = configurationAdapter.getValues("test", Integer.class);

    //then
    assertEquals(List.of(1, 2, 3), values);
    verify(propertySource).containsProperty("test");
    verify(propertySource).getTypedProperty(List.class, "test");
  }

  @Test
  void testGetTypedDefaultValues() {
    //given
    given(propertySource.containsProperty("test")).willReturn(false);
    final PropertySourceBasedConfigurationAdaptor configurationAdapter = new PropertySourceBasedConfigurationAdaptor(
        propertySource);

    //when
    final List<Integer> values = configurationAdapter.getValues("test", Integer.class,
        List.of(1, 2, 3));

    //then
    assertEquals(List.of(1, 2, 3), values);
    verify(propertySource).containsProperty("test");
  }

  @Test
  void testGetNodeConfig() {
    //given
    final PropertySourceBasedConfigurationAdaptor configurationAdapter = new PropertySourceBasedConfigurationAdaptor(
        propertySource);

    //when
    final NodeConfig data = configurationAdapter.getConfigData(NodeConfig.class);

    //then
    assertNotNull(data);
    assertNotNull(data.accountsExportPath());
    assertNotNull(data.activeProfile());
    assertNotNull(data.consThrottlesToSample());
    assertNotNull(data.devListeningAccount());
    assertNotNull(data.hapiThrottlesToSample());
  }

  @Test
  void testGetGlobalConfig() {
    //given
    final PropertySourceBasedConfigurationAdaptor configurationAdapter = new PropertySourceBasedConfigurationAdaptor(
        propertySource);

    //when
    final GlobalConfig data = configurationAdapter.getConfigData(GlobalConfig.class);

    //then
    assertNotNull(data);
    assertEquals(false, data.workflowsEnabled());
  }

}