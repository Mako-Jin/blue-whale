package com.bluewhale.iot.service;

import com.bluewhale.iot.config.IotProperties;
import com.bluewhale.iot.dto.TelemetryIngestResult;
import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.entity.IotTelemetry;
import com.bluewhale.iot.entity.IotThingModel;
import com.bluewhale.iot.mapper.IotDeviceMapper;
import com.bluewhale.iot.mapper.IotProductMapper;
import com.bluewhale.iot.mapper.IotTelemetryMapper;
import com.bluewhale.iot.mapper.IotThingModelMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 遥测服务单测：主题/设备定位、物模型分流、脏数据丢弃口径与事件/回执分流。
 *
 * @author BlueWhale
 */
class IotTelemetryServiceTest {

    private static final String USERNAME = "temp-sensor/dev-001";

    private static final String PROPERTY_TOPIC = "bw/1/temp-sensor/dev-001/up/property";

    private static final String EVENT_TOPIC = "bw/1/temp-sensor/dev-001/up/event";

    private static final String REPLY_TOPIC = "bw/1/temp-sensor/dev-001/up/reply";

    private IotTelemetryMapper telemetryMapper;

    private IotThingModelMapper thingModelMapper;

    private IotDeviceMapper deviceMapper;

    private IotProductMapper productMapper;

    private IotShadowService shadowService;

    private IotStreamService streamService;

    private IotCommandService commandService;

    private IotRuleEngine ruleEngine;

    private IotTelemetryService service;

    @BeforeEach
    void setUp() {
        telemetryMapper = mock(IotTelemetryMapper.class);
        thingModelMapper = mock(IotThingModelMapper.class);
        deviceMapper = mock(IotDeviceMapper.class);
        productMapper = mock(IotProductMapper.class);
        shadowService = mock(IotShadowService.class);
        streamService = mock(IotStreamService.class);
        commandService = mock(IotCommandService.class);
        ruleEngine = mock(IotRuleEngine.class);
        service = new IotTelemetryService(telemetryMapper, thingModelMapper, deviceMapper, productMapper,
                shadowService, streamService, commandService, ruleEngine, new IotProperties());
    }

    @Test
    void ingestPropertySplitsNumericAndTextAndRejectsUnknown() {
        mockDevice();
        when(thingModelMapper.selectList(any())).thenReturn(List.of(
                model("temperature", "double"), model("mode", "string")));

        TelemetryIngestResult result = service.ingest(USERNAME, PROPERTY_TOPIC,
                "{\"temperature\":25.5,\"mode\":\"eco\",\"unknown\":1}");

        assertEquals(2, result.getAccepted());
        assertEquals(1, result.getRejected());
        ArgumentCaptor<IotTelemetry> captor = ArgumentCaptor.forClass(IotTelemetry.class);
        verify(telemetryMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        IotTelemetry numeric = captor.getAllValues().get(0);
        assertEquals("temperature", numeric.getIdentifier());
        assertEquals(25.5, numeric.getValue());
        assertEquals("mode", captor.getAllValues().get(1).getIdentifier());
        assertEquals("eco", captor.getAllValues().get(1).getValueText());
        verify(shadowService).applyReported(any(IotDevice.class), any(), any());
        verify(streamService).publish(eq(9L), eq(IotStreamService.EVENT_TELEMETRY), any());
    }

    @Test
    void ingestPropertyRejectsTypeMismatch() {
        mockDevice();
        when(thingModelMapper.selectList(any())).thenReturn(List.of(model("temperature", "double")));

        TelemetryIngestResult result = service.ingest(USERNAME, PROPERTY_TOPIC,
                "{\"temperature\":\"hot\"}");

        assertEquals(0, result.getAccepted());
        assertEquals(1, result.getRejected());
        verify(telemetryMapper, never()).insert(any(IotTelemetry.class));
        verify(shadowService, never()).applyReported(any(), any(), any());
    }

    @Test
    void ingestPropertyWithoutThingModelAcceptsAnything() {
        mockDevice();
        when(thingModelMapper.selectList(any())).thenReturn(List.of());

        TelemetryIngestResult result = service.ingest(USERNAME, PROPERTY_TOPIC,
                "{\"properties\":{\"temperature\":18,\"mode\":\"eco\"}}");

        assertEquals(2, result.getAccepted());
        assertEquals(0, result.getRejected());
    }

    @Test
    void ingestEventStoresPrefixedIdentifierAndPushesEvent() {
        mockDevice();

        TelemetryIngestResult result = service.ingest(USERNAME, EVENT_TOPIC,
                "{\"identifier\":\"overheat\",\"level\":\"warn\"}");

        assertEquals(1, result.getAccepted());
        ArgumentCaptor<IotTelemetry> captor = ArgumentCaptor.forClass(IotTelemetry.class);
        verify(telemetryMapper).insert(captor.capture());
        assertEquals("event:overheat", captor.getValue().getIdentifier());
        assertNotNull(captor.getValue().getValueText());
        verify(streamService).publish(eq(9L), eq(IotStreamService.EVENT_EVENT), any());
    }

    @Test
    void ingestEventWithoutIdentifierIsRejected() {
        mockDevice();

        TelemetryIngestResult result = service.ingest(USERNAME, EVENT_TOPIC, "{\"level\":\"warn\"}");

        assertEquals(0, result.getAccepted());
        assertEquals(1, result.getRejected());
    }

    @Test
    void ingestReplyDelegatesToCommandService() {
        mockDevice();
        when(commandService.onReply(anyString())).thenReturn(true);

        TelemetryIngestResult result = service.ingest(USERNAME, REPLY_TOPIC,
                "{\"id\":\"msg-1\",\"code\":0}");

        assertEquals(1, result.getAccepted());
        verify(commandService).onReply(anyString());
        verify(telemetryMapper, never()).insert(any(IotTelemetry.class));
    }

    @Test
    void ingestUnknownDeviceReturnsReason() {
        when(productMapper.selectOne(any())).thenReturn(null);

        TelemetryIngestResult result = service.ingest(USERNAME, PROPERTY_TOPIC, "{}");

        assertEquals("device not found", result.getReason());
        verify(telemetryMapper, never()).insert(any(IotTelemetry.class));
    }

    /**
     * 桩：产品与设备均存在。
     */
    private void mockDevice() {
        IotProduct product = new IotProduct();
        product.setId(7L);
        product.setCode("temp-sensor");
        when(productMapper.selectOne(any())).thenReturn(product);
        IotDevice device = new IotDevice();
        device.setId(9L);
        device.setTenantId(1L);
        device.setProductId(7L);
        device.setDeviceName("dev-001");
        when(deviceMapper.selectOne(any())).thenReturn(device);
    }

    /**
     * 构造物模型属性条目。
     *
     * @param identifier 标识符
     * @param dataType   数据类型
     * @return 物模型实体
     */
    private static IotThingModel model(String identifier, String dataType) {
        IotThingModel model = new IotThingModel();
        model.setProductId(7L);
        model.setKind(IotThingModelService.KIND_PROPERTY);
        model.setIdentifier(identifier);
        model.setDataType(dataType);
        return model;
    }
}
