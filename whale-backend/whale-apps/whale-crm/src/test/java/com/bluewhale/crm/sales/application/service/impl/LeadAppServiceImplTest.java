package com.bluewhale.crm.sales.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.CustomerView;
import com.bluewhale.crm.sales.application.command.LeadActivityCreateCommand;
import com.bluewhale.crm.sales.application.command.LeadConvertCommand;
import com.bluewhale.crm.sales.application.command.LeadInvalidCommand;
import com.bluewhale.crm.sales.application.command.LeadSaveCommand;
import com.bluewhale.crm.sales.application.command.LeadStatusCommand;
import com.bluewhale.crm.sales.application.view.LeadActivityView;
import com.bluewhale.crm.sales.application.view.LeadConvertResultView;
import com.bluewhale.crm.sales.application.view.LeadView;
import com.bluewhale.crm.sales.domain.model.Lead;
import com.bluewhale.crm.sales.domain.model.LeadQuery;
import com.bluewhale.crm.sales.domain.model.LeadStatus;
import com.bluewhale.crm.sales.domain.model.Opportunity;
import com.bluewhale.crm.sales.domain.repository.LeadActivityRepository;
import com.bluewhale.crm.sales.domain.repository.LeadRepository;
import com.bluewhale.crm.sales.domain.repository.OpportunityRepository;
import com.bluewhale.crm.sales.domain.repository.OpportunityStageHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LeadAppServiceImpl} 单元测试：编号、查重、数据范围、状态机与转化幂等。
 *
 * @author BlueWhale
 */
class LeadAppServiceImplTest {

    private LeadRepository leadRepository;

    private LeadActivityRepository activityRepository;

    private OpportunityRepository opportunityRepository;

    private OpportunityStageHistoryRepository historyRepository;

    private CustomerAppService customerAppService;

    private CrmProperties properties;

    private LeadAppServiceImpl service;

    @BeforeEach
    void setUp() {
        leadRepository = mock(LeadRepository.class);
        activityRepository = mock(LeadActivityRepository.class);
        opportunityRepository = mock(OpportunityRepository.class);
        historyRepository = mock(OpportunityStageHistoryRepository.class);
        customerAppService = mock(CustomerAppService.class);
        properties = new CrmProperties();
        service = new LeadAppServiceImpl(leadRepository, activityRepository, opportunityRepository,
                historyRepository, customerAppService, properties);
    }

    @Test
    @DisplayName("新建线索：生成 LEAD-年份-序号编号，普通销售归属本人")
    void shouldCreateLeadWithGeneratedNo() {
        when(leadRepository.findMaxNo(anyString())).thenReturn(null);
        when(leadRepository.findDuplicates(any(), any(), any())).thenReturn(List.of());

        LeadView view = service.create(command("华星科技", "13800000000", null), sales(7L));

        String expectedPrefix = "LEAD-" + LocalDate.now().getYear() + "-";
        assertTrue(view.getLeadNo().startsWith(expectedPrefix));
        assertEquals("NEW", view.getStatus());
        assertEquals(7L, view.getOwnerId());
        assertEquals("138****0000", view.getContactPhone());
        verify(leadRepository).insert(any());
    }

    @Test
    @DisplayName("新建线索：电话疑似重复时拒绝，确认后可放行")
    void shouldRejectDuplicateUnlessConfirmed() {
        when(leadRepository.findMaxNo(anyString())).thenReturn(null);
        when(leadRepository.findDuplicates(any(), any(), any())).thenReturn(List.of(lead(9L, 7L, LeadStatus.NEW)));

        BizException exception = assertThrows(BizException.class,
                () -> service.create(command("重复线索", "13800000000", null), sales(7L)));
        assertEquals(CrmErrorCode.LEAD_DUPLICATE, exception.getCode());
        verify(leadRepository, never()).insert(any());

        LeadSaveCommand confirmed = command("重复线索", "13800000000", null);
        confirmed.setConfirmedDuplicate(true);
        service.create(confirmed, sales(7L));
        verify(leadRepository).insert(any());
    }

    @Test
    @DisplayName("数据范围：普通销售访问他人线索按不存在处理，管理者可见")
    void shouldHideOtherOwnersLead() {
        when(leadRepository.findById(1L)).thenReturn(lead(1L, 7L, LeadStatus.NEW));

        BizException exception = assertThrows(BizException.class, () -> service.detail(1L, sales(8L)));
        assertEquals(CrmErrorCode.LEAD_NOT_FOUND, exception.getCode());
        assertEquals("NEW", service.detail(1L, manager(8L)).getStatus());
    }

