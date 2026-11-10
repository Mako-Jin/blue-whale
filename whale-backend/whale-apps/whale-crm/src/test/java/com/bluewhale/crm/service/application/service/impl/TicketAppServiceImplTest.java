package com.bluewhale.crm.service.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.commerce.domain.model.SalesOrder;
import com.bluewhale.crm.commerce.domain.repository.SalesOrderRepository;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.config.CrmServiceProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.ContactView;
import com.bluewhale.crm.customer.application.view.CustomerDetailView;
import com.bluewhale.crm.customer.application.view.CustomerView;
import com.bluewhale.crm.service.application.command.TicketAssignCommand;
import com.bluewhale.crm.service.application.command.TicketCreateCommand;
import com.bluewhale.crm.service.application.command.TicketHandleCommand;
import com.bluewhale.crm.service.application.service.SatisfactionAppService;
import com.bluewhale.crm.service.application.view.TicketView;
import com.bluewhale.crm.service.domain.model.Ticket;
import com.bluewhale.crm.service.domain.model.TicketAction;
import com.bluewhale.crm.service.domain.model.TicketActionType;
import com.bluewhale.crm.service.domain.model.TicketPriority;
import com.bluewhale.crm.service.domain.model.TicketQuery;
import com.bluewhale.crm.service.domain.model.TicketStatus;
import com.bluewhale.crm.service.domain.repository.ServiceCalendarRepository;
import com.bluewhale.crm.service.domain.repository.SlaPolicyRepository;
import com.bluewhale.crm.service.domain.repository.TicketActionRepository;
import com.bluewhale.crm.service.domain.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TicketAppServiceImpl} 应用服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TicketAppServiceImplTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketActionRepository ticketActionRepository;

    @Mock
    private SlaPolicyRepository slaPolicyRepository;

    @Mock
    private ServiceCalendarRepository serviceCalendarRepository;

    @Mock
    private CustomerAppService customerAppService;

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private SatisfactionAppService satisfactionAppService;

    private CrmProperties properties;

    private CrmServiceProperties serviceProperties;

    private TicketAppServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new CrmProperties();
        serviceProperties = new CrmServiceProperties();
        service = new TicketAppServiceImpl(ticketRepository, ticketActionRepository, slaPolicyRepository,
                serviceCalendarRepository, customerAppService, salesOrderRepository, properties, serviceProperties,
                satisfactionAppService);
    }

    @Test
    @DisplayName("建单：客户快照、联系人姓名、工单号与 D7 关单时限写入")
    void shouldCreateTicketWithSnapshotsAndSla() {
        when(customerAppService.detail(eq(10L), any(CrmActor.class))).thenReturn(customerDetail());
        when(ticketRepository.countByPrefix(anyString())).thenReturn(3L);
        doAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(99L);
            return null;
        }).when(ticketRepository).insert(any(Ticket.class));

        TicketCreateCommand command = createCommand();
        TicketView view = service.create(command, adminActor());

        assertEquals(99L, view.getId());
        assertTrue(view.getTicketNo().endsWith("-0004"));
        assertEquals(TicketStatus.NEW.name(), view.getStatus());
        assertEquals("客户A", view.getCustomerName());
        assertEquals("CUS-1", view.getCustomerCode());
        assertEquals("李四", view.getContactName());
        assertEquals("设备离线", view.getTitle());
        assertEquals(TicketPriority.P1.name(), view.getPriority());
        assertEquals(20L, view.getOwnerId());
        assertEquals(30L, view.getDeptId());
        assertEquals(0L, view.getPausedSeconds());
        assertNotNull(view.getResponseDeadline());
        assertNotNull(view.getResolveDeadline());

        ArgumentCaptor<TicketAction> actionCaptor = ArgumentCaptor.forClass(TicketAction.class);
        verify(ticketActionRepository).insert(actionCaptor.capture());
        assertEquals(TicketActionType.CREATE, actionCaptor.getValue().getActionType());
    }

    @Test
    @DisplayName("建单：来源订单不属于该客户时拒绝")
    void shouldRejectOrderOfAnotherCustomer() {
        when(customerAppService.detail(eq(10L), any(CrmActor.class))).thenReturn(customerDetail());
        when(ticketRepository.countByPrefix(anyString())).thenReturn(0L);
        SalesOrder order = new SalesOrder();
        order.setId(7L);
        order.setCustomerId(11L);
        when(salesOrderRepository.findById(7L)).thenReturn(order);
        TicketCreateCommand command = createCommand();
        command.setOrderId(7L);

        BizException exception = assertThrows(BizException.class, () -> service.create(command, adminActor()));

        assertEquals(CrmErrorCode.PARAM_INVALID, exception.getCode());
        verify(ticketRepository, never()).insert(any(Ticket.class));
    }

    @Test
    @DisplayName("分页：非全量数据范围按负责人或处理人过滤")
    void shouldScopePageForRegularUser() {
        when(ticketRepository.page(any(TicketQuery.class))).thenReturn(List.of());
        when(ticketRepository.count(any(TicketQuery.class))).thenReturn(0L);
        TicketQuery query = new TicketQuery();

        PageResult<TicketView> result = service.page(query, regularActor());

        assertEquals(1L, query.getScopeUserId());
        assertEquals(0, result.getRecords().size());
    }

    @Test
    @DisplayName("详情：非本人负责且非本人处理的工单按不存在处理")
    void shouldHideTicketOutsideDataScope() {
        Ticket ticket = ticket(TicketStatus.NEW);
        ticket.setOwnerId(20L);
        when(ticketRepository.findById(1L)).thenReturn(ticket);

        BizException exception = assertThrows(BizException.class, () -> service.detail(1L, regularActor()));

        assertEquals(CrmErrorCode.TICKET_NOT_FOUND, exception.getCode());
        verify(ticketActionRepository, never()).findByTicketId(any());
    }

    @Test
    @DisplayName("分派：状态流转并追加分派记录")
    void shouldAssignAndRecordAction() {
        Ticket ticket = ticket(TicketStatus.NEW);
        when(ticketRepository.findById(1L)).thenReturn(ticket);
        TicketAssignCommand command = new TicketAssignCommand();
        command.setAssigneeId(1001L);
        command.setAssigneeName("张三");

        TicketView view = service.assign(1L, command, adminActor());

        assertEquals(TicketStatus.ASSIGNED.name(), view.getStatus());
        assertEquals(1001L, view.getAssigneeId());
        ArgumentCaptor<TicketAction> actionCaptor = ArgumentCaptor.forClass(TicketAction.class);
        verify(ticketActionRepository).insert(actionCaptor.capture());
        assertEquals(TicketActionType.ASSIGN, actionCaptor.getValue().getActionType());
    }

    @Test
    @DisplayName("升级：P3 升为 P2 并按新优先级重算截止时间")
    void shouldEscalateAndRecomputeDeadline() {
        Ticket ticket = ticket(TicketStatus.PROCESSING);
        ticket.setPriority(TicketPriority.P3);
        when(ticketRepository.findById(1L)).thenReturn(ticket);

        TicketView view = service.escalate(1L, new TicketHandleCommand(), adminActor());

        assertEquals(TicketPriority.P2.name(), view.getPriority());
        assertNotNull(view.getResponseDeadline());
        assertNotNull(view.getResolveDeadline());
    }

    @Test
    @DisplayName("恢复：暂停时长计入累计并顺延截止时间")
    void shouldResumeAndShiftDeadlines() {
        Ticket ticket = ticket(TicketStatus.WAITING_CUSTOMER);
        LocalDateTime now = LocalDateTime.now();
        ticket.setPausedAt(now.minusMinutes(30));
        LocalDateTime responseDeadline = now.plusHours(1);
        ticket.setResponseDeadline(responseDeadline);
        ticket.setResolveDeadline(now.plusHours(4));
        when(ticketRepository.findById(1L)).thenReturn(ticket);

        TicketView view = service.resume(1L, new TicketHandleCommand(), adminActor());

        assertEquals(TicketStatus.PROCESSING.name(), view.getStatus());
        assertTrue(view.getPausedSeconds() >= 1800L);
        assertTrue(view.getResponseDeadline().isAfter(responseDeadline));
    }

    @Test
    @DisplayName("门禁：需求门禁或售后服务开关关闭时拒绝访问")
    void shouldRejectWhenGateClosed() {
        properties.setDemandGatePassed(false);
        BizException gateClosed = assertThrows(BizException.class,
                () -> service.page(new TicketQuery(), adminActor()));
        assertEquals(CrmErrorCode.GATE_CLOSED, gateClosed.getCode());

        properties.setDemandGatePassed(true);
        serviceProperties.setEnabled(false);
        BizException disabled = assertThrows(BizException.class,
                () -> service.page(new TicketQuery(), adminActor()));
        assertEquals(CrmErrorCode.GATE_CLOSED, disabled.getCode());
    }

    /**
     * 构造管理员操作人。
     *
     * @return 操作人
     */
    private static CrmActor adminActor() {
        return new CrmActor(1L, "admin", Set.of("crm_admin"), Set.of());
    }

    /**
     * 构造普通操作人。
     *
     * @return 操作人
     */
    private static CrmActor regularActor() {
        return new CrmActor(1L, "sales01", Set.of("crm_sales"), Set.of());
    }

    /**
     * 构造客户详情。
     *
     * @return 客户详情
     */
    private static CustomerDetailView customerDetail() {
        CustomerView customer = new CustomerView();
        customer.setId(10L);
        customer.setCustomerCode("CUS-1");
        customer.setName("客户A");
        customer.setOwnerId(20L);
        customer.setDeptId(30L);
        ContactView contact = new ContactView();
        contact.setId(5L);
        contact.setName("李四");
        CustomerDetailView detail = new CustomerDetailView();
        detail.setCustomer(customer);
        detail.setContacts(List.of(contact));
        return detail;
    }

    /**
     * 构造建单命令。
     *
     * @return 命令
     */
    private static TicketCreateCommand createCommand() {
        TicketCreateCommand command = new TicketCreateCommand();
        command.setCustomerId(10L);
        command.setContactId(5L);
        command.setPriority("P1");
        command.setTitle(" 设备离线 ");
        return command;
    }

    /**
     * 构造工单。
     *
     * @param status 状态
     * @return 工单
     */
    private static Ticket ticket(TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setTicketNo("TICKET-20261106-0001");
        ticket.setCustomerId(10L);
        ticket.setCustomerName("客户A");
        ticket.setPriority(TicketPriority.P2);
        ticket.setTitle("设备离线");
        ticket.setStatus(status);
        ticket.setOwnerId(1L);
        ticket.setPausedSeconds(0L);
        ticket.setReopenCount(0);
        ticket.setLockVersion(0);
        return ticket;
    }
}
