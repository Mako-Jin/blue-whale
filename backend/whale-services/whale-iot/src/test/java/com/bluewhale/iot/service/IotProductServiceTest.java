package com.bluewhale.iot.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.iot.dto.ProductSaveRequest;
import com.bluewhale.iot.dto.ProductVO;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.mapper.IotDeviceMapper;
import com.bluewhale.iot.mapper.IotProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 产品服务单测：编码唯一、状态机、删除卡口与发布禁改。
 *
 * @author BlueWhale
 */
class IotProductServiceTest {

    private IotProductMapper productMapper;

    private IotDeviceMapper deviceMapper;

    private IotProductService service;

    @BeforeEach
    void setUp() {
        productMapper = mock(IotProductMapper.class);
        deviceMapper = mock(IotDeviceMapper.class);
        service = new IotProductService(productMapper, deviceMapper);
    }

    @Test
    void createRejectsDuplicateCode() {
        when(productMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> service.create(request("temp-sensor"), 7L));

        assertTrue(exception.getMessage().contains("已存在"));
        verify(productMapper, never()).insert(any(IotProduct.class));
    }

    @Test
    void createDefaultsToDraftAndMqtt() {
        when(productMapper.selectCount(any())).thenReturn(0L);

        ProductVO vo = service.create(request("temp-sensor"), 7L);

        assertEquals("draft", vo.getStatus());
        assertEquals("mqtt", vo.getProtocol());
        verify(productMapper).insert(any(IotProduct.class));
    }

    @Test
    void updateRejectsPublishedProduct() {
        IotProduct product = product(1L, IotProductService.STATUS_PUBLISHED);
        when(productMapper.selectById(1L)).thenReturn(product);

        BizException exception = assertThrows(BizException.class,
                () -> service.update(1L, request("temp-sensor")));

        assertTrue(exception.getMessage().contains("已发布"));
    }

    @Test
    void updateAllowsDraftProduct() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, IotProductService.STATUS_DRAFT));

        ProductVO vo = service.update(1L, request("temp-sensor"));

        assertEquals("温度传感器", vo.getName());
        verify(productMapper).updateById(any(IotProduct.class));
    }

    @Test
    void deleteRejectsWhenDevicesRemain() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, IotProductService.STATUS_DRAFT));
        when(deviceMapper.selectCount(any())).thenReturn(3L);

        BizException exception = assertThrows(BizException.class, () -> service.delete(1L));

        assertTrue(exception.getMessage().contains("3 台设备"));
        verify(productMapper, never()).deleteById(1L);
    }

    @Test
    void deleteAllowsEmptyProduct() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, IotProductService.STATUS_DRAFT));
        when(deviceMapper.selectCount(any())).thenReturn(0L);

        service.delete(1L);

        verify(productMapper).deleteById(1L);
    }

    @Test
    void requireExistsThrowsWhenAbsent() {
        when(productMapper.selectById(9L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> service.requireExists(9L));

        assertEquals("产品不存在", exception.getMessage());
    }

    private static ProductSaveRequest request(String code) {
        ProductSaveRequest request = new ProductSaveRequest();
        request.setCode(code);
        request.setName("温度传感器");
        return request;
    }

    private static IotProduct product(Long id, String status) {
        IotProduct product = new IotProduct();
        product.setId(id);
        product.setCode("temp-sensor");
        product.setName("温度传感器");
        product.setProtocol("mqtt");
        product.setStatus(status);
        return product;
    }
}
