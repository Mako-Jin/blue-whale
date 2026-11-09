package com.bluewhale.iot.service;

import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.mapper.IotDeviceMapper;
import com.bluewhale.iot.mapper.IotProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EMQX 认证与连接事件服务单测：HMAC 契约、fail-closed 口径与在线态维护。
 *
 * @author BlueWhale
 */
class EmqxAuthServiceTest {

    private static final String USERNAME = "temp-sensor/dev-001";

    private IotDeviceMapper deviceMapper;

    private IotProductMapper productMapper;

    private EmqxAuthService service;

    @BeforeEach
    void setUp() {
        deviceMapper = mock(IotDeviceMapper.class);
        productMapper = mock(IotProductMapper.class);
        service = new EmqxAuthService(deviceMapper, productMapper);
    }

    @Test
    void authenticateAllowsCorrectHmac() {
        IotDevice device = device("s3cret", IotDeviceService.STATUS_ACTIVE);
        when(productMapper.selectOne(any())).thenReturn(publishedProduct());
        when(deviceMapper.selectOne(any())).thenReturn(device);
        String password = EmqxAuthService.hmacHex("s3cret", USERNAME);

        assertTrue(service.authenticate(USERNAME, password));
    }

    @Test
    void authenticateDeniesWrongPassword() {
        when(productMapper.selectOne(any())).thenReturn(publishedProduct());
        when(deviceMapper.selectOne(any())).thenReturn(device("s3cret", IotDeviceService.STATUS_ACTIVE));
        String wrong = EmqxAuthService.hmacHex("other-secret", USERNAME);

        assertFalse(service.authenticate(USERNAME, wrong));
    }

    @Test
    void authenticateDeniesRawSecretAsPassword() {
        when(productMapper.selectOne(any())).thenReturn(publishedProduct());
        when(deviceMapper.selectOne(any())).thenReturn(device("s3cret", IotDeviceService.STATUS_ACTIVE));

        assertFalse(service.authenticate(USERNAME, "s3cret"));
    }

    @Test
    void authenticateDeniesDisabledDevice() {
        when(productMapper.selectOne(any())).thenReturn(publishedProduct());
        when(deviceMapper.selectOne(any())).thenReturn(device("s3cret", IotDeviceService.STATUS_DISABLED));
        String password = EmqxAuthService.hmacHex("s3cret", USERNAME);

        assertFalse(service.authenticate(USERNAME, password));
    }

    @Test
    void authenticateDeniesUnpublishedProduct() {
        when(productMapper.selectOne(any())).thenReturn(product(IotProductService.STATUS_DRAFT));
        String password = EmqxAuthService.hmacHex("s3cret", USERNAME);

        assertFalse(service.authenticate(USERNAME, password));
    }

    @Test
    void authenticateDeniesUnknownProductOrDevice() {
        when(productMapper.selectOne(any())).thenReturn(null);

        assertFalse(service.authenticate(USERNAME, "whatever"));
    }

    @Test
    void authenticateDeniesMalformedUsernameOrBlankPassword() {
        assertFalse(service.authenticate("no-separator", "x"));
        assertFalse(service.authenticate("/dev-001", "x"));
        assertFalse(service.authenticate(USERNAME, ""));
        assertFalse(service.authenticate(USERNAME, null));
    }

    @Test
    void onConnectedMarksDeviceOnline() {
        IotDevice device = device("s3cret", IotDeviceService.STATUS_ACTIVE);
        when(productMapper.selectOne(any())).thenReturn(publishedProduct());
        when(deviceMapper.selectOne(any())).thenReturn(device);

        service.onConnected(USERNAME);

        assertEquals(1, device.getOnline());
        assertEquals(LocalDateTime.now().toLocalDate(), device.getLastOnlineTime().toLocalDate());
        verify(deviceMapper).updateById(device);
    }

    @Test
    void onDisconnectedMarksDeviceOffline() {
        IotDevice device = device("s3cret", IotDeviceService.STATUS_ACTIVE);
        device.setOnline(1);
        when(productMapper.selectOne(any())).thenReturn(publishedProduct());
        when(deviceMapper.selectOne(any())).thenReturn(device);

        service.onDisconnected(USERNAME);

        assertEquals(0, device.getOnline());
        verify(deviceMapper).updateById(device);
    }

    @Test
    void connectionEventsTolerateUnknownDevice() {
        when(productMapper.selectOne(any())).thenReturn(publishedProduct());
        when(deviceMapper.selectOne(any())).thenReturn(null);

        service.onConnected(USERNAME);
        service.onDisconnected(USERNAME);

        verify(deviceMapper, org.mockito.Mockito.never()).updateById(any(IotDevice.class));
    }

    @Test
    void parseUsernameHandlesEdgeCases() {
        assertNull(EmqxAuthService.parseUsername(null));
        assertNull(EmqxAuthService.parseUsername(""));
        assertNull(EmqxAuthService.parseUsername("noseparator"));
        assertNull(EmqxAuthService.parseUsername("/leading"));
        assertNull(EmqxAuthService.parseUsername("trailing/"));

        EmqxAuthService.ParsedUsername parsed = EmqxAuthService.parseUsername("temp-sensor/dev-001");
        assertEquals("temp-sensor", parsed.productCode());
        assertEquals("dev-001", parsed.deviceName());
    }

    private static IotProduct publishedProduct() {
        return product(IotProductService.STATUS_PUBLISHED);
    }

    private static IotProduct product(String status) {
        IotProduct product = new IotProduct();
        product.setId(1L);
        product.setCode("temp-sensor");
        product.setStatus(status);
        return product;
    }

    private static IotDevice device(String secret, String status) {
        IotDevice device = new IotDevice();
        device.setId(100L);
        device.setProductId(1L);
        device.setDeviceName("dev-001");
        device.setSecret(secret);
        device.setOnline(0);
        device.setStatus(status);
        return device;
    }
}
