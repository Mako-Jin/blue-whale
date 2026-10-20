package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pay.dto.PayOrderCreateRequest;
import com.bluewhale.pay.dto.PayOrderQueryRequest;
import com.bluewhale.pay.entity.PayOrder;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.vo.PayOrderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PayOrderServiceImpl} 分页、详情、创建查重与状态流转单元测试。
 *
 * @author BlueWhale
 */
class PayOrderServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    private final PayOrderMapper payOrderMapper = mock(PayOrderMapper.class);

    private final PayOrderServiceImpl payOrderService = new PayOrderServiceImpl(payOrderMapper);

    /**
     * 构造支付单实体桩对象。
     *
     * @param id     主键ID
     * @param orderNo 支付单号
     * @param status  状态
     * @return 支付单实体
     */
    private static PayOrder order(Long id, String orderNo, String status) {
        PayOrder entity = new PayOrder();
        entity.setId(id);
        entity.setOrderNo(orderNo);
        entity.setStatus(status);
        entity.setAmount(new BigDecimal("100.00"));
        entity.setCurrency("CNY");
        return entity;
    }

    /**
     * 构造创建请求桩对象。
     *
     * @param orderNo 支付单号
     * @return 创建请求
     */
    private static PayOrderCreateRequest createRequest(String orderNo) {
        PayOrderCreateRequest request = new PayOrderCreateRequest();
        request.setOrderNo(orderNo);
        request.setBusinessKey("BIZ-1");
        request.setAmount(new BigDecimal("88.88"));
        request.setCurrency("CNY");
        request.setChannel("BALANCE");
        return request;
    }

    @Test
    @DisplayName("page：按条件分页查询并映射结果")
    void shouldPageWithConditions() {
        PayOrder entity = order(1L, "PAY20261020001", "CREATED");
        Page<PayOrder> page = new Page<>(1L, 10L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(payOrderMapper.selectPage(ArgumentMatchers.<Page<PayOrder>>any(),
                ArgumentMatchers.<Wrapper<PayOrder>>any())).thenReturn(page);

        PayOrderQueryRequest request = new PayOrderQueryRequest();
        request.setKeyword("PAY2026");
        request.setStatus("CREATED");
        PageResult<PayOrderVO> result = payOrderService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("PAY20261020001", result.getRecords().get(0).getOrderNo());
        assertEquals("CREATED", result.getRecords().get(0).getStatus());
    }

    @Test
    @DisplayName("getDetail：支付单不存在抛 NOT_FOUND")
    void shouldThrowWhenDetailMissing() {
        when(payOrderMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> payOrderService.getDetail(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("create：orderNo 冲突抛参数错误且不落库")
    void shouldRejectDuplicateOrderNo() {
        when(payOrderMapper.selectCount(ArgumentMatchers.<Wrapper<PayOrder>>any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> payOrderService.create(createRequest("PAY20261020001")));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).insert(any(PayOrder.class));
    }

    @Test
    @DisplayName("create：orderNo 唯一时落库并置初始状态 CREATED")
    void shouldCreateWithCreatedStatus() {
        when(payOrderMapper.selectCount(ArgumentMatchers.<Wrapper<PayOrder>>any())).thenReturn(0L);
        PayOrder saved = order(100L, "PAY20261020002", "CREATED");
        when(payOrderMapper.selectById(any())).thenReturn(saved);

        PayOrderVO vo = payOrderService.create(createRequest("PAY20261020002"));

        assertEquals("CREATED", vo.getStatus());
        ArgumentCaptor<PayOrder> captor = ArgumentCaptor.forClass(PayOrder.class);
        verify(payOrderMapper).insert(captor.capture());
        assertEquals("PAY20261020002", captor.getValue().getOrderNo());
        assertEquals("CREATED", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("pay：CREATED → PAID 并回填支付时间")
    void shouldPayFromCreated() {
        PayOrder existing = order(1L, "PAY20261020001", "CREATED");
        PayOrder paid = order(1L, "PAY20261020001", "PAID");
        when(payOrderMapper.selectById(1L)).thenReturn(existing, paid);
        when(payOrderMapper.updateById(any(PayOrder.class))).thenReturn(1);

        PayOrderVO vo = payOrderService.pay(1L);

        assertEquals("PAID", vo.getStatus());
        ArgumentCaptor<PayOrder> captor = ArgumentCaptor.forClass(PayOrder.class);
        verify(payOrderMapper).updateById(captor.capture());
        assertEquals("PAID", captor.getValue().getStatus());
        assertNotNull(captor.getValue().getPayTime());
    }

    @Test
    @DisplayName("pay：非 CREATED 状态拒绝流转且不落库")
    void shouldRejectPayWhenNotCreated() {
        PayOrder existing = order(2L, "PAY20261020003", "PAID");
        when(payOrderMapper.selectById(2L)).thenReturn(existing);

        BizException ex = assertThrows(BizException.class, () -> payOrderService.pay(2L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).updateById(any(PayOrder.class));
    }

    @Test
    @DisplayName("pay：支付单不存在抛 NOT_FOUND")
    void shouldThrowWhenPayMissing() {
        when(payOrderMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> payOrderService.pay(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("cancel：CREATED → CANCELLED")
    void shouldCancelFromCreated() {
        PayOrder existing = order(3L, "PAY20261020004", "CREATED");
        PayOrder cancelled = order(3L, "PAY20261020004", "CANCELLED");
        when(payOrderMapper.selectById(3L)).thenReturn(existing, cancelled);
        when(payOrderMapper.updateById(any(PayOrder.class))).thenReturn(1);

        PayOrderVO vo = payOrderService.cancel(3L);

        assertEquals("CANCELLED", vo.getStatus());
        ArgumentCaptor<PayOrder> captor = ArgumentCaptor.forClass(PayOrder.class);
        verify(payOrderMapper).updateById(captor.capture());
        assertEquals("CANCELLED", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("cancel：非 CREATED 状态拒绝流转且不落库")
    void shouldRejectCancelWhenNotCreated() {
        PayOrder existing = order(4L, "PAY20261020005", "CANCELLED");
        when(payOrderMapper.selectById(4L)).thenReturn(existing);

        BizException ex = assertThrows(BizException.class, () -> payOrderService.cancel(4L));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(payOrderMapper, never()).updateById(any(PayOrder.class));
    }
}
