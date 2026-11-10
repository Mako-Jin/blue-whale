package com.bluewhale.iot.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.iot.dto.DeviceCreateRequest;
import com.bluewhale.iot.dto.DeviceCreateVO;
import com.bluewhale.iot.dto.DeviceSecretResetVO;
import com.bluewhale.iot.dto.DeviceUpdateRequest;
import com.bluewhale.iot.dto.DeviceVO;
import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.mapper.IotDeviceMapper;
import com.bluewhale.iot.mapper.IotProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 设备服务单测：三元组签发（secret 一次性）、重置与产品内重名。
 *
 * @author BlueWhale
 */
class IotDeviceServiceTest {

    private IotDeviceMapper deviceMapper;

    private IotProductMapper productMapper;

    private IotDeviceService service;

    @BeforeEach
    void setUp() {
        deviceMapper = mock(IotDeviceMapper.class);
        productMapper = mock(IotProductMapper.class);
        IotProductService productService = new IotProductService(productMapper, deviceMapper);
        service = new IotDeviceService(deviceMapper, productService);
    }

    @Test
    void createIssuesSecretOnce() {
        when(productMapper.selectById(1L)).thenReturn(publishedProduct());
        when(deviceMapper.selectCount(any())).thenReturn(0L);

        DeviceCreateVO vo = service.create(request("dev-001"));

        assertEquals(64, vo.getSecret().length());
        assertEquals("temp-sensor", vo.getProductCode());
        assertEquals(0, vo.getOnline());
        assertEquals("active", vo.getStatus());
        ArgumentCaptor<IotDevice> captor = ArgumentCaptor.forClass(IotDevice.class);
        verify(deviceMapper).insert(captor.capture());
        assertEquals(vo.getSecret(), captor.getValue().getSecret());
    }

    @Test
    void createRejectsDuplicateDeviceName() {
        when(productMapper.selectById(1L)).thenReturn(publishedProduct());
        when(deviceMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class, () -> service.create(request("dev-001")));

        assertTrue(exception.getMessage().contains("已存在"));
        verify(deviceMapper, never()).insert(any(IotDevice.class));
    }

    @Test
    void resetSecretRotatesAndReturnsOnce() {
        IotDevice device = device(1L, "old-secret");
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(productMapper.selectById(1L)).thenReturn(publishedProduct());

        DeviceSecretResetVO vo = service.resetSecret(1L);

        assertEquals(64, vo.getSecret().length());
        assertNotEquals("old-secret", vo.getSecret());
        assertEquals(vo.getSecret(), device.getSecret());
        verify(deviceMapper).updateById(device);
    }

    @Test
    void updateAppliesNicknameAndStatus() {
        IotDevice device = device(1L, "secret");
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(productMapper.selectById(1L)).thenReturn(publishedProduct());
        DeviceUpdateRequest request = new DeviceUpdateRequest();
        request.setNickname("一号机");
        request.setStatus("disabled");

        DeviceVO vo = service.update(1L, request);

        assertEquals("一号机", vo.getNickname());
        assertEquals("disabled", vo.getStatus());
    }

    @Test
    void requireExistsThrowsWhenAbsent() {
        when(deviceMapper.selectById(9L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> service.requireExists(9L));

        assertEquals("设备不存在", exception.getMessage());
    }

    private static DeviceCreateRequest request(String deviceName) {
        DeviceCreateRequest request = new DeviceCreateRequest();
        request.setProductId(1L);
        request.setDeviceName(deviceName);
        return request;
    }

    private static IotProduct publishedProduct() {
        IotProduct product = new IotProduct();
        product.setId(1L);
        product.setCode("temp-sensor");
        product.setName("温度传感器");
        product.setStatus(IotProductService.STATUS_PUBLISHED);
        return product;
    }

    private static IotDevice device(Long id, String secret) {
        IotDevice device = new IotDevice();
        device.setId(id);
        device.setProductId(1L);
        device.setDeviceName("dev-001");
        device.setSecret(secret);
        device.setOnline(0);
        device.setStatus(IotDeviceService.STATUS_ACTIVE);
        return device;
    }
}
