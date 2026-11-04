package com.bluewhale.oa.approval.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.application.command.ApprovalInstanceRegisterCommand;
import com.bluewhale.oa.approval.application.command.ApprovalResultCommand;
import com.bluewhale.oa.approval.application.command.ApprovalRouteCommand;
import com.bluewhale.oa.approval.application.service.ApprovalConfigAppService;
import com.bluewhale.oa.approval.application.service.ApprovalInstanceAppService;
import com.bluewhale.oa.approval.application.view.ApprovalConfigView;
import com.bluewhale.oa.approval.application.view.ApprovalEffectView;
import com.bluewhale.oa.approval.application.view.ApprovalInstanceView;
import com.bluewhale.oa.approval.application.view.ApprovalRouteView;
import com.bluewhale.oa.approval.domain.model.ApprovalEffect;
import com.bluewhale.oa.approval.domain.model.ApprovalInstance;
import com.bluewhale.oa.approval.domain.model.ApprovalInstanceQuery;
import com.bluewhale.oa.approval.domain.model.ApprovalRoute;
import com.bluewhale.oa.approval.domain.model.ApprovalStatus;
import com.bluewhale.oa.approval.domain.repository.ApprovalEffectRepository;
import com.bluewhale.oa.approval.domain.repository.ApprovalInstanceRepository;
import com.bluewhale.oa.approval.domain.repository.ApprovalRouteRepository;
import com.bluewhale.oa.common.OaErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 审批实例应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ApprovalInstanceAppServiceImpl implements ApprovalInstanceAppService {

    /** 标题模板占位符：申请人 */
    private static final String PLACEHOLDER_APPLICANT = "{applicant}";

    /** 标题模板占位符：业务单号 */
    private static final String PLACEHOLDER_CODE = "{businessCode}";

    /** 标题模板占位符：业务名称 */
    private static final String PLACEHOLDER_NAME = "{businessName}";

    /** 账号快照分隔符 */
    private static final String ASSIGNEE_SEPARATOR = ",";

    private final ApprovalInstanceRepository approvalInstanceRepository;

    private final ApprovalRouteRepository approvalRouteRepository;

    private final ApprovalEffectRepository approvalEffectRepository;

    private final ApprovalConfigAppService approvalConfigAppService;

    @Override
    public PageResult<ApprovalInstanceView> page(ApprovalInstanceQuery query) {
        List<ApprovalInstanceView> records = approvalInstanceRepository.page(query)
                .stream().map(this::toView).toList();
        return PageResult.of(records, approvalInstanceRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public ApprovalInstanceView detail(Long id) {
        ApprovalInstance instance = requireInstance(id);
        return toDetailView(instance);
    }

    @Override
    public ApprovalInstanceView findByBusiness(String businessType, Long businessId) {
        ApprovalInstance instance = approvalInstanceRepository.findByBusiness(businessType, businessId)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.APPROVAL_INSTANCE_NOT_FOUND));
        return toDetailView(instance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalInstanceView register(ApprovalInstanceRegisterCommand command) {
        Optional<ApprovalInstance> existing = approvalInstanceRepository
                .findByBusiness(command.getBusinessType(), command.getBusinessId());
        if (existing.isPresent()) {
            return toDetailView(existing.get());
        }
        ApprovalConfigView config = approvalConfigAppService.requireEnabled(command.getBusinessType());
        List<ApprovalRoute> routes = buildRoutes(command.getRoutes());
        ApprovalInstance instance = new ApprovalInstance();
        instance.setBusinessType(command.getBusinessType());
        instance.setBusinessId(command.getBusinessId());
        instance.setBusinessCode(command.getBusinessCode());
        instance.setDefinitionCode(resolveDefinitionCode(command, config));
        instance.setTitle(resolveTitle(command, config));
        instance.setInitiatorId(command.getInitiatorId());
        instance.setInitiatorName(command.getInitiatorName());
        instance.setStatus(ApprovalStatus.RUNNING);
        instance.setSubmittedAt(LocalDateTime.now());
        instance.setBusinessUrl(resolveBusinessUrl(command, config));
        instance.setRemark(command.getRemark());
        applyFirstNode(instance, routes);
        if (!approvalInstanceRepository.insertIfAbsent(instance)) {
            return toDetailView(approvalInstanceRepository
                    .findByBusiness(command.getBusinessType(), command.getBusinessId())
                    .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.APPROVAL_INSTANCE_NOT_FOUND)));
        }
        if (!routes.isEmpty()) {
            routes.forEach(route -> route.setApprovalInstanceId(instance.getId()));
            approvalRouteRepository.insertAll(routes);
        }
        return toDetailView(instance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalInstanceView attachWorkflow(Long id, Long workflowInstanceId) {
        ApprovalInstance instance = requireInstance(id);
        if (instance.attachWorkflow(workflowInstanceId)) {
            approvalInstanceRepository.update(instance);
        }
        return toDetailView(instance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalInstanceView syncResult(Long id, ApprovalResultCommand command) {
        ApprovalInstance instance = requireInstance(id);
        ApprovalStatus target = ApprovalStatus.of(command.getStatus());
        if (instance.markResult(target)) {
            approvalInstanceRepository.update(instance);
        }
        return toDetailView(instance);
    }

    /**
     * 按规则生成审批人快照（过滤空账号，忽略空列表）。
     *
     * @param commands 审批人命令
     * @return 审批人快照
     */
    private List<ApprovalRoute> buildRoutes(List<ApprovalRouteCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        List<ApprovalRoute> routes = new ArrayList<>(commands.size());
        int index = 0;
        for (ApprovalRouteCommand command : commands) {
            index++;
            if (!StringUtils.hasText(command.getAssigneeAccount())) {
                continue;
            }
            ApprovalRoute route = new ApprovalRoute();
            route.setNodeKey(command.getNodeKey());
            route.setNodeName(command.getNodeName());
            route.setAssigneeId(command.getAssigneeId());
            route.setAssigneeAccount(command.getAssigneeAccount());
            route.setResolverType(command.getResolverType());
            route.setSortNo(command.getSortNo() == null ? index : command.getSortNo());
            routes.add(route);
        }
        return routes;
    }

    /**
     * 用首个节点初始化当前节点快照。
     *
     * @param instance 审批实例
     * @param routes 审批人快照
     */
    private void applyFirstNode(ApprovalInstance instance, List<ApprovalRoute> routes) {
        if (routes.isEmpty()) {
            return;
        }
        ApprovalRoute first = routes.getFirst();
        instance.setCurrentNodeKey(first.getNodeKey());
        instance.setCurrentNodeName(first.getNodeName());
        String assignees = routes.stream()
                .filter(route -> first.getNodeKey().equals(route.getNodeKey()))
                .map(ApprovalRoute::getAssigneeAccount)
                .collect(Collectors.joining(ASSIGNEE_SEPARATOR));
        instance.setCurrentAssignees(assignees);
    }

    /**
     * 解析流程定义编码：命令优先，缺省回退审批配置。
     *
     * @param command 登记命令
     * @param config 审批配置
     * @return 流程定义编码
     */
    private String resolveDefinitionCode(ApprovalInstanceRegisterCommand command, ApprovalConfigView config) {
        return StringUtils.hasText(command.getDefinitionCode())
                ? command.getDefinitionCode() : config.getDefinitionCode();
    }

    /**
     * 解析业务详情路由：命令优先，缺省回退审批配置。
     *
     * @param command 登记命令
     * @param config 审批配置
     * @return 业务详情路由
     */
    private String resolveBusinessUrl(ApprovalInstanceRegisterCommand command, ApprovalConfigView config) {
        return StringUtils.hasText(command.getBusinessUrl())
                ? command.getBusinessUrl() : config.getBusinessUrl();
    }

    /**
     * 解析审批标题：命令 &gt; 配置模板 &gt; 业务名称 + 单号。
     *
     * @param command 登记命令
     * @param config 审批配置
     * @return 审批标题
     */
    private String resolveTitle(ApprovalInstanceRegisterCommand command, ApprovalConfigView config) {
        if (StringUtils.hasText(command.getTitle())) {
            return command.getTitle();
        }
        if (StringUtils.hasText(config.getTitleTemplate())) {
            return config.getTitleTemplate()
                    .replace(PLACEHOLDER_APPLICANT, nullToEmpty(command.getInitiatorName()))
                    .replace(PLACEHOLDER_CODE, nullToEmpty(command.getBusinessCode()))
                    .replace(PLACEHOLDER_NAME, nullToEmpty(config.getBusinessName()));
        }
        String suffix = StringUtils.hasText(command.getBusinessCode()) ? " " + command.getBusinessCode() : "";
        return config.getBusinessName() + suffix;
    }

    /**
     * 空值转空串。
     *
     * @param value 原值
     * @return 非空字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 查询审批实例，缺失时抛业务异常。
     *
     * @param id 主键
     * @return 审批实例
     */
    private ApprovalInstance requireInstance(Long id) {
        return approvalInstanceRepository.findById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.APPROVAL_INSTANCE_NOT_FOUND));
    }

    /**
     * 领域模型转列表视图。
     *
     * @param instance 审批实例
     * @return 审批实例视图
     */
    private ApprovalInstanceView toView(ApprovalInstance instance) {
        ApprovalInstanceView view = new ApprovalInstanceView();
        view.setId(instance.getId());
        view.setBusinessType(instance.getBusinessType());
        view.setBusinessId(instance.getBusinessId());
        view.setBusinessCode(instance.getBusinessCode());
        view.setDefinitionCode(instance.getDefinitionCode());
        view.setWorkflowInstanceId(instance.getWorkflowInstanceId());
        view.setTitle(instance.getTitle());
        view.setInitiatorId(instance.getInitiatorId());
        view.setInitiatorName(instance.getInitiatorName());
        view.setStatus(instance.getStatus() == null ? null : instance.getStatus().name());
        view.setCurrentNodeKey(instance.getCurrentNodeKey());
        view.setCurrentNodeName(instance.getCurrentNodeName());
        view.setCurrentAssignees(instance.getCurrentAssignees());
        view.setSubmittedAt(instance.getSubmittedAt());
        view.setFinishedAt(instance.getFinishedAt());
        view.setBusinessUrl(instance.getBusinessUrl());
        view.setRemark(instance.getRemark());
        view.setCreateTime(instance.getCreateTime());
        view.setUpdateTime(instance.getUpdateTime());
        return view;
    }

    /**
     * 领域模型转详情视图（含快照与副作用）。
     *
     * @param instance 审批实例
     * @return 审批实例视图
     */
    private ApprovalInstanceView toDetailView(ApprovalInstance instance) {
        ApprovalInstanceView view = toView(instance);
        view.setRoutes(approvalRouteRepository.listByInstance(instance.getId())
                .stream().map(this::toRouteView).toList());
        view.setEffects(approvalEffectRepository.listByInstance(instance.getId())
                .stream().map(this::toEffectView).toList());
        return view;
    }

    /**
     * 审批人快照转视图。
     *
     * @param route 审批人快照
     * @return 审批人视图
     */
    private ApprovalRouteView toRouteView(ApprovalRoute route) {
        ApprovalRouteView view = new ApprovalRouteView();
        view.setNodeKey(route.getNodeKey());
        view.setNodeName(route.getNodeName());
        view.setAssigneeId(route.getAssigneeId());
        view.setAssigneeAccount(route.getAssigneeAccount());
        view.setResolverType(route.getResolverType());
        view.setSortNo(route.getSortNo());
        return view;
    }

    /**
     * 业务副作用转视图。
     *
     * @param effect 业务副作用
     * @return 副作用视图
     */
    private ApprovalEffectView toEffectView(ApprovalEffect effect) {
        ApprovalEffectView view = new ApprovalEffectView();
        view.setEffectType(effect.getEffectType());
        view.setEffectStatus(effect.getEffectStatus() == null ? null : effect.getEffectStatus().name());
        view.setExecutedAt(effect.getExecutedAt());
        view.setRetryCount(effect.getRetryCount());
        view.setErrorMsg(effect.getErrorMsg());
        return view;
    }
}
