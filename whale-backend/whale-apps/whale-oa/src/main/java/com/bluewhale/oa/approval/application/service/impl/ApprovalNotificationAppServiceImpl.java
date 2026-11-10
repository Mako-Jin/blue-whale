package com.bluewhale.oa.approval.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.approval.application.service.ApprovalNotificationAppService;
import com.bluewhale.oa.approval.domain.gateway.ApprovalNotifyGateway;
import com.bluewhale.oa.approval.domain.gateway.UserDirectoryGateway;
import com.bluewhale.oa.approval.domain.gateway.WorkflowApprovalGateway;
import com.bluewhale.oa.approval.domain.model.ApprovalInstance;
import com.bluewhale.oa.approval.domain.model.ApprovalNotifyMessage;
import com.bluewhale.oa.approval.domain.model.WorkflowCcQuery;
import com.bluewhale.oa.approval.domain.model.WorkflowCcSnapshot;
import com.bluewhale.oa.approval.domain.model.WorkflowInstanceSnapshot;
import com.bluewhale.oa.approval.domain.model.WorkflowTaskSnapshot;
import com.bluewhale.oa.approval.domain.repository.ApprovalInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 审批通知应用服务实现。
 *
 * <p>通知不进入审批事务的关键路径：任何投递异常都只记录 warn。幂等键按业务语义构造，
 * 由消息服务按 {@code (tenantId, idempotencyKey)} 去重。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalNotificationAppServiceImpl implements ApprovalNotificationAppService {

    /** 通知业务类型 */
    private static final String BIZ_TYPE = "OA_APPROVAL";

    /** 待办模板 */
    private static final String TEMPLATE_TODO = "OA_APPROVAL_TODO";

    /** 结果模板 */
    private static final String TEMPLATE_RESULT = "OA_APPROVAL_RESULT";

    /** 抄送模板 */
    private static final String TEMPLATE_CC = "OA_APPROVAL_CC";

    /** 催办模板 */
    private static final String TEMPLATE_URGE = "OA_APPROVAL_URGE";

    /** 流程运行中状态 */
    private static final int STATUS_RUNNING = 0;

    /** 抄送未读标记 */
    private static final int CC_UNREAD = 0;

    /** 抄送单页上限 */
    private static final long CC_PAGE_SIZE = 200L;

    /** 自动催办幂等键日期格式 */
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    /** 自动催办人标识 */
    private static final String AUTO_URGER = "系统自动催办";

    /** 自动催办说明 */
    private static final String AUTO_URGE_COMMENT = "审批已超过预设时长仍未处理，请尽快处理";

    private final ApprovalInstanceRepository approvalInstanceRepository;

    private final WorkflowApprovalGateway workflowApprovalGateway;

    private final UserDirectoryGateway userDirectoryGateway;

    private final ApprovalNotifyGateway approvalNotifyGateway;

    @Override
    public void notifyTodo(Long workflowInstanceId) {
        try {
            WorkflowInstanceSnapshot snapshot = loadSnapshot(workflowInstanceId);
            if (snapshot == null || !running(snapshot)) {
                return;
            }
            ApprovalInstance local = findLocalByWorkflow(workflowInstanceId);
            List<WorkflowTaskSnapshot> tasks = runningTasks(snapshot);
            for (WorkflowTaskSnapshot task : tasks) {
                Long userId = resolveAccount(task.getAssignee()).orElse(null);
                if (userId == null) {
                    continue;
                }
                String key = "OA_TODO:" + workflowInstanceId + ":" + task.getId() + ":" + userId;
                send(key, TEMPLATE_TODO, local, snapshot, Map.of(
                        "applicant", applicantOf(local, snapshot),
                        "title", titleOf(local, snapshot),
                        "nodeName", nullToEmpty(task.getNodeName()),
                        "businessUrl", businessUrlOf(local)), List.of(userId));
            }
        } catch (RuntimeException exception) {
            log.warn("审批待办通知跳过: workflowInstanceId={}, error={}", workflowInstanceId, exception.getMessage());
        }
    }

    @Override
    public void notifyResult(String businessType, Long businessId, String status) {
        try {
            Optional<ApprovalInstance> local = approvalInstanceRepository.findByBusiness(businessType, businessId);
            if (local.isEmpty() || local.get().getInitiatorId() == null) {
                return;
            }
            ApprovalInstance instance = local.get();
            String key = "OA_RESULT:" + instance.getId() + ":" + status;
            send(key, TEMPLATE_RESULT, instance, null, Map.of(
                    "title", titleOf(instance, null),
                    "result", resultLabel(status),
                    "comment", "",
                    "businessUrl", businessUrlOf(instance)), List.of(instance.getInitiatorId()));
        } catch (RuntimeException exception) {
            log.warn("审批结果通知跳过: businessType={}, businessId={}, error={}",
                    businessType, businessId, exception.getMessage());
        }
    }

    @Override
    public void notifyUrge(Long workflowInstanceId, String targetAccount, String comment, String urger) {
        try {
            WorkflowInstanceSnapshot snapshot = loadSnapshot(workflowInstanceId);
            if (snapshot == null) {
                return;
            }
            ApprovalInstance local = findLocalByWorkflow(workflowInstanceId);
            List<Long> userIds;
            if (StringUtils.hasText(targetAccount)) {
                Long userId = resolveAccount(targetAccount).orElse(null);
                userIds = userId == null ? List.of() : List.of(userId);
            } else {
                userIds = resolveUserIds(assigneeAccounts(snapshot));
            }
            if (userIds.isEmpty()) {
                return;
            }
            String key = "OA_URGE:" + workflowInstanceId + ":" + System.currentTimeMillis();
            send(key, TEMPLATE_URGE, local, snapshot, Map.of(
                    "title", titleOf(local, snapshot),
                    "urger", nullToEmpty(urger),
                    "comment", nullToEmpty(comment),
                    "businessUrl", businessUrlOf(local)), userIds);
        } catch (RuntimeException exception) {
            log.warn("审批催办通知跳过: workflowInstanceId={}, error={}", workflowInstanceId, exception.getMessage());
        }
    }

    @Override
    public void notifyCc(Long workflowInstanceId) {
        try {
            WorkflowInstanceSnapshot snapshot = loadSnapshot(workflowInstanceId);
            if (snapshot == null || !running(snapshot)) {
                return;
            }
            ApprovalInstance local = findLocalByWorkflow(workflowInstanceId);
            WorkflowCcQuery query = new WorkflowCcQuery();
            query.setInstanceId(workflowInstanceId);
            query.setCurrent(1);
            query.setSize(CC_PAGE_SIZE);
            PageResult<WorkflowCcSnapshot> page = workflowApprovalGateway.pageCcs(query);
            for (WorkflowCcSnapshot cc : page.getRecords()) {
                if (cc.getReadFlag() != null && cc.getReadFlag() != CC_UNREAD) {
                    continue;
                }
                resolveAccount(cc.getRecipient()).ifPresent(userId -> send(
                        "OA_CC:" + workflowInstanceId + ":" + cc.getId() + ":" + userId,
                        TEMPLATE_CC, local, snapshot, Map.of(
                                "applicant", applicantOf(local, snapshot),
                                "title", titleOf(local, snapshot),
                                "businessUrl", businessUrlOf(local)), List.of(userId)));
            }
        } catch (RuntimeException exception) {
            log.warn("审批抄送通知跳过: workflowInstanceId={}, error={}", workflowInstanceId, exception.getMessage());
        }
    }

    @Override
    public void notifyAutoUrge(Long workflowInstanceId) {
        try {
            WorkflowInstanceSnapshot snapshot = loadSnapshot(workflowInstanceId);
            if (snapshot == null || !running(snapshot)) {
                return;
            }
            ApprovalInstance local = findLocalByWorkflow(workflowInstanceId);
            Long initiatorId = local == null ? null : local.getInitiatorId();
            List<Long> userIds = resolveUserIds(assigneeAccounts(snapshot)).stream()
                    .filter(userId -> !userId.equals(initiatorId))
                    .toList();
            if (userIds.isEmpty()) {
                return;
            }
            String key = "OA_AUTO_URGE:" + workflowInstanceId + ":" + LocalDate.now().format(DAY_FORMAT);
            send(key, TEMPLATE_URGE, local, snapshot, Map.of(
                    "title", titleOf(local, snapshot),
                    "urger", AUTO_URGER,
                    "comment", AUTO_URGE_COMMENT,
                    "businessUrl", businessUrlOf(local)), userIds);
        } catch (RuntimeException exception) {
            log.warn("自动催办通知跳过: workflowInstanceId={}, error={}", workflowInstanceId, exception.getMessage());
        }
    }

    /**
     * 组装并投递通知。
     *
     * @param idempotencyKey 幂等键
     * @param templateCode 模板编码
     * @param local 本地审批实例，可为空
     * @param snapshot 流程实例快照，可为空
     * @param payload 模板变量
     * @param userIds 接收人
     */
    private void send(String idempotencyKey, String templateCode, ApprovalInstance local,
                      WorkflowInstanceSnapshot snapshot, Map<String, Object> payload, List<Long> userIds) {
        ApprovalNotifyMessage message = new ApprovalNotifyMessage();
        message.setBizType(BIZ_TYPE);
        message.setBizId(local == null || local.getId() == null ? null : String.valueOf(local.getId()));
        message.setIdempotencyKey(idempotencyKey);
        message.setTemplateCode(templateCode);
        message.setTitle(titleOf(local, snapshot));
        message.setContent("");
        message.setPayload(payload);
        message.setUserIds(userIds);
        approvalNotifyGateway.send(message);
    }

    /**
     * 加载流程实例快照。
     *
     * @param workflowInstanceId 流程实例ID
     * @return 快照；ID 为空时返回 {@code null}
     */
    private WorkflowInstanceSnapshot loadSnapshot(Long workflowInstanceId) {
        return workflowInstanceId == null ? null : workflowApprovalGateway.getInstance(workflowInstanceId);
    }

    /**
     * 查询本地审批实例。
     *
     * @param workflowInstanceId 流程实例ID
     * @return 本地实例，可为空
     */
    private ApprovalInstance findLocalByWorkflow(Long workflowInstanceId) {
        return approvalInstanceRepository.findByWorkflowInstanceId(workflowInstanceId).orElse(null);
    }

    /**
     * 判断流程实例是否运行中。
     *
     * @param snapshot 流程实例快照
     * @return 运行中返回 {@code true}
     */
    private boolean running(WorkflowInstanceSnapshot snapshot) {
        return Integer.valueOf(STATUS_RUNNING).equals(snapshot.getStatus());
    }

    /**
     * 过滤出运行中的任务。
     *
     * @param snapshot 流程实例快照
     * @return 运行中任务
     */
    private List<WorkflowTaskSnapshot> runningTasks(WorkflowInstanceSnapshot snapshot) {
        if (snapshot.getTasks() == null) {
            return List.of();
        }
        return snapshot.getTasks().stream()
                .filter(task -> Integer.valueOf(STATUS_RUNNING).equals(task.getStatus()))
                .toList();
    }

    /**
     * 收集当前待办人账号。
     *
     * @param snapshot 流程实例快照
     * @return 账号集合
     */
    private Set<String> assigneeAccounts(WorkflowInstanceSnapshot snapshot) {
        Set<String> accounts = new LinkedHashSet<>();
        runningTasks(snapshot).forEach(task -> {
            if (StringUtils.hasText(task.getAssignee())) {
                accounts.add(task.getAssignee().trim());
            }
        });
        return accounts;
    }

    /**
     * 解析多个账号为去重后的用户ID。
     *
     * @param accounts 账号集合
     * @return 用户ID列表
     */
    private List<Long> resolveUserIds(Set<String> accounts) {
        Set<Long> userIds = new LinkedHashSet<>();
        accounts.forEach(account -> resolveAccount(account).ifPresent(userIds::add));
        return new ArrayList<>(userIds);
    }

    /**
     * 解析单个账号：纯数字按用户ID，其余走用户目录精确匹配。
     *
     * @param account 账号
     * @return 用户ID
     */
    private Optional<Long> resolveAccount(String account) {
        if (!StringUtils.hasText(account)) {
            return Optional.empty();
        }
        String text = account.trim();
        if (text.chars().allMatch(Character::isDigit)) {
            try {
                return Optional.of(Long.parseLong(text));
            } catch (NumberFormatException exception) {
                log.warn("审批人账号无法解析为用户ID: account={}", account);
                return Optional.empty();
            }
        }
        Optional<Long> userId = userDirectoryGateway.findUserIdByUsername(text);
        if (userId.isEmpty()) {
            log.warn("审批人账号未匹配到用户: account={}", account);
        }
        return userId;
    }

    /**
     * 审批标题：本地实例优先，缺省回退流程实例标题。
     *
     * @param local 本地实例
     * @param snapshot 流程实例快照
     * @return 标题
     */
    private String titleOf(ApprovalInstance local, WorkflowInstanceSnapshot snapshot) {
        if (local != null && StringUtils.hasText(local.getTitle())) {
            return local.getTitle();
        }
        return snapshot == null ? "" : nullToEmpty(snapshot.getTitle());
    }

    /**
     * 申请人名称：本地快照优先，缺省回退流程发起人账号。
     *
     * @param local 本地实例
     * @param snapshot 流程实例快照
     * @return 申请人
     */
    private String applicantOf(ApprovalInstance local, WorkflowInstanceSnapshot snapshot) {
        if (local != null && StringUtils.hasText(local.getInitiatorName())) {
            return local.getInitiatorName();
        }
        return snapshot == null ? "" : nullToEmpty(snapshot.getInitiator());
    }

    /**
     * 业务详情路由。
     *
     * @param local 本地实例
     * @return 路由
     */
    private String businessUrlOf(ApprovalInstance local) {
        return local == null ? "" : nullToEmpty(local.getBusinessUrl());
    }

    /**
     * 结果状态转中文。
     *
     * @param status 状态
     * @return 中文标签
     */
    private String resultLabel(String status) {
        return switch (status == null ? "" : status) {
            case "APPROVED" -> "通过";
            case "REJECTED" -> "驳回";
            case "CANCELLED" -> "撤销";
            default -> nullToEmpty(status);
        };
    }

    /**
     * 空值转空串。
     *
     * @param value 原始值
     * @return 非空字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
