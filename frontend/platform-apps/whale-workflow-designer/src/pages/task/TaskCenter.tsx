// 我的待办 + 审批页：分页待办列表 + 审批抽屉（表单可编辑保存 + 意见 + 同意/驳回）
// 另提供转办 / 委派 / 回退 / 处理完成（resolve）/ 加签操作，入参经 Modal + Form 收集。
// 契约层：../../api/workflow（待办/审批/实例）、../../api/form（表单定义/数据），本文件不改动契约层。
// 雪花 ID 全链路只用 string；审批前若有未保存的表单修改先 PUT /form/data/{id}。
//
// 编排职责在此；列表数据、抽屉状态、办理动作与对应 UI 见 task/ 下的 hooks 与组件。

import { useTaskList } from './useTaskList';
import { useApprovalDrawer } from './useApprovalDrawer';
import { useTaskActions } from './useTaskActions';
import ApprovalDrawer from './ApprovalDrawer';
import TaskActionModal from './TaskActionModal';
import TaskListTable from './TaskListTable';

export default function TaskCenter() {
  const list = useTaskList();
  const approval = useApprovalDrawer({
    loadList: list.loadList,
    setInstanceBrief: list.setInstanceBrief,
  });
  const actions = useTaskActions({
    activeTask: approval.activeTask,
    closeDrawer: approval.closeDrawer,
    loadList: list.loadList,
  });

  return (
    <div>
      <TaskListTable list={list} onOpenTask={approval.openDrawer} />

      <ApprovalDrawer approval={approval} onOpenTaskAction={actions.openTaskAction} />

      <TaskActionModal
        action={actions.taskAction}
        form={actions.actionForm}
        submitting={actions.actionSubmitting}
        rollbackOptions={actions.rollbackOptions}
        rollbackLoading={actions.rollbackLoading}
        onCancel={actions.closeTaskAction}
        onSubmit={() => void actions.submitTaskAction()}
      />
    </div>
  );
}
