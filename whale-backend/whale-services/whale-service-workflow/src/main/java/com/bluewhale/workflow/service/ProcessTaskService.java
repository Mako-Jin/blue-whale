package com.bluewhale.workflow.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.request.ApprovalRequest;
import com.bluewhale.workflow.request.ProcessTaskQueryRequest;
import com.bluewhale.workflow.request.TaskCompleteRequest;
import com.bluewhale.workflow.request.TaskRejectRequest;
import com.bluewhale.workflow.vo.ProcessTaskVO;
import com.bluewhale.workflow.vo.TaskRejectOptionsVO;

import java.util.List;

/**
 * 待办任务服务
 *
 * @author BlueWhale
 */
public interface ProcessTaskService {

    /**
     * 分页查询待办任务
     *
     * @param request 查询条件
     * @return 待办任务分页结果
     */
    PageResult<ProcessTaskVO> page(ProcessTaskQueryRequest request);

    /**
     * 同意任务（可附带流程变量，用于后续条件选路）
     *
     * @param id 任务ID
     * @param request 审批请求，可为 {@code null}
     */
    void approve(Long id, ApprovalRequest request);

    /**
     * 驳回任务（内网终止语义）：实例「已驳回」，同实例其余待办置「已取消」
     *
     * @param id 任务ID
     * @param comment 审批意见，可为 {@code null}
     */
    void reject(Long id, String comment);

    /**
     * 驳回并退回：按目标退回发起人或上游审批节点，实例保持运行中
     *
     * @param id 任务ID
     * @param request 退回请求（目标必填；会签节点忽略目标、按策略处理）
     */
    void returnTask(Long id, TaskRejectRequest request);

    /**
     * 查询当前任务可用的驳回（退回）选项
     *
     * @param id 任务ID
     * @return 驳回选项
     */
    TaskRejectOptionsVO rejectOptions(Long id);

    /**
     * 转办：任务审批人改为目标人，原始责任人保留在 {@code ownerId}
     *
     * @param id 任务ID
     * @param targetUser 目标人账号
     */
    void transfer(Long id, String targetUser);

    /**
     * 委派：任务临时交给目标人处理，原审批人记入 {@code delegateFrom}
     *
     * @param id 任务ID
     * @param targetUser 目标人账号
     */
    void delegate(Long id, String targetUser);

    /**
     * 委派处理完成：当前任务置「已取消」并向原审批人新建待办
     *
     * @param id 任务ID
     * @param comment 处理意见，可为 {@code null}
     */
    void resolve(Long id, String comment);

    /**
     * 回退到指定审批节点：取消当前待办，在目标节点重建待办
     *
     * @param id 任务ID
     * @param targetNodeKey 目标节点键（必须为审批类节点）
     */
    void rollback(Long id, String targetNodeKey);

    /**
     * 加签：为当前节点追加同级审批人，节点需追加人全部同意后才完成
     *
     * @param id 任务ID
     * @param assignees 加签人账号列表（去空白、去重后非空，单次最多 10 人）
     */
    void addSign(Long id, List<String> assignees);

    /**
     * 内网完成任务（第三方签章回调等异步场景，幂等）
     *
     * @param id 任务ID
     * @param request 完成请求（动作/意见/变量/来源）
     */
    void completeInternal(Long id, TaskCompleteRequest request);
}
