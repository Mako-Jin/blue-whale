package com.bluewhale.workflow.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.DefinitionEnsureRequest;
import com.bluewhale.workflow.dto.DefinitionMigrationRequest;
import com.bluewhale.workflow.dto.ProcessDefinitionSaveRequest;
import com.bluewhale.workflow.dto.ProcessQueryRequest;
import com.bluewhale.workflow.vo.DefinitionMigrationVO;
import com.bluewhale.workflow.vo.ProcessDefinitionVO;
import com.bluewhale.workflow.vo.ProcessDefinitionVersionVO;

import java.util.List;

/**
 * 流程定义服务
 *
 * @author BlueWhale
 */
public interface ProcessDefinitionService {

    /**
     * 新增流程定义
     *
     * @param request 流程定义请求参数
     * @return 流程定义视图对象
     */
    ProcessDefinitionVO create(ProcessDefinitionSaveRequest request);

    /**
     * 按流程编码幂等 ensure：已存在直接返回，不存在则校验并新建。
     *
     * <p>供种子 / 集成同步使用，重复执行不会重复建定义。</p>
     *
     * @param request ensure 请求
     * @return 已存在或新建的流程定义
     */
    ProcessDefinitionVO ensure(DefinitionEnsureRequest request);

    /**
     * 更新流程定义
     *
     * @param id 流程定义ID
     * @param request 流程定义请求参数
     * @return 流程定义视图对象
     */
    ProcessDefinitionVO update(Long id, ProcessDefinitionSaveRequest request);

    /**
     * 删除流程定义（逻辑删除）
     *
     * @param id 流程定义ID
     */
    void delete(Long id);

    /**
     * 流程定义详情
     *
     * @param id 流程定义ID
     * @return 流程定义视图对象
     */
    ProcessDefinitionVO getDetail(Long id);

    /**
     * 分页查询流程定义
     *
     * @param request 查询条件
     * @return 流程定义分页结果
     */
    PageResult<ProcessDefinitionVO> page(ProcessQueryRequest request);

    /**
     * 查询流程定义的全部版本快照（版本号倒序）。
     *
     * @param id 流程定义ID
     * @return 版本快照列表
     */
    List<ProcessDefinitionVersionVO> versions(Long id);

    /**
     * 发布指定版本：将其置为已发布并把定义表切换到该版本内容，供新实例使用。
     *
     * @param id 流程定义ID
     * @param version 目标版本号
     * @return 切换后的流程定义视图对象
     */
    ProcessDefinitionVO publish(Long id, Integer version);

    /**
     * 把运行中的实例迁移到目标版本（按节点键对齐，缺失节点记失败）。
     *
     * @param id 流程定义ID
     * @param request 迁移请求
     * @return 迁移结果
     */
    DefinitionMigrationVO migrate(Long id, DefinitionMigrationRequest request);
}
