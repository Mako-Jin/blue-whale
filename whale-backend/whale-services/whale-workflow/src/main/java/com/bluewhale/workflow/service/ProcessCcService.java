package com.bluewhale.workflow.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.dto.ProcessCcQueryRequest;
import com.bluewhale.workflow.vo.ProcessCcVO;

import java.util.List;

/**
 * 流程抄送服务：抄送只发通知，不产生待办。
 *
 * @author BlueWhale
 */
public interface ProcessCcService {

    /**
     * 分页查询抄送记录。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ProcessCcVO> page(ProcessCcQueryRequest query);

    /**
     * 按主键查询抄送记录。
     *
     * @param id 抄送记录ID
     * @return 抄送记录
     */
    ProcessCcVO get(Long id);

    /**
     * 标记抄送已读（幂等）。
     *
     * @param id 抄送记录ID
     */
    void markRead(Long id);

    /**
     * 写入发起级抄送（节点键固定为 {@code START}）。
     *
     * @param instanceId 流程实例ID
     * @param ccUserIds 收件人用户ID
     */
    void createStartCc(Long instanceId, List<Long> ccUserIds);

    /**
     * 写入节点级抄送。
     *
     * @param instanceId 流程实例ID
     * @param nodeKey 抄送节点键
     * @param nodeName 抄送节点名称
     * @param recipients 收件人
     */
    void createNodeCc(Long instanceId, String nodeKey, String nodeName, List<String> recipients);
}
