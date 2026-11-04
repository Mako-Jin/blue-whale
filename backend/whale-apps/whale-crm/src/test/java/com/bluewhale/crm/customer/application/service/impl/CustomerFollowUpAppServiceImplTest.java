package com.bluewhale.crm.customer.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.command.FollowUpCreateCommand;
import com.bluewhale.crm.customer.application.view.FollowUpView;
import com.bluewhale.crm.customer.domain.model.Customer;
import com.bluewhale.crm.customer.domain.model.CustomerStatus;
import com.bluewhale.crm.customer.domain.model.PoolStatus;
import com.bluewhale.crm.customer.domain.repository.CustomerFollowUpRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CustomerFollowUpAppServiceImpl} 单元测试：append-only、回写最近跟进与分页上限。
 *
 * @author BlueWhale
 */
class CustomerFollowUpAppServiceImplTest {

    private CustomerRepository customerRepository;

    private CustomerFollowUpRepository followUpRepository;

    private CrmProperties properties;

    private CustomerFollowUpAppServiceImpl service;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        followUpRepository = mock(CustomerFollowUpRepository.class);
        properties = new CrmProperties();
        service = new CustomerFollowUpAppServiceImpl(customerRepository, followUpRepository, properties);
    }

    @Test
    @DisplayName("新增跟进：写入记录并回写客户最近跟进时间")
    void shouldCreateFollowUpAndTouchCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(activeCustomer(1L, 7L)));

        FollowUpView view = service.create(followUpCommand(1L, "CALL", "电话沟通报价"), sales(7L));

        assertEquals("CALL", view.getChannel());
        assertEquals("电话沟通报价", view.getContent());
        assertEquals(7L, view.getCreatedBy());
        verify(followUpRepository).insert(any());
        verify(customerRepository).touchFollowUp(anyLong(), any());
    }

    @Test
    @DisplayName("新增跟进：渠道转为大写枚举，结果与下次动作去空白")
    void shouldNormalizeFields() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(activeCustomer(1L, 7L)));
        FollowUpCreateCommand command = followUpCommand(1L, "wechat", "  微信沟通  ");
        command.setResult("  有意向  ");
        command.setNextAction("  下周回访  ");
        command.setNextActionAt(LocalDateTime.of(2026, 11, 10, 10, 0));

        FollowUpView view = service.create(command, sales(7L));

        assertEquals("WECHAT", view.getChannel());
        assertEquals("微信沟通", view.getContent());
        assertEquals("有意向", view.getResult());
        assertEquals("下周回访", view.getNextAction());
        assertEquals(LocalDateTime.of(2026, 11, 10, 10, 0), view.getNextActionAt());
    }

    @Test
    @DisplayName("新增跟进：必须指定客户")
    void shouldRequireCustomer() {
        BizException exception = assertThrows(BizException.class,
                () -> service.create(followUpCommand(null, "CALL", "内容"), sales(7L)));
        assertEquals(CrmErrorCode.FOLLOW_UP_CUSTOMER_REQUIRED, exception.getCode());
    }

    @Test
    @DisplayName("新增跟进：内容为空被拒绝")
    void shouldRejectBlankContent() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(activeCustomer(1L, 7L)));
        BizException exception = assertThrows(BizException.class,
                () -> service.create(followUpCommand(1L, "CALL", "   "), sales(7L)));
        assertEquals(CrmErrorCode.PARAM_INVALID, exception.getCode());
        verify(followUpRepository, never()).insert(any());
    }

    @Test
    @DisplayName("新增跟进：渠道为空或非法被拒绝")
    void shouldRejectInvalidChannel() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(activeCustomer(1L, 7L)));
        assertEquals(CrmErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> service.create(followUpCommand(1L, null, "内容"), sales(7L))).getCode());
        assertEquals(CrmErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> service.create(followUpCommand(1L, "SMS", "内容"), sales(7L))).getCode());
    }

    @Test
    @DisplayName("新增跟进：他人客户按不存在处理")
    void shouldHideOtherOwnerCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(activeCustomer(1L, 99L)));
        BizException exception = assertThrows(BizException.class,
                () -> service.create(followUpCommand(1L, "CALL", "内容"), sales(7L)));
        assertEquals(CrmErrorCode.CUSTOMER_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("新增跟进：已合并客户不可追加")
    void shouldRejectMergedCustomer() {
        Customer customer = activeCustomer(1L, 7L);
        customer.setStatus(CustomerStatus.MERGED.name());
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        BizException exception = assertThrows(BizException.class,
                () -> service.create(followUpCommand(1L, "CALL", "内容"), manager(1L)));
        assertEquals(CrmErrorCode.CUSTOMER_MERGED_READONLY, exception.getCode());
    }

    @Test
    @DisplayName("分页：超过配置上限时按上限截断")
    void shouldCapPageSize() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(activeCustomer(1L, 7L)));
        when(followUpRepository.count(1L)).thenReturn(1L);
        when(followUpRepository.page(any(), any())).thenReturn(List.of());
        PageQuery pageQuery = new PageQuery();
        pageQuery.setSize(500);

        PageResult<FollowUpView> result = service.page(1L, pageQuery, manager(1L));

        assertEquals(100, result.getSize());
        assertEquals(100, pageQuery.getSize());
    }

    @Test
    @DisplayName("分页：非法页码归一为 1，空页返回空列表")
    void shouldNormalizePageSize() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(activeCustomer(1L, 7L)));
        when(followUpRepository.count(1L)).thenReturn(0L);
        when(followUpRepository.page(any(), any())).thenReturn(List.of());
        PageQuery pageQuery = new PageQuery();
        pageQuery.setSize(0);

        PageResult<FollowUpView> result = service.page(1L, pageQuery, manager(1L));

        assertEquals(1, result.getSize());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("门禁关闭：跟进接口不可用")
    void shouldRejectWhenGateClosed() {
        properties.setDemandGatePassed(false);
        BizException exception = assertThrows(BizException.class,
                () -> service.page(1L, new PageQuery(), manager(1L)));
        assertEquals(CrmErrorCode.GATE_CLOSED, exception.getCode());
    }

    private static CrmActor sales(Long userId) {
        return new CrmActor(userId, "sales", Set.of("crm_sales"), Set.of());
    }

    private static CrmActor manager(Long userId) {
        return new CrmActor(userId, "manager", Set.of("crm_sales_manager"), Set.of());
    }

    private static Customer activeCustomer(Long id, Long ownerId) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setName("蓝鲸科技");
        customer.setStatus(CustomerStatus.ACTIVE.name());
        customer.setOwnerId(ownerId);
        customer.setPoolStatus(PoolStatus.OWNED.name());
        return customer;
    }

    private static FollowUpCreateCommand followUpCommand(Long customerId, String channel, String content) {
        FollowUpCreateCommand command = new FollowUpCreateCommand();
        command.setCustomerId(customerId);
        command.setChannel(channel);
        command.setContent(content);
        return command;
    }
}
