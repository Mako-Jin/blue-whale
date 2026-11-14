package com.bluewhale.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.converter.WorkflowConverter;
import com.bluewhale.workflow.request.DefinitionEnsureRequest;
import com.bluewhale.workflow.request.DefinitionMigrationRequest;
import com.bluewhale.workflow.request.ProcessDefinitionSaveRequest;
import com.bluewhale.workflow.request.ProcessQueryRequest;
import com.bluewhale.workflow.po.ProcessDefinitionPo;
import com.bluewhale.workflow.po.ProcessDefinitionVersionPo;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.mapper.ProcessDefinitionMapper;
import com.bluewhale.workflow.mapper.ProcessDefinitionVersionMapper;
import com.bluewhale.workflow.mapper.ProcessInstanceMapper;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.service.ProcessDefinitionService;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
import com.bluewhale.workflow.support.ProcessModelValidator;
import com.bluewhale.workflow.support.ValidationReport;
import com.bluewhale.workflow.support.WorkflowConstants;
import com.bluewhale.workflow.support.WorkflowMetrics;
import com.bluewhale.workflow.vo.DefinitionMigrationVO;
import com.bluewhale.workflow.vo.ProcessDefinitionVO;
import com.bluewhale.workflow.vo.ProcessDefinitionVersionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 流程定义服务实现
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessDefinitionServiceImpl implements ProcessDefinitionService {

    /** 流程版本号默认值 */
    private static final int DEFAULT_VERSION = 1;

    /** 流程定义 JSON 最大长度（字符数）：64KB */
    private static final int MAX_DEFINITION_LENGTH = 65536;

    /** 迁移失败明细最大保留条数 */
    private static final int MAX_MIGRATION_FAILURES = 100;

    private final ProcessDefinitionMapper processDefinitionMapper;

    private final ProcessDefinitionVersionMapper processDefinitionVersionMapper;

    private final ProcessInstanceMapper processInstanceMapper;

    private final ProcessTaskMapper processTaskMapper;

    private final WorkflowMetrics workflowMetrics;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessDefinitionVO create(ProcessDefinitionSaveRequest request) {
        assertCodeUnique(request.getCode(), null);
        validateDefinitionSize(request.getDefinitionJson());
        ProcessDefinitionPo entity = new ProcessDefinitionPo();
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setDefinitionJson(request.getDefinitionJson());
        entity.setVersion(request.getVersion() == null ? DEFAULT_VERSION : request.getVersion());
        entity.setStatus(request.getStatus() == null ? WorkflowConstants.DEFINITION_ENABLED : request.getStatus());
        entity.setRemark(request.getRemark());
        try {
            processDefinitionMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            workflowMetrics.recordCodeConflict();
            log.warn("流程编码并发冲突: code={}", request.getCode());
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "流程编码已存在");
        }
        saveSnapshot(entity.getId(), entity.getVersion(), entity.getDefinitionJson(),
                WorkflowConstants.PUBLISH_PUBLISHED);
        return toVo(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessDefinitionVO ensure(DefinitionEnsureRequest request) {
        String code = request.getCode().trim();
        ProcessDefinitionPo existing = findDefinitionByCode(code);
        if (existing != null) {
            return toVo(existing);
        }
        ValidationReport report = ProcessModelValidator.validate(request.getDefinitionJson());
        if (!report.isValid()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "流程定义校验未通过：" + code);
        }
        ProcessDefinitionSaveRequest saveRequest = new ProcessDefinitionSaveRequest();
        saveRequest.setCode(code);
        saveRequest.setName(request.getName().trim());
        saveRequest.setDefinitionJson(request.getDefinitionJson());
        saveRequest.setRemark(request.getRemark());
        return create(saveRequest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessDefinitionVO update(Long id, ProcessDefinitionSaveRequest request) {
        ProcessDefinitionPo entity = requireProcess(id);
        if (!entity.getCode().equals(request.getCode())) {
            assertCodeUnique(request.getCode(), id);
            entity.setCode(request.getCode());
        }
        validateDefinitionSize(request.getDefinitionJson());
        int previousVersion = entity.getVersion() == null ? DEFAULT_VERSION : entity.getVersion();
        boolean jsonChanged = !Objects.equals(entity.getDefinitionJson(), request.getDefinitionJson());
        int targetVersion = resolveUpdateVersion(request.getVersion(), previousVersion, jsonChanged);
        if (jsonChanged) {
            rejectPublishedTargetVersion(entity.getId(), targetVersion);
        }
        entity.setName(request.getName());
        entity.setDefinitionJson(request.getDefinitionJson());
        entity.setVersion(targetVersion);
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        entity.setRemark(request.getRemark());
        try {
            processDefinitionMapper.updateById(entity);
        } catch (DuplicateKeyException e) {
            workflowMetrics.recordCodeConflict();
            log.warn("流程编码并发冲突: definitionId={}, code={}", entity.getId(), request.getCode());
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "流程编码已存在");
        }
        if (jsonChanged) {
            // 内容变更只写草稿快照；发布态仅由 publish() 显式切换，避免绕过发布流程产生多个已发布版本
            saveSnapshot(entity.getId(), entity.getVersion(), entity.getDefinitionJson(),
                    WorkflowConstants.PUBLISH_DRAFT);
        }
        return toVo(entity);
    }

    /**
     * 解析更新后的目标版本号：内容变更时版本号必须前进，避免覆盖当前已发布版本。
     *
     * @param requestVersion 请求显式指定的版本号，可为 {@code null}
     * @param previousVersion 更新前的版本号
     * @param jsonChanged 流程定义内容是否变更
     * @return 目标版本号
     */
    private static int resolveUpdateVersion(Integer requestVersion, int previousVersion, boolean jsonChanged) {
        int targetVersion = requestVersion == null
                ? (jsonChanged ? previousVersion + 1 : previousVersion)
                : requestVersion;
        if (jsonChanged && targetVersion == previousVersion) {
            targetVersion = previousVersion + 1;
        }
        return targetVersion;
    }

    /**
     * 拒绝把内容变更写入已发布快照：已发布内容只能通过 publish() 切换。
     *
     * @param definitionId 流程定义ID
     * @param targetVersion 目标版本号
     * @throws BizException 目标版本已发布时抛出 {@code PARAM_ERROR}
     */
    private void rejectPublishedTargetVersion(Long definitionId, int targetVersion) {
        ProcessDefinitionVersionPo target = findSnapshot(definitionId, targetVersion);
        if (target != null && target.getPublishStatus() != null
                && target.getPublishStatus() == WorkflowConstants.PUBLISH_PUBLISHED) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "目标版本已发布，内容变更请使用新版本号");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireProcess(id);
        processDefinitionMapper.deleteById(id);
    }

    @Override
    public ProcessDefinitionVO getDetail(Long id) {
        return toVo(requireProcess(id));
    }

    @Override
    public PageResult<ProcessDefinitionVO> page(ProcessQueryRequest request) {
        LambdaQueryWrapper<ProcessDefinitionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getName()), ProcessDefinitionPo::getName, request.getName());
        wrapper.eq(StringUtils.hasText(request.getCode()), ProcessDefinitionPo::getCode, request.getCode());
        wrapper.eq(request.getStatus() != null, ProcessDefinitionPo::getStatus, request.getStatus());
        wrapper.orderByDesc(ProcessDefinitionPo::getCreateTime);
        long current = Math.min(Math.max(request.getCurrent(), 1L), WorkflowConstants.MAX_PAGE_CURRENT);
        long size = Math.min(Math.max(request.getSize(), 1L), WorkflowConstants.MAX_PAGE_SIZE);
        Page<ProcessDefinitionPo> page = processDefinitionMapper.selectPage(new Page<>(current, size), wrapper);
        List<ProcessDefinitionVO> records = page.getRecords().stream().map(ProcessDefinitionServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), current, size);
    }

    @Override
    public List<ProcessDefinitionVersionVO> versions(Long id) {
        requireProcess(id);
        LambdaQueryWrapper<ProcessDefinitionVersionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessDefinitionVersionPo::getDefinitionId, id);
        wrapper.orderByDesc(ProcessDefinitionVersionPo::getVersion);
        return processDefinitionVersionMapper.selectList(wrapper).stream()
                .map(ProcessDefinitionServiceImpl::toVersionVo).toList();
    }

    @Override
    @AuditLog(operation = "发布流程定义", type = OperateType.UPDATE,
            targetType = "process_definition", targetId = "#id")
    @Transactional(rollbackFor = Exception.class)
    public ProcessDefinitionVO publish(Long id, Integer version) {
        ProcessDefinitionPo definition = requireProcess(id);
        ProcessDefinitionVersionPo snapshot = requireSnapshot(id, version);
        snapshot.setPublishStatus(WorkflowConstants.PUBLISH_PUBLISHED);
        processDefinitionVersionMapper.updateById(snapshot);
        clearOtherPublished(id, snapshot.getId());
        definition.setVersion(snapshot.getVersion());
        definition.setDefinitionJson(snapshot.getDefinitionJson());
        processDefinitionMapper.updateById(definition);
        workflowMetrics.recordDefinitionPublish();
        log.info("流程定义已发布: definitionId={}, code={}, version={}",
                definition.getId(), definition.getCode(), snapshot.getVersion());
        return toVo(definition);
    }

    @Override
    @AuditLog(operation = "迁移流程定义实例", type = OperateType.UPDATE,
            targetType = "process_definition", targetId = "#id", recordParams = false)
    @Transactional(rollbackFor = Exception.class)
    public DefinitionMigrationVO migrate(Long id, DefinitionMigrationRequest request) {
        requireProcess(id);
        int targetVersion = request.getTargetVersion();
        ProcessDefinitionVersionPo snapshot = requireSnapshot(id, targetVersion);
        ProcessModel targetModel = ProcessModelParser.parse(snapshot.getDefinitionJson());
        DefinitionMigrationVO vo = new DefinitionMigrationVO();
        vo.setDefinitionId(id);
        vo.setTargetVersion(targetVersion);
        for (ProcessInstancePo instance : listInstances(id, request.getInstanceIds())) {
            migrateInstance(instance, targetVersion, targetModel, vo);
        }
        return vo;
    }

    /**
     * 写入版本快照：同版本已存在则覆盖内容与发布状态，否则新增。
     *
     * @param definitionId 流程定义ID
     * @param version 版本号
     * @param definitionJson 流程定义JSON
     * @param publishStatus 发布状态：0 草稿 / 1 已发布
     */
    private void saveSnapshot(Long definitionId, Integer version, String definitionJson, int publishStatus) {
        ProcessDefinitionVersionPo existing = findSnapshot(definitionId, version);
        if (existing == null) {
            ProcessDefinitionVersionPo snapshot = new ProcessDefinitionVersionPo();
            snapshot.setDefinitionId(definitionId);
            snapshot.setVersion(version);
            snapshot.setDefinitionJson(definitionJson);
            snapshot.setPublishStatus(publishStatus);
            processDefinitionVersionMapper.insert(snapshot);
            return;
        }
        existing.setDefinitionJson(definitionJson);
        existing.setPublishStatus(publishStatus);
        processDefinitionVersionMapper.updateById(existing);
    }

    /**
     * 按定义ID与版本号查询快照。
     *
     * @param definitionId 流程定义ID
     * @param version 版本号
     * @return 快照实体，不存在返回 {@code null}
     */
    private ProcessDefinitionVersionPo findSnapshot(Long definitionId, Integer version) {
        if (definitionId == null || version == null) {
            return null;
        }
        LambdaQueryWrapper<ProcessDefinitionVersionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessDefinitionVersionPo::getDefinitionId, definitionId);
        wrapper.eq(ProcessDefinitionVersionPo::getVersion, version);
        List<ProcessDefinitionVersionPo> snapshots = processDefinitionVersionMapper.selectList(wrapper);
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }

    /**
     * 查询快照，不存在则抛参数错误。
     *
     * @param definitionId 流程定义ID
     * @param version 版本号
     * @return 快照实体
     * @throws BizException 版本不存在时抛出 {@code PARAM_ERROR}
     */
    private ProcessDefinitionVersionPo requireSnapshot(Long definitionId, Integer version) {
        ProcessDefinitionVersionPo snapshot = findSnapshot(definitionId, version);
        if (snapshot == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "流程版本不存在：" + version);
        }
        return snapshot;
    }

    /**
     * 把其余已发布快照退回草稿，保证同一定义同时只有一个已发布版本。
     *
     * @param definitionId 流程定义ID
     * @param keepId 需保留发布态的快照ID
     */
    private void clearOtherPublished(Long definitionId, Long keepId) {
        LambdaQueryWrapper<ProcessDefinitionVersionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessDefinitionVersionPo::getDefinitionId, definitionId);
        wrapper.eq(ProcessDefinitionVersionPo::getPublishStatus, WorkflowConstants.PUBLISH_PUBLISHED);
        wrapper.ne(ProcessDefinitionVersionPo::getId, keepId);
        for (ProcessDefinitionVersionPo other : processDefinitionVersionMapper.selectList(wrapper)) {
            other.setPublishStatus(WorkflowConstants.PUBLISH_DRAFT);
            processDefinitionVersionMapper.updateById(other);
        }
    }

    /**
     * 查询迁移候选实例（可按实例ID过滤）。
     *
     * @param definitionId 流程定义ID
     * @param instanceIds 实例ID列表，可为空
     * @return 实例列表
     */
    private List<ProcessInstancePo> listInstances(Long definitionId, List<Long> instanceIds) {
        LambdaQueryWrapper<ProcessInstancePo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessInstancePo::getDefinitionId, definitionId);
        if (instanceIds != null && !instanceIds.isEmpty()) {
            wrapper.in(ProcessInstancePo::getId, instanceIds);
        }
        wrapper.orderByAsc(ProcessInstancePo::getId);
        return processInstanceMapper.selectList(wrapper);
    }

    /**
     * 迁移单个实例：非运行中或已在目标版本则跳过，目标版本缺节点则记失败。
     *
     * @param instance 流程实例
     * @param targetVersion 目标版本号
     * @param targetModel 目标版本流程模型
     * @param vo 迁移结果累加器
     */
    private void migrateInstance(ProcessInstancePo instance, int targetVersion, ProcessModel targetModel,
                                 DefinitionMigrationVO vo) {
        if (instance.getStatus() == null || instance.getStatus() != WorkflowConstants.INSTANCE_RUNNING
                || Objects.equals(instance.getDefinitionVersion(), targetVersion)) {
            vo.setSkipped(vo.getSkipped() + 1);
            return;
        }
        List<String> keys = activeKeys(instance);
        String missing = missingNode(keys, targetModel);
        if (missing != null) {
            vo.setFailed(vo.getFailed() + 1);
            if (vo.getFailures().size() < MAX_MIGRATION_FAILURES) {
                vo.getFailures().add(instance.getId() + ": " + missing);
            }
            return;
        }
        int oldIndex = instance.getCurrentIndex() == null ? 0 : instance.getCurrentIndex();
        ProcessModelNode node = targetModel.node(keys.get(0));
        instance.setDefinitionVersion(targetVersion);
        instance.setCurrentNodeKey(node.getKey());
        instance.setCurrentIndex(targetModel.indexOf(node.getKey()));
        processInstanceMapper.updateById(instance);
        updatePendingTasks(instance, targetModel, oldIndex);
        vo.setMigrated(vo.getMigrated() + 1);
    }

    /**
     * 解析实例的活动节点键：优先并行活动节点键，其次当前节点键，最后回落旧版下标。
     *
     * @param instance 流程实例
     * @return 节点键列表，无法解析返回空列表
     */
    private List<String> activeKeys(ProcessInstancePo instance) {
        if (StringUtils.hasText(instance.getActiveNodeKeys())) {
            return List.of(instance.getActiveNodeKeys().split(","));
        }
        String key = resolveInstanceKey(instance);
        return key == null ? List.of() : List.of(key);
    }

    /**
     * 解析实例当前节点键：优先快照字段，为空时按锁定版本的模型下标回推。
     *
     * @param instance 流程实例
     * @return 节点键，无法解析返回 {@code null}
     */
    private String resolveInstanceKey(ProcessInstancePo instance) {
        if (StringUtils.hasText(instance.getCurrentNodeKey())) {
            return instance.getCurrentNodeKey();
        }
        String json = instanceDefinitionJson(instance);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        ProcessModel model = ProcessModelParser.parse(json);
        ProcessModelNode node = model.nodeAt(instance.getCurrentIndex() == null ? 0 : instance.getCurrentIndex());
        return node == null ? null : node.getKey();
    }

    /**
     * 取实例锁定版本的流程定义 JSON：优先版本快照，缺失回落定义表。
     *
     * @param instance 流程实例
     * @return 流程定义JSON，均缺失返回 {@code null}
     */
    private String instanceDefinitionJson(ProcessInstancePo instance) {
        ProcessDefinitionVersionPo snapshot = findSnapshot(instance.getDefinitionId(), instance.getDefinitionVersion());
        if (snapshot != null) {
            return snapshot.getDefinitionJson();
        }
        ProcessDefinitionPo definition = processDefinitionMapper.selectById(instance.getDefinitionId());
        return definition == null ? null : definition.getDefinitionJson();
    }

    /**
     * 校验目标版本是否覆盖全部活动节点。
     *
     * @param keys 活动节点键
     * @param targetModel 目标版本流程模型
     * @return 失败原因，全部可用返回 {@code null}
     */
    private static String missingNode(List<String> keys, ProcessModel targetModel) {
        if (keys.isEmpty()) {
            return "无法解析实例当前节点";
        }
        for (String key : keys) {
            ProcessModelNode node = targetModel.node(key);
            if (node == null) {
                return "目标版本缺少节点 " + key;
            }
            if (node.getType() == null || !node.getType().isApproval()) {
                return "节点 " + key + " 在目标版本中不是审批节点";
            }
        }
        return null;
    }

    /**
     * 同步实例的待办任务到目标版本的节点下标 / 名称 / 表单绑定。
     *
     * @param instance 已迁移的流程实例
     * @param targetModel 目标版本流程模型
     * @param oldIndex 迁移前的节点下标
     */
    private void updatePendingTasks(ProcessInstancePo instance, ProcessModel targetModel, int oldIndex) {
        LambdaQueryWrapper<ProcessTaskPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessTaskPo::getInstanceId, instance.getId());
        wrapper.eq(ProcessTaskPo::getStatus, WorkflowConstants.TASK_PENDING);
        for (ProcessTaskPo task : processTaskMapper.selectList(wrapper)) {
            String key = task.getNodeKey();
            if (!StringUtils.hasText(key) && Objects.equals(task.getNodeIndex(), oldIndex)) {
                key = instance.getCurrentNodeKey();
            }
            ProcessModelNode node = key == null ? null : targetModel.node(key);
            if (node == null) {
                continue;
            }
            task.setNodeKey(node.getKey());
            task.setNodeIndex(targetModel.indexOf(node.getKey()));
            task.setNodeName(node.getName());
            task.setFormId(node.getFormId());
            task.setFormCode(node.getFormCode());
            processTaskMapper.updateById(task);
        }
    }

    /**
     * 按流程编码查询定义（取最新一条）。
     *
     * @param code 流程编码
     * @return 流程定义，不存在返回 {@code null}
     */
    private ProcessDefinitionPo findDefinitionByCode(String code) {
        LambdaQueryWrapper<ProcessDefinitionPo> wrapper = new LambdaQueryWrapper<ProcessDefinitionPo>()
                .eq(ProcessDefinitionPo::getCode, code)
                .orderByDesc(ProcessDefinitionPo::getId)
                .last("LIMIT 1");
        List<ProcessDefinitionPo> definitions = processDefinitionMapper.selectList(wrapper);
        return definitions.isEmpty() ? null : definitions.get(0);
    }

    /**
     * 查询流程定义，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 流程定义ID
     * @return 流程定义实体
     */
    private ProcessDefinitionPo requireProcess(Long id) {
        ProcessDefinitionPo entity = processDefinitionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return entity;
    }

    /**
     * 校验流程编码唯一性：命中已存在的编码则抛参数错误
     *
     * @param code 流程编码
     * @param excludeId 需排除的当前记录ID（更新时传入，新增传 {@code null}）
     */
    private void assertCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<ProcessDefinitionPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessDefinitionPo::getCode, code);
        wrapper.ne(excludeId != null, ProcessDefinitionPo::getId, excludeId);
        Long count = processDefinitionMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "流程编码已存在");
        }
    }

    /**
     * 校验流程定义长度不超过 64KB
     *
     * @param definitionJson 流程定义JSON
     * @throws BizException 超长时抛出 {@code PARAM_ERROR}
     */
    private static void validateDefinitionSize(String definitionJson) {
        if (definitionJson != null && definitionJson.length() > MAX_DEFINITION_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "流程定义长度不能超过 65536 字符");
        }
    }

    /**
     * 流程实体转视图对象
     *
     * @param entity 流程定义实体
     * @return 流程定义视图对象
     */
    private static ProcessDefinitionVO toVo(ProcessDefinitionPo entity) {
        return WorkflowConverter.INSTANCE.toDefinitionVo(entity);
    }

    /**
     * 版本快照实体转视图对象
     *
     * @param entity 版本快照实体
     * @return 版本快照视图对象
     */
    private static ProcessDefinitionVersionVO toVersionVo(ProcessDefinitionVersionPo entity) {
        return WorkflowConverter.INSTANCE.toDefinitionVersionVo(entity);
    }
}
