package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.pm.application.assembler.PmAssembler;
import com.bluewhale.pm.domain.model.ActivityAction;
import com.bluewhale.pm.infrastructure.entity.PmActivity;
import com.bluewhale.pm.infrastructure.mapper.PmActivityMapper;
import com.bluewhale.pm.interfaces.vo.ActivityVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 工作项动态应用服务：append-only 记录与查询。
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkItemActivityAppService {

    /** 动态值最大长度（与 pm_activity.old_value / new_value 列宽一致） */
    private static final int VALUE_MAX_LENGTH = 512;

    /** 动态值截断保留长度 */
    private static final int VALUE_KEEP_LENGTH = 500;

    private final PmActivityMapper activityMapper;

    /**
     * 记录一条工作项动态。
     *
     * @param projectId  项目ID
     * @param workItemId 工作项ID
     * @param actorId    操作人用户ID
     * @param action     动作
     * @param field      变更字段
     * @param oldValue   旧值
     * @param newValue   新值
     */
    public void record(Long projectId, Long workItemId, Long actorId, ActivityAction action,
            String field, String oldValue, String newValue) {
        PmActivity activity = new PmActivity();
        activity.setProjectId(projectId);
        activity.setWorkItemId(workItemId);
        activity.setActorId(actorId);
        activity.setAction(action.name());
        activity.setField(field);
        activity.setOldValue(truncate(oldValue));
        activity.setNewValue(truncate(newValue));
        activity.setTraceId(currentTraceId());
        activityMapper.insert(activity);
    }

    /**
     * 查询工作项动态（按时间升序）。
     *
     * @param workItemId 工作项ID
     * @return 动态列表
     */
    public List<ActivityVO> listByItem(Long workItemId) {
        List<PmActivity> activities = activityMapper.selectList(new LambdaQueryWrapper<PmActivity>()
                .eq(PmActivity::getWorkItemId, workItemId)
                .orderByAsc(PmActivity::getCreateTime)
                .orderByAsc(PmActivity::getId));
        return activities.stream().map(PmAssembler::toActivityVo).toList();
    }

    /**
     * 查询项目级动态（按时间倒序，最多 100 条）。
     *
     * @param projectId 项目ID
     * @return 动态列表
     */
    public List<ActivityVO> listByProject(Long projectId) {
        List<PmActivity> activities = activityMapper.selectList(new LambdaQueryWrapper<PmActivity>()
                .eq(PmActivity::getProjectId, projectId)
                .orderByDesc(PmActivity::getCreateTime)
                .orderByDesc(PmActivity::getId)
                .last("LIMIT 100"));
        return activities.stream().map(PmAssembler::toActivityVo).toList();
    }

    /**
     * 截断超长动态值，避免超出列宽。
     *
     * @param value 原值
     * @return 截断后的值
     */
    private String truncate(String value) {
        if (value == null || value.length() <= VALUE_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, VALUE_KEEP_LENGTH);
    }

    /**
     * 读取当前请求的链路追踪ID，非 Web 上下文返回 {@code null}。
     *
     * @return 链路追踪ID
     */
    private String currentTraceId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return WebHeaders.traceId(servletAttributes.getRequest());
        }
        return null;
    }
}
