package com.bluewhale.workflow.service.impl.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.entity.ProcessDefinition;
import com.bluewhale.workflow.entity.ProcessDefinitionVersion;
import com.bluewhale.workflow.entity.ProcessInstance;
import com.bluewhale.workflow.mapper.ProcessDefinitionMapper;
import com.bluewhale.workflow.mapper.ProcessDefinitionVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 流程实例锁定版本的流程定义 JSON 加载器。
 *
 * <p>优先取实例锁定版本的版本快照，快照缺失时回落定义表最新内容，
 * 保证运行期推进与发起时使用的定义语义一致。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class InstanceModelLoader {

    private final ProcessDefinitionMapper processDefinitionMapper;

    private final ProcessDefinitionVersionMapper processDefinitionVersionMapper;

    /**
     * 取实例锁定版本的流程定义 JSON：优先版本快照，缺失回落定义表最新内容。
     *
     * @param instance 流程实例
     * @return 流程定义JSON
     * @throws BizException 定义与快照均缺失时抛出 {@code NOT_FOUND}
     */
    public String resolveInstanceJson(ProcessInstance instance) {
        ProcessDefinitionVersion snapshot = findSnapshot(instance.getDefinitionId(), instance.getDefinitionVersion());
        if (snapshot != null && snapshot.getDefinitionJson() != null && !snapshot.getDefinitionJson().isBlank()) {
            return snapshot.getDefinitionJson();
        }
        ProcessDefinition definition = processDefinitionMapper.selectById(instance.getDefinitionId());
        if (definition == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return definition.getDefinitionJson();
    }

    /**
     * 按定义ID与版本号查询版本快照。
     *
     * @param definitionId 流程定义ID
     * @param version 版本号
     * @return 快照实体，不存在返回 {@code null}
     */
    private ProcessDefinitionVersion findSnapshot(Long definitionId, Integer version) {
        if (definitionId == null || version == null) {
            return null;
        }
        LambdaQueryWrapper<ProcessDefinitionVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessDefinitionVersion::getDefinitionId, definitionId);
        wrapper.eq(ProcessDefinitionVersion::getVersion, version);
        List<ProcessDefinitionVersion> snapshots = processDefinitionVersionMapper.selectList(wrapper);
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }
}
