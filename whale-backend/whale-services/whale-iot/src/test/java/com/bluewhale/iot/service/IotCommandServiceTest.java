package com.bluewhale.iot.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.iot.dto.CommandCreateRequest;
import com.bluewhale.iot.dto.CommandVO;
import com.bluewhale.iot.entity.IotCommand;
import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.entity.IotThingModel;
import com.bluewhale.iot.mapper.IotCommandMapper;
import com.bluewhale.iot.mapper.IotDeviceMapper;
import com.bluewhale.iot.mapper.IotThingModelMapper;
import com.bluewhale.iot.support.EmqxPublishClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 指令服务单测：投递与状态流转、幂等、物模型校验与回执收敛。
 *
 * @author BlueWhale
 */
class IotCommandServiceTest {

    private static final Long DEVICE_ID = 9L;

    private IotCommandMapper commandMapper;

    private IotDeviceMapper deviceMapper;

    private IotThingModelMapper thingModelMapper;

    private IotProductService productService;

    private IotShadowService shadowService;

    private IotStreamService streamService;

    private EmqxPublishClient publishClient;

    private IotCommandService service;

    @BeforeEach
    void setUp() {
        commandMapper = mock(IotCommandMapper.class);
        deviceMapper = mock(IotDeviceMapper.class);
        thingModelMapper = mock(IotThingModelMapper.class);
        productService = mock(IotProductService.class);
        shadowService = mock(IotShadowService.class);
        streamService = mock(IotStreamService.class);
        publishClient = mock(EmqxPublishClient.class);
        service = new IotCommandService(commandMapper, deviceMapper, thingModelMapper, productService,
                shadowService, streamService, publishClient);
    }

    @Test
    void createPublishesCommandAndMarksSent() {
        mockActiveDevice();
        when(thingModelMapper.selectList(any())).thenReturn(List.of());
        IotProduct product = new IotProduct();
        product.setCode("temp-sensor");
        when(productService.requireExists(any())).thenReturn(product);
        when(publishClient.publish(anyString(), anyString())).thenReturn(true);

        CommandVO vo = service.create(DEVICE_ID, request("reboot", null), 100L);

        assertEquals(IotCommandService.STATUS_SENT, vo.getStatus());
        assertNotNull(vo.getMessageId());
        verify(commandMapper).insert(any(IotCommand.class));
        verify(commandMapper).updateById(any(IotCommand.class));
        verify(publishClient).publish(eq("bw/1/temp-sensor/dev-001/down/command"), anyString());
        verify(streamService).publish(eq(DEVICE_ID), eq(IotStreamService.EVENT_COMMAND), any());
    }

    @Test
    void createKeepsPendingWhenPublishFails() {
        mockActiveDevice();
        when(thingModelMapper.selectList(any())).thenReturn(List.of());
        IotProduct product = new IotProduct();
        product.setCode("temp-sensor");
        when(productService.requireExists(any())).thenReturn(product);
        when(publishClient.publish(anyString(), anyString())).thenReturn(false);

        CommandVO vo = service.create(DEVICE_ID, request("reboot", null), 100L);

        assertEquals(IotCommandService.STATUS_PENDING, vo.getStatus());
        verify(commandMapper, never()).updateById(any(IotCommand.class));
    }

    @Test
    void createReturnsExistingCommandForDuplicateMessageId() {
        mockActiveDevice();
        IotCommand existed = new IotCommand();
        existed.setId(1L);
        existed.setDeviceId(DEVICE_ID);
        existed.setMessageId("msg-12345678");
        existed.setStatus(IotCommandService.STATUS_SUCCESS);
        when(commandMapper.selectOne(any())).thenReturn(existed);

        CommandVO vo = service.create(DEVICE_ID, request("reboot", "msg-12345678"), 100L);

        assertEquals("msg-12345678", vo.getMessageId());
        assertEquals(IotCommandService.STATUS_SUCCESS, vo.getStatus());
        verify(commandMapper, never()).insert(any(IotCommand.class));
        verify(publishClient, never()).publish(anyString(), anyString());
    }

