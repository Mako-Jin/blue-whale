package com.bluewhale.message.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.message.dto.MessageTemplateSaveRequest;
import com.bluewhale.message.po.MessageTemplatePo;
import com.bluewhale.message.mapper.MessageTemplateMapper;
import com.bluewhale.message.vo.MessageTemplateVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MessageTemplateServiceImpl} 模板管理单元测试。
 *
 * @author BlueWhale
 */
class MessageTemplateServiceImplTest {

    private final MessageTemplateMapper templateMapper = mock(MessageTemplateMapper.class);

    private final MessageTemplateServiceImpl service = new MessageTemplateServiceImpl(templateMapper);

    @Test
    @DisplayName("新增模板时编码规范化为大写并默认启用")
    void shouldCreateTemplateWithUppercaseCode() {
        when(templateMapper.countByCodeIncludeDeleted("ORDER_PAID", 0L)).thenReturn(0L);
        doAnswer(invocation -> {
            MessageTemplatePo template = invocation.getArgument(0);
            template.setId(1001L);
            return 1;
        }).when(templateMapper).insert(any(MessageTemplatePo.class));

        MessageTemplateVO vo = service.create(request("order_paid"));

        assertEquals(1001L, vo.getId());
        assertEquals("ORDER_PAID", vo.getTemplateCode());
        assertEquals(1, vo.getStatus());
    }

    @Test
    @DisplayName("模板编码重复时拒绝新增（含逻辑删除占用）")
    void shouldRejectDuplicateCode() {
        when(templateMapper.countByCodeIncludeDeleted("ORDER_PAID", 0L)).thenReturn(1L);

        assertThrows(BizException.class, () -> service.create(request("ORDER_PAID")));
    }

    @Test
    @DisplayName("更新不存在的模板时拒绝")
    void shouldRejectUpdateWhenTemplateMissing() {
        when(templateMapper.selectById(9L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.update(9L, request("ORDER_PAID")));
    }

    @Test
    @DisplayName("删除不存在的模板时拒绝")
    void shouldRejectDeleteWhenTemplateMissing() {
        when(templateMapper.selectById(9L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.delete(9L));
    }

    /**
     * 构造模板保存请求。
     *
     * @param code 模板编码
     * @return 保存请求
     */
    private static MessageTemplateSaveRequest request(String code) {
        MessageTemplateSaveRequest request = new MessageTemplateSaveRequest();
        request.setTemplateCode(code);
        request.setTemplateName("订单支付通知");
        request.setTitleTemplate("支付成功");
        request.setContentTemplate("订单 ${orderNo} 已支付");
        request.setRemark("单元测试");
        return request;
    }
}
