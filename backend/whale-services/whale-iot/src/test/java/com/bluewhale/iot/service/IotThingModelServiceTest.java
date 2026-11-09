package com.bluewhale.iot.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.iot.dto.ThingModelItem;
import com.bluewhale.iot.dto.ThingModelSaveRequest;
import com.bluewhale.iot.dto.ThingModelVO;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.mapper.IotThingModelMapper;
import com.bluewhale.iot.service.spec.ThingModelValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 物模型服务单测：整包覆盖、已发布冻结、条目去重与规格校验。
 *
 * @author BlueWhale
 */
class IotThingModelServiceTest {

    private IotThingModelMapper thingModelMapper;

    private IotProductService productService;

    private IotThingModelService service;

    @BeforeEach
    void setUp() {
        thingModelMapper = mock(IotThingModelMapper.class);
        productService = mock(IotProductService.class);
        service = new IotThingModelService(thingModelMapper, productService);
    }

    @Test
    void replaceAllRejectsPublishedProduct() {
        when(productService.requireExists(1L)).thenReturn(product(IotProductService.STATUS_PUBLISHED));

        BizException exception = assertThrows(BizException.class,
                () -> service.replaceAll(1L, request(item("property", "temperature", "double", null))));

        assertTrue(exception.getMessage().contains("冻结"));
        verify(thingModelMapper, never()).deletePhysicalByProductId(1L);
    }

    @Test
    void replaceAllPhysicallyReplacesEntries() {
        when(productService.requireExists(1L)).thenReturn(product(IotProductService.STATUS_DRAFT));
        when(thingModelMapper.selectList(any())).thenReturn(List.of(
                model("event", "overheat", null),
                model("property", "temperature", "double")));

        List<ThingModelVO> result = service.replaceAll(1L,
                request(item("property", "temperature", "double", Map.of("min", -40, "max", 125)),
                        item("event", "overheat", null, null)));

        verify(thingModelMapper).deletePhysicalByProductId(1L);
        verify(thingModelMapper, times(2)).insert(any(com.bluewhale.iot.entity.IotThingModel.class));
        assertEquals(2, result.size());
        assertEquals("event", result.get(0).getKind());
    }

    @Test
    void replaceAllRejectsDuplicateIdentifier() {
        when(productService.requireExists(1L)).thenReturn(product(IotProductService.STATUS_DRAFT));

        BizException exception = assertThrows(BizException.class,
                () -> service.replaceAll(1L, request(
                        item("property", "temperature", "double", null),
                        item("property", "temperature", "double", null))));

        assertTrue(exception.getMessage().contains("重复"));
        verify(thingModelMapper, never()).deletePhysicalByProductId(1L);
    }

    @Test
    void replaceAllRejectsPropertyWithoutDataType() {
        when(productService.requireExists(1L)).thenReturn(product(IotProductService.STATUS_DRAFT));

        BizException exception = assertThrows(BizException.class,
                () -> service.replaceAll(1L, request(item("property", "temperature", null, null))));

        assertTrue(exception.getMessage().contains("dataType"));
    }

    @Test
    void replaceAllRejectsInvalidRange() {
        when(productService.requireExists(1L)).thenReturn(product(IotProductService.STATUS_DRAFT));

        BizException exception = assertThrows(BizException.class,
                () -> service.replaceAll(1L, request(
                        item("property", "temperature", "double", Map.of("min", 100, "max", 50)))));

        assertTrue(exception.getMessage().contains("min 不能大于 max"));
    }

    @Test
    void replaceAllSerializesSpec() {
        when(productService.requireExists(1L)).thenReturn(product(IotProductService.STATUS_DRAFT));

        service.replaceAll(1L, request(item("property", "temperature", "double", Map.of("min", 0, "max", 10))));

        ArgumentCaptor<com.bluewhale.iot.entity.IotThingModel> captor =
                ArgumentCaptor.forClass(com.bluewhale.iot.entity.IotThingModel.class);
        verify(thingModelMapper).insert(captor.capture());
        assertTrue(captor.getValue().getSpecJson().contains("\"max\":10"));
        assertEquals(0, captor.getValue().getRequired());
    }

    @Test
    void validatorAcceptsPropertyWithoutSpec() {
        ThingModelValidator.validate(List.of(item("property", "humidity", "double", null)));
    }

    private static ThingModelSaveRequest request(ThingModelItem... items) {
        ThingModelSaveRequest request = new ThingModelSaveRequest();
        request.setItems(List.of(items));
        return request;
    }

    private static ThingModelItem item(String kind, String identifier, String dataType, Object spec) {
        ThingModelItem item = new ThingModelItem();
        item.setKind(kind);
        item.setIdentifier(identifier);
        item.setName("名称-" + identifier);
        item.setDataType(dataType);
        item.setSpec(spec);
        return item;
    }

    private static IotProduct product(String status) {
        IotProduct product = new IotProduct();
        product.setId(1L);
        product.setCode("temp-sensor");
        product.setStatus(status);
        return product;
    }

    private static com.bluewhale.iot.entity.IotThingModel model(String kind, String identifier, String dataType) {
        com.bluewhale.iot.entity.IotThingModel model = new com.bluewhale.iot.entity.IotThingModel();
        model.setProductId(1L);
        model.setKind(kind);
        model.setIdentifier(identifier);
        model.setName("名称-" + identifier);
        model.setDataType(dataType);
        model.setRequired(0);
        return model;
    }
}
