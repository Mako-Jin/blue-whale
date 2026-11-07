package com.bluewhale.crm.marketing.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmMarketingProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.CustomerDetailView;
import com.bluewhale.crm.marketing.application.command.NurtureCreateCommand;
import com.bluewhale.crm.marketing.application.command.NurtureStatusCommand;
import com.bluewhale.crm.marketing.application.command.NurtureUpdateCommand;
import com.bluewhale.crm.marketing.application.service.NurtureAppService;
import com.bluewhale.crm.marketing.application.view.NurtureTaskView;
import com.bluewhale.crm.marketing.domain.model.NurtureQuery;
import com.bluewhale.crm.marketing.domain.model.NurtureStatus;
import com.bluewhale.crm.marketing.domain.model.NurtureTask;
import com.bluewhale.crm.marketing.domain.repository.NurtureTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 客户培育任务应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class NurtureAppServiceImpl implements NurtureAppService {

    /** 任务编号前缀 */
    private static final String CODE_PREFIX = "NURTURE-";

    /** 日期格式 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final NurtureTaskRepository nurtureTaskRepository;

    private final CustomerAppService customerAppService;

    private final CrmProperties crmProperties;

    private final CrmMarketingProperties marketingProperties;

    @Override
    public PageResult<NurtureTaskView> page(NurtureQuery query, CrmActor actor) {
        ensureGate();
        if (!actor.allDataScope()) {
            query.setScopeUserId(actor.requireUserId());
        }
        List<NurtureTaskView> records = nurtureTaskRepository.page(query).stream().map(NurtureTaskView::from).toList();
        long total = nurtureTaskRepository.count(query);
        return PageResult.of(records, total, query.getCurrent(), query.getSize());
    }

    @Override
    public NurtureTaskView detail(Long id, CrmActor actor) {
        ensureGate();
        return NurtureTaskView.from(loadTask(id, actor));
    }

    @Override
    @Transactional
    public NurtureTaskView create(NurtureCreateCommand command, CrmActor actor) {
        ensureGate();
        Long userId = actor.requireUserId();
        CustomerDetailView detail = customerAppService.detail(command.getCustomerId(), actor);
        NurtureTask task = new NurtureTask();
        task.setTaskNo(nextCode());
        task.setCustomerId(command.getCustomerId());
        task.setCustomerName(detail.getCustomer() == null ? null : detail.getCustomer().getName());
        task.setLeadId(command.getLeadId());
        task.setContentDocId(command.getContentDocId());
        task.setContentDocVersion(command.getContentDocVersion());
        task.setChannel(command.getChannel());
        task.setPlanDate(command.getPlanDate());
        task.setStatus(NurtureStatus.OPEN);
        task.setAssigneeId(command.getAssigneeId());
        task.setAssigneeName(command.getAssigneeName());
        task.setNote(command.getNote());
        task.setOwnerId(command.getOwnerId() == null ? userId : command.getOwnerId());
        task.setDeptId(command.getDeptId());
        task.setLockVersion(0);
        nurtureTaskRepository.insert(task);
        return NurtureTaskView.from(task);
    }

    @Override
    @Transactional
    public NurtureTaskView update(Long id, NurtureUpdateCommand command, CrmActor actor) {
        ensureGate();
        NurtureTask task = loadTask(id, actor);
        if (task.getStatus() != NurtureStatus.OPEN) {
            throw CrmErrorCode.exception(CrmErrorCode.NURTURE_STATE_CONFLICT, "培育任务已结束，不允许编辑");
        }
        task.setLeadId(command.getLeadId());
        task.setContentDocId(command.getContentDocId());
        task.setContentDocVersion(command.getContentDocVersion());
        task.setChannel(command.getChannel());
        task.setPlanDate(command.getPlanDate());
        task.setAssigneeId(command.getAssigneeId());
        task.setAssigneeName(command.getAssigneeName());
        task.setNote(command.getNote());
        if (command.getOwnerId() != null) {
            task.setOwnerId(command.getOwnerId());
        }
        if (command.getDeptId() != null) {
            task.setDeptId(command.getDeptId());
        }
        nurtureTaskRepository.update(task);
        return NurtureTaskView.from(task);
    }

    @Override
    @Transactional
    public NurtureTaskView changeStatus(Long id, NurtureStatusCommand command, CrmActor actor) {
        ensureGate();
        NurtureTask task = loadTask(id, actor);
        task.changeStatus(NurtureStatus.from(command.getStatus()), LocalDateTime.now());
        nurtureTaskRepository.update(task);
        return NurtureTaskView.from(task);
    }

    /**
     * 校验需求门禁与营销开关。
     */
    private void ensureGate() {
        if (!crmProperties.isDemandGatePassed() || !marketingProperties.isEnabled()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 加载培育任务并校验数据范围。
     *
     * @param id 任务 ID
     * @param actor 当前操作人
     * @return 任务
     */
    private NurtureTask loadTask(Long id, CrmActor actor) {
        NurtureTask task = id == null ? null : nurtureTaskRepository.findById(id);
        if (task == null) {
            throw CrmErrorCode.exception(CrmErrorCode.NURTURE_NOT_FOUND);
        }
        if (!actor.allDataScope() && !Objects.equals(task.getOwnerId(), actor.requireUserId())) {
            throw CrmErrorCode.exception(CrmErrorCode.NURTURE_NOT_FOUND);
        }
        return task;
    }

    /**
     * 生成任务编号。
     *
     * @return 任务编号
     */
    private String nextCode() {
        String prefix = CODE_PREFIX + LocalDate.now().format(DATE_FORMAT) + "-";
        return prefix + String.format("%04d", nurtureTaskRepository.countByTaskNoPrefix(prefix) + 1);
    }
}
