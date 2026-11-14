package com.bluewhale.workflow.service.impl.task;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.workflow.mapper.ProcessTaskMapper;
import com.bluewhale.workflow.po.ProcessInstancePo;
import com.bluewhale.workflow.po.ProcessTaskPo;
import com.bluewhale.workflow.support.ProcessModel;
import com.bluewhale.workflow.support.ProcessModelNode;
import com.bluewhale.workflow.support.ProcessModelParser;
import com.bluewhale.workflow.support.ProcessNodeType;
import com.bluewhale.workflow.support.ProcessPaths;
import com.bluewhale.workflow.support.WorkflowConstants;
import com.bluewhale.workflow.vo.TaskRejectOptionsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 驳回选项查询：告诉前端当前任务能退回到哪里。
 *
 * <p>入口节点 / 「重新提交」待办不可驳回；会签节点由策略自动决定目标，
 * 但仍返回策略与候选节点供前端解释。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class TaskRejectOptionsCommand {

    private final ProcessTaskMapper processTaskMapper;

    private final TaskCommandSupport taskCommandSupport;

    private final InstanceModelLoader instanceModelLoader;

    /**
     * 查询驳回选项。
     *
     * @param id 任务ID
     * @return 驳回选项
     */
    public TaskRejectOptionsVO options(Long id) {
        ProcessTaskPo task = processTaskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        TaskRejectOptionsVO vo = new TaskRejectOptionsVO();
        if (task.getStatus() == null || task.getStatus() != WorkflowConstants.TASK_PENDING) {
            return notRejectable(vo, "任务已处理");
        }
        if (WorkflowConstants.RESUBMIT_NODE_KEY.equals(task.getNodeKey())) {
            return notRejectable(vo, "发起节点不支持驳回");
        }
        ProcessInstancePo instance = taskCommandSupport.requireRunningInstance(task.getInstanceId());
        ProcessModel model = ProcessModelParser.parse(instanceModelLoader.resolveInstanceJson(instance));
        String nodeKey = StringUtils.hasText(task.getNodeKey()) ? task.getNodeKey() : instance.getCurrentNodeKey();
        ProcessModelNode node = model.node(nodeKey);
        if (node == null) {
            return notRejectable(vo, "当前节点不存在，无法驳回");
        }
        List<ProcessModelNode> candidates = ProcessPaths.ancestorApprovalNodes(model, nodeKey);
        List<ProcessModelNode> previous = ProcessPaths.nearestPreviousApprovalNodes(model, nodeKey);
        vo.setRejectable(true);
        vo.setInitiator(true);
        vo.setCandidates(candidates.stream()
                .map(item -> new TaskRejectOptionsVO.NodeOption(item.getKey(), item.getName()))
                .toList());
        if (previous.size() == 1) {
            vo.setPrevious(toOption(previous.get(0)));
        }
        if (node.getType() == ProcessNodeType.COUNTER_SIGN) {
            vo.setCounterSign(true);
            vo.setPolicy(StringUtils.hasText(node.getCounterSignPolicy())
                    ? node.getCounterSignPolicy().trim() : WorkflowConstants.POLICY_ALL);
        }
        return vo;
    }

    /**
     * 标记不可驳回。
     *
     * @param vo 选项
     * @param reason 原因
     * @return 选项
     */
    private static TaskRejectOptionsVO notRejectable(TaskRejectOptionsVO vo, String reason) {
        vo.setRejectable(false);
        vo.setReason(reason);
        return vo;
    }

    /**
     * 节点转选项。
     *
     * @param node 节点
     * @return 选项
     */
    private static TaskRejectOptionsVO.NodeOption toOption(ProcessModelNode node) {
        return new TaskRejectOptionsVO.NodeOption(node.getKey(), node.getName());
    }
}
