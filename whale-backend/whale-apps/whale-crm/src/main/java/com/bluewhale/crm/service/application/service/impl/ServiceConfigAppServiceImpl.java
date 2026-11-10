package com.bluewhale.crm.service.application.service.impl;

import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.config.CrmServiceProperties;
import com.bluewhale.crm.service.application.command.ServiceCalendarCommand;
import com.bluewhale.crm.service.application.command.SlaPoliciesCommand;
import com.bluewhale.crm.service.application.command.SlaPolicyCommand;
import com.bluewhale.crm.service.application.service.ServiceConfigAppService;
import com.bluewhale.crm.service.application.view.ServiceCalendarView;
import com.bluewhale.crm.service.application.view.SlaPolicyView;
import com.bluewhale.crm.service.domain.model.ServiceCalendar;
import com.bluewhale.crm.service.domain.model.SlaPolicy;
import com.bluewhale.crm.service.domain.model.TicketPriority;
import com.bluewhale.crm.service.domain.repository.ServiceCalendarRepository;
import com.bluewhale.crm.service.domain.repository.SlaPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 售后服务配置应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ServiceConfigAppServiceImpl implements ServiceConfigAppService {

    /** 时间格式（兼容 9:00 与 09:00） */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

    private final SlaPolicyRepository slaPolicyRepository;

    private final ServiceCalendarRepository serviceCalendarRepository;

    private final CrmProperties properties;

    private final CrmServiceProperties serviceProperties;

    @Override
    public List<SlaPolicyView> listPolicies(CrmActor actor) {
        assertGate();
        Map<TicketPriority, SlaPolicy> stored = new EnumMap<>(TicketPriority.class);
        for (SlaPolicy policy : slaPolicyRepository.findAll()) {
            stored.put(policy.getPriority(), policy);
        }
        List<SlaPolicyView> views = new ArrayList<>();
        for (TicketPriority priority : TicketPriority.values()) {
            SlaPolicy policy = stored.getOrDefault(priority, defaultPolicy(priority));
            views.add(SlaPolicyView.from(policy));
        }
        return views;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SlaPolicyView> savePolicies(SlaPoliciesCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        Map<TicketPriority, SlaPolicyCommand> byPriority = new EnumMap<>(TicketPriority.class);
        for (SlaPolicyCommand item : command.getPolicies()) {
            TicketPriority priority = TicketPriority.from(item.getPriority());
            if (byPriority.put(priority, item) != null) {
                throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "SLA 策略优先级重复：" + priority.name());
            }
        }
        for (TicketPriority priority : TicketPriority.values()) {
            SlaPolicyCommand item = byPriority.get(priority);
            if (item == null) {
                continue;
            }
            if (item.getResolveHours() < item.getResponseHours()) {
                throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID,
                        "解决时限不能小于响应时限：" + priority.name());
            }
            SlaPolicy policy = new SlaPolicy();
            policy.setPriority(priority);
            policy.setResponseHours(item.getResponseHours());
            policy.setResolveHours(item.getResolveHours());
            policy.setEnabled(item.getEnabled() == null || item.getEnabled());
            slaPolicyRepository.upsert(policy);
        }
        return listPolicies(actor);
    }

    @Override
    public ServiceCalendarView getCalendar(CrmActor actor) {
        assertGate();
        ServiceCalendar calendar = serviceCalendarRepository.findEnabled(serviceProperties.getDefaultCalendarCode());
        return ServiceCalendarView.from(calendar == null ? defaultCalendar() : calendar);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceCalendarView saveCalendar(ServiceCalendarCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        LocalTime start = parseTime(command.getWorkdayStart(), "上班时间");
        LocalTime end = parseTime(command.getWorkdayEnd(), "下班时间");
        if (end.isBefore(start)) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "下班时间必须晚于上班时间");
        }
        long total = Duration.between(start, end).toMinutes();
        if (total - command.getBreakMinutes() <= 0) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "午休时长必须小于工作时段");
        }
        ServiceCalendar calendar = new ServiceCalendar();
        calendar.setCalendarCode(command.getCalendarCode() == null || command.getCalendarCode().isBlank()
                ? serviceProperties.getDefaultCalendarCode() : command.getCalendarCode().trim());
        calendar.setWorkdayStart(start);
        calendar.setWorkdayEnd(end);
        calendar.setBreakMinutes(command.getBreakMinutes());
        calendar.setWeekdays(ServiceCalendar.normalizeWeekdays(command.getWeekdays()));
        calendar.setEnabled(command.getEnabled() == null || command.getEnabled());
        serviceCalendarRepository.upsert(calendar);
        return ServiceCalendarView.from(calendar);
    }

    /**
     * 解析时间。
     *
     * @param value 文本
     * @param label 字段名
     * @return 时间
     */
    private LocalTime parseTime(String value, String label) {
        try {
            return LocalTime.parse(value.trim(), TIME_FORMAT);
        } catch (RuntimeException ex) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, label + "格式必须为 HH:mm");
        }
    }

    /**
     * 构造默认 SLA 策略。
     *
     * @param priority 优先级
     * @return 策略
     */
    private SlaPolicy defaultPolicy(TicketPriority priority) {
        SlaPolicy policy = new SlaPolicy();
        policy.setPriority(priority);
        policy.setEnabled(true);
        switch (priority) {
            case P1 -> {
                policy.setResponseHours(serviceProperties.getP1ResponseHours());
                policy.setResolveHours(serviceProperties.getP1ResolveHours());
            }
            case P2 -> {
                policy.setResponseHours(serviceProperties.getP2ResponseHours());
                policy.setResolveHours(serviceProperties.getP2ResolveHours());
            }
            default -> {
                policy.setResponseHours(serviceProperties.getP3ResponseHours());
                policy.setResolveHours(serviceProperties.getP3ResolveHours());
            }
        }
        return policy;
    }

    /**
     * 构造默认工作日历。
     *
     * @return 日历
     */
    private ServiceCalendar defaultCalendar() {
        ServiceCalendar calendar = new ServiceCalendar();
        calendar.setCalendarCode(serviceProperties.getDefaultCalendarCode());
        calendar.setWorkdayStart(parseTime(serviceProperties.getWorkdayStart(), "上班时间"));
        calendar.setWorkdayEnd(parseTime(serviceProperties.getWorkdayEnd(), "下班时间"));
        calendar.setBreakMinutes(serviceProperties.getBreakMinutes());
        calendar.setWeekdays(ServiceCalendar.normalizeWeekdays(serviceProperties.getWeekdays()));
        calendar.setEnabled(true);
        return calendar;
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
