package com.bluewhale.crm.customer.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.command.PoolAssignCommand;
import com.bluewhale.crm.customer.application.command.PoolRuleSaveCommand;
import com.bluewhale.crm.customer.application.view.PoolRecycleResultView;
import com.bluewhale.crm.customer.application.view.PoolRuleView;
import com.bluewhale.crm.customer.domain.model.Customer;
import com.bluewhale.crm.customer.domain.model.CustomerPoolRule;
import com.bluewhale.crm.customer.domain.model.CustomerQuery;
import com.bluewhale.crm.customer.domain.model.CustomerStatus;
import com.bluewhale.crm.customer.domain.model.PoolStatus;
import com.bluewhale.crm.customer.domain.repository.CustomerPoolRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CustomerPoolAppServiceImpl} 单元测试：领取并发、每日上限、释放 / 分配范围与规则回收。
 *
 * @author BlueWhale
 */
class CustomerPoolAppServiceImplTest {

    private CustomerRepository customerRepository;

    private CustomerPoolRepository poolRepository;

    private CrmProperties properties;

    private CustomerPoolAppServiceImpl service;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        poolRepository = mock(CustomerPoolRepository.class);
        properties = new CrmProperties();
        service = new CustomerPoolAppServiceImpl(customerRepository, poolRepository, properties);
    }

    @Test
    @DisplayName("公海分页：强制附加公海状态条件")
    void shouldForcePoolStatusOnPage() {
        when(customerRepository.count(any())).thenReturn(0L);
        when(customerRepository.page(any())).thenReturn(List.of());
        CustomerQuery query = new CustomerQuery();

        service.page(query, manager(1L));

        assertEquals(PoolStatus.IN_POOL.name(), query.getPoolStatus());
    }

    @Test
    @DisplayName("领取：客户已被他人持有直接冲突")
    void shouldRejectClaimForOwnedCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(ownedCustomer(1L, 99L)));

        BizException exception = assertThrows(BizException.class, () -> service.claim(1L, sales(7L)));
        assertEquals(CrmErrorCode.POOL_CLAIM_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("领取：超过每日领取上限被拒绝")
    void shouldRejectClaimWhenDailyLimitReached() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(poolCustomer(1L)));
        CustomerPoolRule rule = CustomerPoolRule.defaults();
        rule.setDailyClaimLimit(2);
        when(poolRepository.findEnabled()).thenReturn(Optional.of(rule));
        when(poolRepository.countClaims(anyLong(), any())).thenReturn(2L);

        BizException exception = assertThrows(BizException.class, () -> service.claim(1L, sales(7L)));
        assertEquals(CrmErrorCode.POOL_CLAIM_LIMIT, exception.getCode());
        verify(customerRepository, never()).claim(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("领取：原子更新失败说明已被并发抢占")
    void shouldRejectClaimWhenAtomicUpdateFails() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(poolCustomer(1L)));
        when(customerRepository.claim(anyLong(), anyLong(), any())).thenReturn(false);

        BizException exception = assertThrows(BizException.class, () -> service.claim(1L, sales(7L)));
        assertEquals(CrmErrorCode.POOL_CLAIM_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("领取：成功写负责人并留痕")
    void shouldClaimAndWriteLog() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(poolCustomer(1L)));
        when(customerRepository.claim(anyLong(), anyLong(), any())).thenReturn(true);

        service.claim(1L, sales(7L));

        verify(poolRepository).insertOwnerLog(any());
        verify(customerRepository).claim(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("释放：已在公海的客户无需重复释放")
    void shouldRejectReleaseForPoolCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(poolCustomer(1L)));

        BizException exception = assertThrows(BizException.class, () -> service.release(1L, "重复释放", manager(1L)));
        assertEquals(CrmErrorCode.CUSTOMER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("释放：非负责人释放他人客户按不存在处理")
    void shouldRejectReleaseForOtherOwner() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(ownedCustomer(1L, 99L)));

        BizException exception = assertThrows(BizException.class, () -> service.release(1L, null, sales(7L)));
        assertEquals(CrmErrorCode.CUSTOMER_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("释放：负责人可将客户释放到公海并留痕")
    void shouldReleaseOwnedCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(ownedCustomer(1L, 7L)));
        when(customerRepository.release(anyLong(), any())).thenReturn(true);

        service.release(1L, "转公海", sales(7L));

        verify(poolRepository).insertOwnerLog(any());
        verify(customerRepository).release(anyLong(), any());
    }

    @Test
    @DisplayName("分配：非全量数据范围角色无权分配")
    void shouldRejectAssignWithoutDataScope() {
        PoolAssignCommand command = new PoolAssignCommand();
        command.setOwnerId(9L);

        BizException exception = assertThrows(BizException.class, () -> service.assign(1L, command, sales(7L)));
        assertEquals(CrmErrorCode.DATA_SCOPE_DENIED, exception.getCode());
    }

    @Test
    @DisplayName("分配：必须指定负责人")
    void shouldRejectAssignWithoutOwner() {
        BizException exception = assertThrows(BizException.class,
                () -> service.assign(1L, new PoolAssignCommand(), manager(1L)));
        assertEquals(CrmErrorCode.CUSTOMER_OWNER_REQUIRED, exception.getCode());
    }

    @Test
    @DisplayName("分配：全量角色可改负责人并留痕")
    void shouldAssignOwner() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(ownedCustomer(1L, 3L)));
        when(customerRepository.updateOwner(anyLong(), anyLong(), any(), any())).thenReturn(true);
        PoolAssignCommand command = new PoolAssignCommand();
        command.setOwnerId(9L);
        command.setRemark("区域调整");

        service.assign(1L, command, manager(1L));

        verify(poolRepository).insertOwnerLog(any());
        verify(customerRepository).updateOwner(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("回收：按规则回收候选并回写执行结果")
    void shouldRecycleCandidates() {
        CustomerPoolRule rule = CustomerPoolRule.defaults();
        rule.setInactiveDays(30);
        when(poolRepository.findEnabled()).thenReturn(Optional.of(rule));
        when(customerRepository.findRecycleCandidates(any(), any(), any(), anyInt()))
                .thenReturn(List.of(ownedCustomer(1L, 7L), ownedCustomer(2L, 8L)));
        when(customerRepository.release(anyLong(), any())).thenReturn(true);

        PoolRecycleResultView result = service.recycle(manager(1L));

        assertEquals(2, result.getRecycledCount());
        assertEquals(rule.getRuleName(), result.getRuleName());
        verify(poolRepository, times(2)).insertOwnerLog(any());
        verify(poolRepository).saveRule(rule);
    }

    @Test
    @DisplayName("回收：规则停用或未跟进天数为 0 时不执行")
    void shouldSkipRecycleWhenRuleInactive() {
        CustomerPoolRule rule = CustomerPoolRule.defaults();
        rule.setEnabled(Boolean.FALSE);
        when(poolRepository.findEnabled()).thenReturn(Optional.of(rule));

        assertEquals(0, service.recycle(manager(1L)).getRecycledCount());
        verify(customerRepository, never()).findRecycleCandidates(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("回收：无权角色被拒绝")
    void shouldRejectRecycleWithoutDataScope() {
        BizException exception = assertThrows(BizException.class, () -> service.recycle(sales(7L)));
        assertEquals(CrmErrorCode.DATA_SCOPE_DENIED, exception.getCode());
    }

    @Test
    @DisplayName("规则：不存在时返回默认规则（30 天 / C,D / 不限领取）")
    void shouldReturnDefaultRule() {
        when(poolRepository.findEnabled()).thenReturn(Optional.empty());

        PoolRuleView view = service.getRule(manager(1L));

        assertEquals(30, view.getInactiveDays());
        assertEquals("C,D", view.getLevels());
        assertEquals(0, view.getDailyClaimLimit());
        assertTrue(view.getEnabled());
    }

    @Test
    @DisplayName("规则保存：等级码规范化，缺口沿用原值")
    void shouldNormalizeRuleLevels() {
        when(poolRepository.findEnabled()).thenReturn(Optional.empty());
        PoolRuleSaveCommand command = new PoolRuleSaveCommand();
        command.setLevels("KEY,normal");
        command.setInactiveDays(45);

        PoolRuleView view = service.saveRule(command, manager(1L));

        assertEquals("A,C", view.getLevels());
        assertEquals(45, view.getInactiveDays());
        assertEquals(0, view.getNoOpportunityDays());
        verify(poolRepository).saveRule(any());
    }

    @Test
    @DisplayName("规则保存：负数阈值拒绝")
    void shouldRejectNegativeRuleValue() {
        when(poolRepository.findEnabled()).thenReturn(Optional.empty());
        PoolRuleSaveCommand command = new PoolRuleSaveCommand();
        command.setInactiveDays(-1);

        BizException exception = assertThrows(BizException.class,
                () -> service.saveRule(command, manager(1L)));
        assertEquals(CrmErrorCode.PARAM_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("规则保存：按 ID 更新不存在的规则报错")
    void shouldRejectUnknownRuleId() {
        PoolRuleSaveCommand command = new PoolRuleSaveCommand();
        command.setId(9L);
        when(poolRepository.findById(9L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> service.saveRule(command, manager(1L)));
        assertEquals(CrmErrorCode.POOL_RULE_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("规则保存：非全量数据范围无权修改")
    void shouldRejectRuleSaveWithoutDataScope() {
        BizException exception = assertThrows(BizException.class,
                () -> service.saveRule(new PoolRuleSaveCommand(), sales(7L)));
        assertEquals(CrmErrorCode.DATA_SCOPE_DENIED, exception.getCode());
    }

    @Test
    @DisplayName("负责人留痕：他人客户按不存在处理")
    void shouldHideOwnerLogsOfOtherOwner() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(ownedCustomer(1L, 99L)));

        BizException exception = assertThrows(BizException.class, () -> service.ownerLogs(1L, sales(7L)));
        assertEquals(CrmErrorCode.CUSTOMER_NOT_FOUND, exception.getCode());
        verify(poolRepository, never()).listOwnerLogs(anyLong(), anyInt());
    }

    @Test
    @DisplayName("负责人留痕：本人客户可查看")
    void shouldListOwnerLogs() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(ownedCustomer(1L, 7L)));
        when(poolRepository.listOwnerLogs(anyLong(), anyInt())).thenReturn(List.of());

        assertTrue(service.ownerLogs(1L, sales(7L)).isEmpty());
    }

    private static CrmActor sales(Long userId) {
        return new CrmActor(userId, "sales", Set.of("crm_sales"), Set.of());
    }

    private static CrmActor manager(Long userId) {
        return new CrmActor(userId, "manager", Set.of("crm_sales_manager"), Set.of());
    }

    private static Customer ownedCustomer(Long id, Long ownerId) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setName("蓝鲸科技" + id);
        customer.setStatus(CustomerStatus.ACTIVE.name());
        customer.setOwnerId(ownerId);
        customer.setPoolStatus(PoolStatus.OWNED.name());
        return customer;
    }

    private static Customer poolCustomer(Long id) {
        Customer customer = ownedCustomer(id, null);
        customer.setPoolStatus(PoolStatus.IN_POOL.name());
        return customer;
    }
}
