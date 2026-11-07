package com.bluewhale.crm.service.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
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
import com.bluewhale.crm.service.application.service.TicketAppService;
import com.bluewhale.crm.service.application.view.TicketView;
import com.bluewhale.crm.service.domain.model.ServiceCalendar;
import com.bluewhale.crm.service.domain.model.SlaPolicy;
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
import com.bluewhale.crm.service.domain.service.SlaCalculator;
import com.bluewhale.crm.commerce.domain.model.SalesOrder;
import com.bluewhale.crm.commerce.domain.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 工单应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class TicketAppServiceImpl implements TicketAppService {

    /** 工单号前缀 */
    private static final String TICKET_NO_PREFIX = "TICKET-";

    /** 工单号日期格式 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TicketRepository ticketRepository;

    private final TicketActionRepository ticketActionRepository;

    private final SlaPolicyRepository slaPolicyRepository;

    private final ServiceCalendarRepository serviceCalendarRepository;

    private final CustomerAppService customerAppService;

    private final SalesOrderRepository salesOrderRepository;

    private final CrmProperties properties;

    private final CrmServiceProperties serviceProperties;

    private final SatisfactionAppService satisfactionAppService;

    @Override
    public PageResult<TicketView> page(TicketQuery query, CrmActor actor) {
        assertGate();
        if (!actor.allDataScope()) {
            query.setScopeUserId(actor.requireUserId());
        }
        List<Ticket> tickets = ticketRepository.page(query);
        long total = ticketRepository.count(query);
        LocalDateTime now = LocalDateTime.now();
        return PageResult.of(tickets.stream().map(ticket -> TicketView.from(ticket, null, now)).toList(),
                total, query.getCurrent(), query.getSize());
    }

    @Override
    public TicketView detail(Long id, CrmActor actor) {
        assertGate();
        Ticket ticket = requireTicket(id, actor);
        List<TicketAction> actions = ticketActionRepository.findByTicketId(id);
        return TicketView.from(ticket, actions, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketView create(TicketCreateCommand command, CrmActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        CustomerDetailView detail = customerAppService.detail(command.getCustomerId(), actor);
        CustomerView customer = detail.getCustomer();
        LocalDateTime now = LocalDateTime.now();
        Ticket ticket = new Ticket();
        ticket.setTicketNo(nextTicketNo());
        ticket.setCustomerId(customer.getId());
        ticket.setCustomerCode(customer.getCustomerCode());
        ticket.setCustomerName(customer.getName());
        ticket.setContactId(command.getContactId());
        ticket.setContactName(resolveContactName(detail, command));
        ticket.setContractId(command.getContractId());
        ticket.setOrderId(command.getOrderId());
        ticket.setProductCode(command.getProductCode());
        ticket.setSerialNo(command.getSerialNo());
        ticket.setCategory(command.getCategory() == null ? null : command.getCategory().trim());
        ticket.setPriority(TicketPriority.from(command.getPriority()));
        ticket.setTitle(command.getTitle().trim());
        ticket.setDescription(command.getDescription());
        ticket.setStatus(TicketStatus.NEW);
        ticket.setOwnerId(command.getOwnerId() != null ? command.getOwnerId() : customer.getOwnerId());
        if (ticket.getOwnerId() == null) {
            ticket.setOwnerId(userId);
        }
        ticket.setDeptId(command.getDeptId() != null ? command.getDeptId() : customer.getDeptId());
        ticket.setPausedSeconds(0L);
        ticket.setReopenCount(0);
        ticket.setLockVersion(0);
        applyOrderSnapshot(ticket);
        applyDeadlines(ticket, now);
        if (command.getAssigneeId() != null) {
            ticket.assign(command.getAssigneeId(), command.getAssigneeName(), now);
        }
        ticketRepository.insert(ticket);
        recordAction(ticket, TicketActionType.CREATE, null, ticket.getStatus(), actor, "新建工单");
        return TicketView.from(ticket, null, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketView assign(Long id, TicketAssignCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        Ticket ticket = requireTicket(id, actor);
        TicketStatus from = ticket.getStatus();
        ticket.assign(command.getAssigneeId(), command.getAssigneeName(), LocalDateTime.now());
        ticketRepository.update(ticket);
        recordAction(ticket, TicketActionType.ASSIGN, from, ticket.getStatus(), actor,
                "分派给 " + (ticket.getAssigneeName() == null ? ticket.getAssigneeId() : ticket.getAssigneeName()));
        return TicketView.from(ticket, null, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketView process(Long id, TicketHandleCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        Ticket ticket = requireTicket(id, actor);
        TicketStatus from = ticket.getStatus();
        ticket.process(command.getContent(), LocalDateTime.now());
        ticketRepository.update(ticket);
        recordAction(ticket, TicketActionType.PROCESS, from, ticket.getStatus(), actor, command.getContent());
        return TicketView.from(ticket, null, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketView comment(Long id, TicketHandleCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        Ticket ticket = requireTicket(id, actor);
        TicketStatus from = ticket.getStatus();
        ticket.comment(command.getContent(), LocalDateTime.now());
        ticketRepository.update(ticket);
        recordAction(ticket, TicketActionType.COMMENT, from, ticket.getStatus(), actor, command.getContent());
        return TicketView.from(ticket, null, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketView pause(Long id, TicketHandleCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        Ticket ticket = requireTicket(id, actor);
        TicketStatus from = ticket.getStatus();
        String reason = command.getReason() == null ? command.getContent() : command.getReason();
        ticket.pause(reason, LocalDateTime.now());
        ticketRepository.update(ticket);
        recordAction(ticket, TicketActionType.PAUSE, from, ticket.getStatus(), actor, reason);
        return TicketView.from(ticket, null, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketView resume(Long id, TicketHandleCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        Ticket ticket = requireTicket(id, actor);
        TicketStatus from = ticket.getStatus();
        long paused = ticket.resume(LocalDateTime.now());
        ticket.shiftDeadlines(paused);
        ticketRepository.update(ticket);
        String content = command.getContent() == null ? "恢复工单" : command.getContent();
        recordAction(ticket, TicketActionType.RESUME, from, ticket.getStatus(), actor, content);
        return TicketView.from(ticket, null, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketView escalate(Long id, TicketHandleCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        Ticket ticket = requireTicket(id, actor);
        TicketStatus fromStatus = ticket.getStatus();
        TicketPriority before = ticket.getPriority();
        TicketPriority after = ticket.escalate();
        applyDeadlines(ticket, LocalDateTime.now());
        ticketRepository.update(ticket);
        String content = command.getContent() == null
                ? "优先级 " + before.name() + " → " + after.name()
                : command.getContent();
        recordAction(ticket, TicketActionType.ESCALATE, fromStatus, ticket.getStatus(), actor, content);
        return TicketView.from(ticket, null, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketView resolve(Long id, TicketHandleCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        Ticket ticket = requireTicket(id, actor);
        TicketStatus from = ticket.getStatus();
        ticket.resolve(command.getSolution(), LocalDateTime.now());
        ticketRepository.update(ticket);
        recordAction(ticket, TicketActionType.RESOLVE, from, ticket.getStatus(), actor, ticket.getSolution());
        return TicketView.from(ticket, null, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketView close(Long id, TicketHandleCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        Ticket ticket = requireTicket(id, actor);
        TicketStatus from = ticket.getStatus();
        ticket.close(command.getSolution(), LocalDateTime.now());
        ticketRepository.update(ticket);
        recordAction(ticket, TicketActionType.CLOSE, from, ticket.getStatus(), actor, ticket.getSolution());
        satisfactionAppService.autoSend(ticket.getId());
        return TicketView.from(ticket, null, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketView reopen(Long id, TicketHandleCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        Ticket ticket = requireTicket(id, actor);
        TicketStatus from = ticket.getStatus();
        ticket.reopen(command.getReason(), LocalDateTime.now());
        ticketRepository.update(ticket);
        recordAction(ticket, TicketActionType.REOPEN, from, ticket.getStatus(), actor, command.getReason());
        return TicketView.from(ticket, null, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketView cancel(Long id, TicketHandleCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        Ticket ticket = requireTicket(id, actor);
        TicketStatus from = ticket.getStatus();
        String reason = command.getReason() == null ? command.getContent() : command.getReason();
        ticket.cancel(reason, LocalDateTime.now());
        ticketRepository.update(ticket);
        recordAction(ticket, TicketActionType.CANCEL, from, ticket.getStatus(), actor, reason);
        return TicketView.from(ticket, null, LocalDateTime.now());
    }

    /**
     * 追加工单处理记录。
     *
     * @param ticket 工单
     * @param type 动作类型
     * @param from 变更前状态
     * @param to 变更后状态
     * @param actor 操作人
     * @param content 内容
     */
    private void recordAction(Ticket ticket, TicketActionType type, TicketStatus from, TicketStatus to,
                              CrmActor actor, String content) {
        TicketAction action = new TicketAction();
        action.setTicketId(ticket.getId());
        action.setActionType(type);
        action.setFromStatus(from);
        action.setToStatus(to);
        action.setOperatorId(actor.getUserId());
        action.setOperatorName(actor.getUsername());
        action.setContent(content);
        action.setCreatedAt(LocalDateTime.now());
        ticketActionRepository.insert(action);
    }

    /**
     * 计算并写入 SLA 截止时间。
     *
     * @param ticket 工单
     * @param from 起算时间
     */
    private void applyDeadlines(Ticket ticket, LocalDateTime from) {
        SlaPolicy policy = resolvePolicy(ticket.getPriority());
        if (!Boolean.TRUE.equals(policy.getEnabled())) {
            ticket.applyDeadlines(null, null);
            return;
        }
        ServiceCalendar calendar = resolveCalendar();
        ticket.applyDeadlines(
                SlaCalculator.deadline(from, policy.getResponseHours(), calendar),
                SlaCalculator.deadline(from, policy.getResolveHours(), calendar));
    }

    /**
     * 解析优先级策略，缺省用 D7 默认值。
     *
     * @param priority 优先级
     * @return 策略
     */
    private SlaPolicy resolvePolicy(TicketPriority priority) {
        SlaPolicy policy = priority == null ? null : slaPolicyRepository.findByPriority(priority.name());
        if (policy != null) {
            return policy;
        }
        SlaPolicy fallback = new SlaPolicy();
        fallback.setPriority(priority == null ? TicketPriority.P3 : priority);
        fallback.setEnabled(true);
        switch (fallback.getPriority()) {
            case P1 -> {
                fallback.setResponseHours(serviceProperties.getP1ResponseHours());
                fallback.setResolveHours(serviceProperties.getP1ResolveHours());
            }
            case P2 -> {
                fallback.setResponseHours(serviceProperties.getP2ResponseHours());
                fallback.setResolveHours(serviceProperties.getP2ResolveHours());
            }
            default -> {
                fallback.setResponseHours(serviceProperties.getP3ResponseHours());
                fallback.setResolveHours(serviceProperties.getP3ResolveHours());
            }
        }
        return fallback;
    }

    /**
     * 解析工作日历，缺省用配置默认值。
     *
     * @return 日历
     */
    private ServiceCalendar resolveCalendar() {
        ServiceCalendar calendar = serviceCalendarRepository.findEnabled(serviceProperties.getDefaultCalendarCode());
        if (calendar != null) {
            return calendar;
        }
        ServiceCalendar fallback = new ServiceCalendar();
        fallback.setCalendarCode(serviceProperties.getDefaultCalendarCode());
        fallback.setWorkdayStart(LocalTime.parse(serviceProperties.getWorkdayStart()));
        fallback.setWorkdayEnd(LocalTime.parse(serviceProperties.getWorkdayEnd()));
        fallback.setBreakMinutes(serviceProperties.getBreakMinutes());
        fallback.setWeekdays(serviceProperties.getWeekdays());
        fallback.setEnabled(true);
        return fallback;
    }

    /**
     * 解析联系人姓名（命令未传时从客户联系人中查找）。
     *
     * @param detail 客户详情
     * @param command 建单命令
     * @return 联系人姓名
     */
    private String resolveContactName(CustomerDetailView detail, TicketCreateCommand command) {
        if (command.getContactName() != null) {
            return command.getContactName();
        }
        if (command.getContactId() == null || detail.getContacts() == null) {
            return null;
        }
        for (ContactView contact : detail.getContacts()) {
            if (Objects.equals(contact.getId(), command.getContactId())) {
                return contact.getName();
            }
        }
        return null;
    }

    /**
     * 按商务订单补全 ERP 订单号快照。
     *
     * @param ticket 工单
     */
    private void applyOrderSnapshot(Ticket ticket) {
        if (ticket.getOrderId() == null) {
            return;
        }
        SalesOrder order = salesOrderRepository.findById(ticket.getOrderId());
        if (order == null) {
            throw CrmErrorCode.exception(CrmErrorCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getCustomerId(), ticket.getCustomerId())) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "工单来源订单不属于该客户");
        }
        ticket.setErpOrderNo(order.getErpOrderNo());
    }

    /**
     * 生成工单号。
     *
     * @return 工单号
     */
    private String nextTicketNo() {
        String prefix = TICKET_NO_PREFIX + LocalDate.now().format(DATE_FORMAT) + "-";
        long sequence = ticketRepository.countByPrefix(prefix) + 1;
        return prefix + String.format("%04d", sequence);
    }

    /**
     * 查询并校验工单数据范围。
     *
     * @param id 工单 ID
     * @param actor 当前操作人
     * @return 工单
     */
    private Ticket requireTicket(Long id, CrmActor actor) {
        Ticket ticket = ticketRepository.findById(id);
        if (ticket == null) {
            throw CrmErrorCode.exception(CrmErrorCode.TICKET_NOT_FOUND);
        }
        if (!actor.allDataScope()) {
            Long userId = actor.requireUserId();
            if (!Objects.equals(ticket.getOwnerId(), userId) && !Objects.equals(ticket.getAssigneeId(), userId)) {
                throw CrmErrorCode.exception(CrmErrorCode.TICKET_NOT_FOUND);
            }
        }
        return ticket;
    }

    /**
     * 校验需求门禁与售后服务开关。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
        if (!serviceProperties.isEnabled()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED, "售后服务模块未启用");
        }
    }
}