    @Test
    void createRejectsDisabledDevice() {
        IotDevice device = device();
        device.setStatus(IotDeviceService.STATUS_DISABLED);
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device);

        assertThrows(BizException.class, () -> service.create(DEVICE_ID, request("reboot", null), 100L));
    }

    @Test
    void createRejectsServiceNotDefinedInThingModel() {
        mockActiveDevice();
        IotThingModel model = new IotThingModel();
        model.setKind(IotThingModelService.KIND_SERVICE);
        model.setIdentifier("reboot");
        when(thingModelMapper.selectList(any())).thenReturn(List.of(model));

        assertThrows(BizException.class, () -> service.create(DEVICE_ID, request("upgrade", null), 100L));
        verify(commandMapper, never()).insert(any(IotCommand.class));
    }

    @Test
    void onReplyMarksSuccess() {
        IotCommand command = new IotCommand();
        command.setId(5L);
        command.setDeviceId(DEVICE_ID);
        command.setMessageId("msg-1");
        command.setStatus(IotCommandService.STATUS_SENT);
        when(commandMapper.selectOne(any())).thenReturn(command);

        assertTrue(service.onReply("{\"id\":\"msg-1\",\"code\":0,\"data\":{\"ok\":true}}"));

        assertEquals(IotCommandService.STATUS_SUCCESS, command.getStatus());
        assertNotNull(command.getReplyTime());
        verify(commandMapper).updateById(command);
        verify(streamService).publish(eq(DEVICE_ID), eq(IotStreamService.EVENT_COMMAND), any());
    }

    @Test
    void onReplyMarksFailedForNonZeroCode() {
        IotCommand command = new IotCommand();
        command.setId(5L);
        command.setDeviceId(DEVICE_ID);
        command.setMessageId("msg-2");
        command.setStatus(IotCommandService.STATUS_SENT);
        when(commandMapper.selectOne(any())).thenReturn(command);

        assertTrue(service.onReply("{\"id\":\"msg-2\",\"code\":500}"));

        assertEquals(IotCommandService.STATUS_FAILED, command.getStatus());
    }

    @Test
    void onReplyIsIdempotentForFinishedCommand() {
        IotCommand command = new IotCommand();
        command.setId(5L);
        command.setDeviceId(DEVICE_ID);
        command.setMessageId("msg-3");
        command.setStatus(IotCommandService.STATUS_SUCCESS);
        when(commandMapper.selectOne(any())).thenReturn(command);

        assertTrue(service.onReply("{\"id\":\"msg-3\",\"code\":1}"));

        assertEquals(IotCommandService.STATUS_SUCCESS, command.getStatus());
        verify(commandMapper, never()).updateById(any(IotCommand.class));
    }

    @Test
    void onReplyReturnsFalseWhenCommandMissing() {
        when(commandMapper.selectOne(any())).thenReturn(null);

        assertFalse(service.onReply("{\"id\":\"unknown\",\"code\":0}"));
    }

    @Test
    void onReplyReturnsFalseForUnparsablePayload() {
        assertFalse(service.onReply("not-json"));
        assertFalse(service.onReply("{\"code\":0}"));
    }

    /**
     * 构造指令请求。
     *
     * @param identifier 服务标识符
     * @param messageId  幂等键（可空）
     * @return 请求对象
     */
    private static CommandCreateRequest request(String identifier, String messageId) {
        CommandCreateRequest request = new CommandCreateRequest();
        request.setIdentifier(identifier);
        request.setMessageId(messageId);
        request.setParams(null);
        return request;
    }

    /**
     * 桩：启用的设备。
     */
    private void mockActiveDevice() {
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device());
    }

    /**
     * 构造启用设备。
     *
     * @return 设备实体
     */
    private static IotDevice device() {
        IotDevice device = new IotDevice();
        device.setId(DEVICE_ID);
        device.setTenantId(1L);
        device.setProductId(7L);
        device.setDeviceName("dev-001");
        device.setStatus(IotDeviceService.STATUS_ACTIVE);
        return device;
    }
}
