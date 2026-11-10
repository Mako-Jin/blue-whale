package com.bluewhale.oa.hr.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.hr.domain.model.EmployeeChange;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeQuery;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeStatus;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeTask;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeTaskStatus;
import com.bluewhale.oa.hr.domain.model.EmployeeChangeType;
import com.bluewhale.oa.hr.domain.repository.EmployeeChangeRepository;
import com.bluewhale.oa.hr.infrastructure.persistence.entity.EmployeeChangeEntity;
import com.bluewhale.oa.hr.infrastructure.persistence.entity.EmployeeChangeTaskEntity;
import com.bluewhale.oa.hr.infrastructure.persistence.mapper.EmployeeChangeMapper;
import com.bluewhale.oa.hr.infrastructure.persistence.mapper.EmployeeChangeTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 人员异动仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class EmployeeChangeRepositoryImpl implements EmployeeChangeRepository {

    private final EmployeeChangeMapper changeMapper;
    private final EmployeeChangeTaskMapper taskMapper;

    @Override
    public EmployeeChange findById(Long id) {
        EmployeeChangeEntity entity = changeMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public EmployeeChange findByChangeNo(String changeNo) {
        if (!StringUtils.hasText(changeNo)) {
            return null;
        }
        EmployeeChangeEntity entity = changeMapper.selectOne(new LambdaQueryWrapper<EmployeeChangeEntity>()
                .eq(EmployeeChangeEntity::getChangeNo, changeNo)
                .last("LIMIT 1"));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public PageResult<EmployeeChange> page(EmployeeChangeQuery query) {
        Page<EmployeeChangeEntity> page = changeMapper.selectPage(query.toPage(), filter(query));
        return PageResult.of(page.convert(EmployeeChangeRepositoryImpl::toDomain));
    }

    @Override
    public List<EmployeeChangeTask> findTasks(Long changeId) {
        return taskMapper.selectList(new LambdaQueryWrapper<EmployeeChangeTaskEntity>()
                        .eq(EmployeeChangeTaskEntity::getChangeId, changeId)
                        .orderByAsc(EmployeeChangeTaskEntity::getId))
                .stream().map(EmployeeChangeRepositoryImpl::toDomain).toList();
    }

    @Override
    public EmployeeChangeTask findTask(Long changeId, Long taskId) {
        EmployeeChangeTaskEntity entity = taskMapper.selectOne(new LambdaQueryWrapper<EmployeeChangeTaskEntity>()
                .eq(EmployeeChangeTaskEntity::getChangeId, changeId)
                .eq(EmployeeChangeTaskEntity::getId, taskId)
                .last("LIMIT 1"));
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void insert(EmployeeChange change) {
        EmployeeChangeEntity entity = toEntity(change);
        changeMapper.insert(entity);
        change.setId(entity.getId());
        change.setCreateTime(entity.getCreateTime());
        change.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void update(EmployeeChange change) {
        changeMapper.updateById(toEntity(change));
    }

    @Override
    public void insertTask(EmployeeChangeTask task) {
        EmployeeChangeTaskEntity entity = toEntity(task);
        taskMapper.insert(entity);
        task.setId(entity.getId());
        task.setCreateTime(entity.getCreateTime());
        task.setUpdateTime(entity.getUpdateTime());
    }

    @Override
    public void updateTask(EmployeeChangeTask task) {
        taskMapper.updateById(toEntity(task));
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<EmployeeChangeEntity> filter(EmployeeChangeQuery query) {
        LambdaQueryWrapper<EmployeeChangeEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(EmployeeChangeEntity::getChangeNo, keyword)
                    .or().like(EmployeeChangeEntity::getEmployeeName, keyword)
                    .or().like(EmployeeChangeEntity::getUsername, keyword));
        }
        if (StringUtils.hasText(query.getChangeType())) {
            wrapper.eq(EmployeeChangeEntity::getChangeType, query.getChangeType().trim().toUpperCase());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(EmployeeChangeEntity::getStatus, query.getStatus().trim().toUpperCase());
        }
        if (query.getUserId() != null) {
            wrapper.eq(EmployeeChangeEntity::getUserId, query.getUserId());
        }
        wrapper.orderByDesc(EmployeeChangeEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private static EmployeeChange toDomain(EmployeeChangeEntity entity) {
        EmployeeChange change = new EmployeeChange();
        change.setId(entity.getId());
        change.setChangeNo(entity.getChangeNo());
        change.setChangeType(parseType(entity.getChangeType()));
        change.setUserId(entity.getUserId());
        change.setEmployeeName(entity.getEmployeeName());
        change.setUsername(entity.getUsername());
        change.setFromDeptId(entity.getFromDeptId());
        change.setToDeptId(entity.getToDeptId());
        change.setFromStatus(entity.getFromStatus());
        change.setToStatus(entity.getToStatus());
        change.setEffectiveDate(entity.getEffectiveDate());
        change.setReason(entity.getReason());
        change.setStatus(parseStatus(entity.getStatus()));
        change.setApprovalInstanceId(entity.getApprovalInstanceId());
        change.setApplicantId(entity.getApplicantId());
        change.setApplicantName(entity.getApplicantName());
        change.setExecutorId(entity.getExecutorId());
        change.setExecutorName(entity.getExecutorName());
        change.setExecutedTime(entity.getExecutedTime());
        change.setExecuteResult(entity.getExecuteResult());
        change.setRemark(entity.getRemark());
        change.setCreateTime(entity.getCreateTime());
        change.setUpdateTime(entity.getUpdateTime());
        return change;
    }

    /**
     * 领域模型转实体。
     *
     * @param change 领域模型
     * @return 实体
     */
    private static EmployeeChangeEntity toEntity(EmployeeChange change) {
        EmployeeChangeEntity entity = new EmployeeChangeEntity();
        entity.setId(change.getId());
        entity.setChangeNo(change.getChangeNo());
        entity.setChangeType(change.getChangeType() == null ? null : change.getChangeType().name());
        entity.setUserId(change.getUserId());
        entity.setEmployeeName(change.getEmployeeName());
        entity.setUsername(change.getUsername());
        entity.setFromDeptId(change.getFromDeptId());
        entity.setToDeptId(change.getToDeptId());
        entity.setFromStatus(change.getFromStatus());
        entity.setToStatus(change.getToStatus());
        entity.setEffectiveDate(change.getEffectiveDate());
        entity.setReason(change.getReason());
        entity.setStatus(change.getStatus() == null ? null : change.getStatus().name());
        entity.setApprovalInstanceId(change.getApprovalInstanceId());
        entity.setApplicantId(change.getApplicantId());
        entity.setApplicantName(change.getApplicantName());
        entity.setExecutorId(change.getExecutorId());
        entity.setExecutorName(change.getExecutorName());
        entity.setExecutedTime(change.getExecutedTime());
        entity.setExecuteResult(change.getExecuteResult());
        entity.setRemark(change.getRemark());
        return entity;
    }

    /**
     * 任务实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private static EmployeeChangeTask toDomain(EmployeeChangeTaskEntity entity) {
        EmployeeChangeTask task = new EmployeeChangeTask();
        task.setId(entity.getId());
        task.setChangeId(entity.getChangeId());
        task.setTaskCode(entity.getTaskCode());
        task.setTaskName(entity.getTaskName());
        task.setAssigneeId(entity.getAssigneeId());
        task.setAssigneeName(entity.getAssigneeName());
        task.setStatus(entity.getStatus() == null ? null : EmployeeChangeTaskStatus.valueOf(entity.getStatus()));
        task.setDoneTime(entity.getDoneTime());
        task.setRemark(entity.getRemark());
        task.setCreateTime(entity.getCreateTime());
        task.setUpdateTime(entity.getUpdateTime());
        return task;
    }

    /**
     * 任务领域模型转实体。
     *
     * @param task 领域模型
     * @return 实体
     */
    private static EmployeeChangeTaskEntity toEntity(EmployeeChangeTask task) {
        EmployeeChangeTaskEntity entity = new EmployeeChangeTaskEntity();
        entity.setId(task.getId());
        entity.setChangeId(task.getChangeId());
        entity.setTaskCode(task.getTaskCode());
        entity.setTaskName(task.getTaskName());
        entity.setAssigneeId(task.getAssigneeId());
        entity.setAssigneeName(task.getAssigneeName());
        entity.setStatus(task.getStatus() == null ? null : task.getStatus().name());
        entity.setDoneTime(task.getDoneTime());
        entity.setRemark(task.getRemark());
        return entity;
    }

    /**
     * 解析异动类型，兼容历史脏数据。
     *
     * @param value 类型字符串
     * @return 枚举或 null
     */
    private static EmployeeChangeType parseType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return EmployeeChangeType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 解析异动状态，兼容历史脏数据。
     *
     * @param value 状态字符串
     * @return 枚举或 null
     */
    private static EmployeeChangeStatus parseStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return EmployeeChangeStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
