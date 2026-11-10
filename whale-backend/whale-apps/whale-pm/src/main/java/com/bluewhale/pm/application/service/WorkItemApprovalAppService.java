package com.bluewhale.pm.application.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.pm.domain.model.ActivityAction;
import com.bluewhale.pm.infrastructure.client.WorkflowApprovalClient;
import com.bluewhale.pm.infrastructure.config.PmApprovalProperties;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工作项审批应用服务：按需向 whale-workflow 发起审批实例。
 *
 * <p>默认关闭；开启后以 {@code businessKey=工作项ID} 发起，审批过程由 workflow 负责，
 * PM 不内置审批引擎。发起成功后在工作项动态中记录实例ID。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkItemApprovalAppService {

    /** 实例标题最大长度（workflow 侧限制 128） */
    private static final int MAX_TITLE_LENGTH = 120;

    /** 动作字段：审批 */
    private static final String FIELD_APPROVAL = "approval";

    private final WorkItemGuard workItemGuard;

    private final ProjectGuard projectGuard;

    private final WorkItemActivityAppService activityAppService;

    private final WorkflowApprovalClient approvalClient;

    private final PmApprovalProperties properties;

    /**
     * 发起工作项审批。
     *
     * @param id              工作项ID
     * @param operatorId      当前用户ID
     * @param username        当前用户账号
     * @param permissionCodes 当前用户权限码串
     * @return 流程实例ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String submit(Long id, Long operatorId, String username, String permissionCodes) {
        if (!properties.isEnabled()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "审批功能未开启");
        }
        if (properties.getDefinitionId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未配置审批流程定义");
        }
        PmWorkItem item = workItemGuard.requireWritable(id, operatorId);
        projectGuard.requireActiveProject(item.getProjectId());
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("projectId", item.getProjectId());
        variables.put("workItemId", item.getId());
        variables.put("workItemKey", item.getItemKey());
        String instanceId = approvalClient.start(properties.getDefinitionId(),
                title(item), String.valueOf(item.getId()), variables, operatorId, username, permissionCodes);
        activityAppService.record(item.getProjectId(), item.getId(), operatorId, ActivityAction.UPDATE,
                FIELD_APPROVAL, null, instanceId);
        log.info("工作项审批已发起: itemId={}, instanceId={}", item.getId(), instanceId);
        return instanceId;
    }

    /**
     * 构造实例标题（超长截断，避免 workflow 侧校验失败）。
     *
     * @param item 工作项
     * @return 实例标题
     */
    private String title(PmWorkItem item) {
        String title = "[" + item.getItemKey() + "] " + item.getTitle();
        return title.length() <= MAX_TITLE_LENGTH ? title : title.substring(0, MAX_TITLE_LENGTH);
    }
}
