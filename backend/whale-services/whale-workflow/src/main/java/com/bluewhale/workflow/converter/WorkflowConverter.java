package com.bluewhale.workflow.converter;

import com.bluewhale.workflow.entity.ProcessDefinition;
import com.bluewhale.workflow.entity.ProcessDefinitionVersion;
import com.bluewhale.workflow.entity.ProcessInstance;
import com.bluewhale.workflow.entity.ProcessTask;
import com.bluewhale.workflow.vo.ApprovedInstanceVO;
import com.bluewhale.workflow.vo.ArchiveInstanceDetailVO;
import com.bluewhale.workflow.vo.ProcessDefinitionVO;
import com.bluewhale.workflow.vo.ProcessDefinitionVersionVO;
import com.bluewhale.workflow.vo.ProcessInstanceVO;
import com.bluewhale.workflow.vo.ProcessTaskVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 工作流实体与 VO 的映射器。
 *
 * <p>替代 {@code BeanUtils.copyProperties} 反射拷贝（审计 P2）：编译期校验字段
 * 对齐；派生字段（tasks/finishedAt/definitionJson）由调用方显式设置，在此声明
 * ignore 以保持映射输出干净。</p>
 */
@Mapper
public interface WorkflowConverter {

    /** 单例（无状态映射器） */
    WorkflowConverter INSTANCE = Mappers.getMapper(WorkflowConverter.class);

    /**
     * 流程定义实体转视图对象
     *
     * @param entity 流程定义实体
     * @return 流程定义视图对象
     */
    ProcessDefinitionVO toDefinitionVo(ProcessDefinition entity);

    /**
     * 版本快照实体转视图对象
     *
     * @param entity 版本快照实体
     * @return 版本快照视图对象
     */
    ProcessDefinitionVersionVO toDefinitionVersionVo(ProcessDefinitionVersion entity);

    /**
     * 流程实例实体转视图对象（tasks 由调用方按需装配）
     *
     * @param entity 流程实例实体
     * @return 流程实例视图对象
     */
    @Mapping(target = "tasks", ignore = true)
    ProcessInstanceVO toInstanceVo(ProcessInstance entity);

    /**
     * 任务实体转视图对象
     *
     * @param entity 待办任务实体
     * @return 任务视图对象
     */
    ProcessTaskVO toTaskVo(ProcessTask entity);

    /**
     * 归档实例实体转详情视图对象（派生字段由调用方装配）
     *
     * @param entity 流程实例实体
     * @return 归档详情视图对象
     */
    @Mapping(target = "definitionJson", ignore = true)
    @Mapping(target = "finishedAt", ignore = true)
    @Mapping(target = "tasks", ignore = true)
    ArchiveInstanceDetailVO toArchiveDetailVo(ProcessInstance entity);

    /**
     * 归档实例实体转已通过摘要视图对象（finishedAt 由调用方回填）
     *
     * @param entity 流程实例实体
     * @return 已通过实例摘要视图对象
     */
    @Mapping(target = "finishedAt", ignore = true)
    ApprovedInstanceVO toApprovedVo(ProcessInstance entity);
}