    @Test
    @DisplayName("分页：非全量数据范围强制收口到本人")
    void shouldApplyOwnerScopeOnPage() {
        when(leadRepository.page(any())).thenReturn(List.of(lead(1L, 7L, LeadStatus.NEW)));
        when(leadRepository.count(any())).thenReturn(1L);
        LeadQuery query = new LeadQuery();
        query.setOwnerId(99L);

        PageResult<LeadView> result = service.page(query, sales(7L));

        assertEquals(7L, query.getOwnerId());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("标记无效：原因空白拒绝")
    void shouldRequireInvalidReason() {
        when(leadRepository.findById(1L)).thenReturn(lead(1L, 7L, LeadStatus.NEW));
        LeadInvalidCommand command = new LeadInvalidCommand();
        command.setReason("   ");

        BizException exception = assertThrows(BizException.class,
                () -> service.invalidate(1L, command, sales(7L)));
        assertEquals(CrmErrorCode.LEAD_INVALID_REASON_REQUIRED, exception.getCode());
        verify(leadRepository, never()).update(any());
    }

    @Test
    @DisplayName("转化：新建客户并创建首个商机，写入阶段历史")
    void shouldConvertLeadIntoCustomerAndOpportunity() {
        when(leadRepository.findById(1L)).thenReturn(lead(1L, 7L, LeadStatus.QUALIFIED));
        CustomerView customer = new CustomerView();
        customer.setId(100L);
        when(customerAppService.create(any(), any())).thenReturn(customer);
        doAnswer(invocation -> {
            Opportunity opportunity = invocation.getArgument(0);
            opportunity.setId(200L);
            return null;
        }).when(opportunityRepository).insert(any());
        when(leadRepository.markConverted(anyLong(), anyLong(), anyLong(), any())).thenReturn(true);

        LeadConvertResultView result = service.convert(1L, new LeadConvertCommand(), sales(7L));

        assertEquals(100L, result.getCustomerId());
        assertEquals(200L, result.getOpportunityId());
        assertFalse(result.isAlreadyConverted());
        verify(customerAppService).create(any(), any());
        verify(opportunityRepository).insert(any());
        verify(historyRepository).insert(any());
    }

    @Test
    @DisplayName("转化：已转化线索幂等返回既有结果，不重复建客户与商机")
    void shouldReturnExistingResultWhenAlreadyConverted() {
        Lead converted = lead(1L, 7L, LeadStatus.CONVERTED);
        converted.setCustomerId(100L);
        converted.setOpportunityId(200L);
        when(leadRepository.findById(1L)).thenReturn(converted);

        LeadConvertResultView result = service.convert(1L, new LeadConvertCommand(), sales(7L));

        assertTrue(result.isAlreadyConverted());
        assertEquals(100L, result.getCustomerId());
        verify(customerAppService, never()).create(any(), any());
        verify(opportunityRepository, never()).insert(any());
    }

    @Test
    @DisplayName("转化：并发已被转化时回滚并提示")
    void shouldFailWhenConcurrentConversionWins() {
        when(leadRepository.findById(1L)).thenReturn(lead(1L, 7L, LeadStatus.QUALIFIED));
        CustomerView customer = new CustomerView();
        customer.setId(100L);
        when(customerAppService.create(any(), any())).thenReturn(customer);
        when(leadRepository.markConverted(anyLong(), anyLong(), anyLong(), any())).thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> service.convert(1L, new LeadConvertCommand(), sales(7L)));
        assertEquals(CrmErrorCode.LEAD_ALREADY_CONVERTED, exception.getCode());
    }

    @Test
    @DisplayName("追加活动：NEW 线索自动推进为 CONTACTED")
    void shouldMarkContactedWhenAddingActivity() {
        when(leadRepository.findById(1L)).thenReturn(lead(1L, 7L, LeadStatus.NEW));
        LeadActivityCreateCommand command = new LeadActivityCreateCommand();
        command.setChannel("wechat");
        command.setContent("首次微信沟通");

        LeadActivityView view = service.addActivity(1L, command, sales(7L));

        assertEquals("WECHAT", view.getChannel());
        verify(activityRepository).insert(any());
        verify(leadRepository).update(any());
    }

    @Test
    @DisplayName("状态推进：CONTACTED 后 QUALIFIED，非法目标拒绝")
    void shouldAdvanceLeadStatus() {
        when(leadRepository.findById(1L)).thenReturn(lead(1L, 7L, LeadStatus.CONTACTED));
        LeadStatusCommand command = new LeadStatusCommand();
        command.setStatus("QUALIFIED");

        LeadView view = service.advance(1L, command, sales(7L));

        assertEquals("QUALIFIED", view.getStatus());
        LeadStatusCommand invalid = new LeadStatusCommand();
        invalid.setStatus("CONVERTED");
        BizException exception = assertThrows(BizException.class,
                () -> service.advance(1L, invalid, sales(7L)));
        assertEquals(CrmErrorCode.LEAD_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("门禁关闭时拒绝线索查询")
    void shouldRejectWhenGateClosed() {
        properties.setDemandGatePassed(false);
        BizException exception = assertThrows(BizException.class,
                () -> service.page(new LeadQuery(), sales(7L)));
        assertEquals(CrmErrorCode.GATE_CLOSED, exception.getCode());
    }

    @Test
    @DisplayName("活动分页：返回分页信封")
    void shouldPageActivities() {
        when(leadRepository.findById(1L)).thenReturn(lead(1L, 7L, LeadStatus.NEW));
        when(activityRepository.page(any(), any())).thenReturn(List.of());
        when(activityRepository.count(any())).thenReturn(0L);

        PageQuery pageQuery = new PageQuery();
        PageResult<LeadActivityView> result = service.activityPage(1L, pageQuery, sales(7L));

        assertEquals(0, result.getRecords().size());
        assertTrue(result.getTotal() == 0L);
    }

    private LeadSaveCommand command(String name, String phone, String email) {
        LeadSaveCommand command = new LeadSaveCommand();
        command.setName(name);
        command.setContactPhone(phone);
        command.setContactEmail(email);
        command.setSource("WEB");
        return command;
    }

    private static Lead lead(Long id, Long ownerId, LeadStatus status) {
        Lead lead = new Lead();
        lead.setId(id);
        lead.setOwnerId(ownerId);
        lead.setStatus(status);
        lead.setName("测试线索");
        lead.setLeadNo("LEAD-2026-000001");
        return lead;
    }

    private static CrmActor sales(Long userId) {
        return new CrmActor(userId, "sales", Set.of("crm_sales"), Set.of());
    }

    private static CrmActor manager(Long userId) {
        return new CrmActor(userId, "manager", Set.of("crm_sales_manager"), Set.of());
    }
}
