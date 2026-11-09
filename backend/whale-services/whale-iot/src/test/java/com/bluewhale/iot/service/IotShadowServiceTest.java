package com.bluewhale.iot.service;

import com.bluewhale.iot.dto.ShadowVO;
import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.mapper.IotDeviceMapper;
import com.bluewhale.iot.mapper.IotProductMapper;
import com.bluewhale.iot.support.EmqxPublishClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备影子服务单测：上报合并、期望值清理、指令写期望与上线补推。
 *
 * @author BlueWhale
 */
class IotShadowServiceTest {

    private IotDeviceMapper deviceMapper;

    private IotProductMapper productMapper;

    private EmqxPublishClient publishClient;

    private IotShadowService service;

    @BeforeEach
    void setUp() {
        deviceMapper = mock(IotDeviceMapper.class);
        productMapper = mock(IotProductMapper.class);
        publishClient = mock(EmqxPublishClient.class);
        service = new IotShadowService(deviceMapper, productMapper, publishClient);
    }

    @Test
    void readEmptyShadowReturnsZeroVersion() {
        IotShadowService.ShadowState state = service.read(device(null));

        assertEquals(0L, state.getVersion());
        assertTrue(state.getReported().isEmpty());
        assertTrue(state.getDesired().isEmpty());
    }

    @Test
    void applyReportedMergesValuesAndClearsMatchedDesired() {
        IotDevice device = device("{\"reported\":{\"temperature\":20.0},"
                + "\"desired\":{\"temperature\":25.0,\"mode\":\"eco\"},\"version\":3}");

        service.applyReported(device, Map.of("temperature", 25.0), LocalDateTime.now());

        ShadowVO vo = service.toVo(device, service.read(device));
        assertEquals(25.0, ((Number) vo.getReported().get("temperature")).doubleValue());
        assertEquals(4L, vo.getVersion());
        assertFalse(vo.getDesired().containsKey("temperature"));
        assertEquals("eco", vo.getDesired().get("mode"));
        assertNotNull(device.getShadowJson());
        verify(deviceMapper).updateById(device);
    }

    @Test
    void markDesiredKeepsReportedAndBumpsVersion() {
        IotDevice device = device("{\"reported\":{\"temperature\":20.0},\"desired\":{},\"version\":1}");
        Map<String, Object> desired = new LinkedHashMap<>();
        desired.put("mode", "eco");

        service.markDesired(device, desired);

        ShadowVO vo = service.toVo(device, service.read(device));
        assertEquals(2L, vo.getVersion());
        assertEquals("eco", vo.getDesired().get("mode"));
        assertEquals(20.0, ((Number) vo.getReported().get("temperature")).doubleValue());
    }

    @Test
    void markDesiredIgnoresEmptyValues() {
        IotDevice device = device(null);

        service.markDesired(device, Map.of());

        verify(deviceMapper, never()).updateById(any(IotDevice.class));
    }

    @Test
    void syncDesiredOnConnectPublishesDownShadowTopic() {
        IotDevice device = device("{\"reported\":{},\"desired\":{\"mode\":\"eco\"},\"version\":2}");
        device.setId(9L);
        device.setTenantId(1L);
        device.setDeviceName("dev-001");
        device.setProductId(7L);
        IotProduct product = new IotProduct();
        product.setCode("temp-sensor");
        when(productMapper.selectOne(any())).thenReturn(product);
        when(publishClient.publish(anyString(), anyString())).thenReturn(true);

        assertTrue(service.syncDesiredOnConnect(device));
        verify(publishClient).publish(eq("bw/1/temp-sensor/dev-001/down/shadow"), anyString());
    }

    @Test
    void syncDesiredOnConnectSkipsWhenNoDesired() {
        IotDevice device = device("{\"reported\":{\"temperature\":20.0},\"desired\":{},\"version\":1}");

        assertFalse(service.syncDesiredOnConnect(device));
        verify(publishClient, never()).publish(anyString(), anyString(), anyInt());
    }

    @Test
    void getByCodeResolvesProductAndDevice() {
        IotProduct product = new IotProduct();
        product.setId(7L);
        product.setCode("temp-sensor");
        when(productMapper.selectOne(any())).thenReturn(product);
        IotDevice device = device(null);
        device.setId(9L);
        device.setDeviceName("dev-001");
        when(deviceMapper.selectOne(any())).thenReturn(device);

        ShadowVO vo = service.getByCode("temp-sensor", "dev-001");

        assertEquals("9", vo.getDeviceId());
        assertEquals("dev-001", vo.getDeviceName());
    }

    /**
     * 构造设备（影子 JSON 可空）。
     *
     * @param shadowJson 影子 JSON
     * @return 设备实体
     */
    private static IotDevice device(String shadowJson) {
        IotDevice device = new IotDevice();
        device.setId(9L);
        device.setTenantId(1L);
        device.setProductId(7L);
        device.setDeviceName("dev-001");
        device.setOnline(1);
        device.setShadowJson(shadowJson);
        return device;
    }
}
