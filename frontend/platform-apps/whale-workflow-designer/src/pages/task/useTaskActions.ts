// 任务办理动作：转办 / 委派 / 回退 / 处理完成 / 加签，入参经弹窗表单收集后提交。
// 回退目标节点候选来自流程定义中的审批节点（加载失败时仍可手填 key）。

import { App, Form, type FormInstance } from 'antd';
import { useState } from 'react';
import { workflowApi, type ProcessTask } from '../../api/workflow';
import { parseDefinitionModel } from '../start/model';
import { parseAssignees, type ActionFormValues, type TaskAction } from './constants';

interface UseTaskActionsOptions {
  activeTask: ProcessTask | null;
  closeDrawer: () => void;
  /** 办理完成后刷新待办列表 */
  loadList: () => Promise<void>;
}

export interface TaskActionController {
  taskAction: TaskAction | null;
  actionForm: FormInstance<ActionFormValues>;
  actionSubmitting: boolean;
  /** 回退目标节点候选（来自流程定义审批节点） */
  rollbackOptions: { value: string; label: string }[];
  rollbackLoading: boolean;
  /** 打开办理弹窗（转办 / 委派 / 回退 / 处理完成 / 加签） */
  openTaskAction: (next: TaskAction) => void;
  submitTaskAction: () => Promise<void>;
  closeTaskAction: () => void;
}

export function useTaskActions({
  activeTask,
  closeDrawer,
  loadList,
}: UseTaskActionsOptions): TaskActionController {
  const { message } = App.useApp();
  const [taskAction, setTaskAction] = useState<TaskAction | null>(null);
  const [actionForm] = Form.useForm<ActionFormValues>();
  const [actionSubmitting, setActionSubmitting] = useState(false);
  const [rollbackOptions, setRollbackOptions] = useState<{ value: string; label: string }[]>([]);
  const [rollbackLoading, setRollbackLoading] = useState(false);

  /** 拉取回退候选节点：实例详情 → 定义详情 → 审批节点（USER_TASK / COUNTER_SIGN） */
  const loadRollbackNodes = async (task: ProcessTask) => {
    setRollbackLoading(true);
    try {
      const instance = await workflowApi.instanceDetail(task.instanceId);
      const definition = await workflowApi.definitionDetail(instance.definitionId);
      const parsed = parseDefinitionModel(definition.definitionJson);
      const options = (parsed?.nodes ?? [])
        .filter((node) => node.type === 'USER_TASK' || node.type === 'COUNTER_SIGN')
        .map((node) => ({ value: node.key, label: `${node.name || node.key}（${node.key}）` }));
      setRollbackOptions(options);
    } catch {
      // 定义不可达时清空候选，弹窗仍允许手填节点 key
      setRollbackOptions([]);
    } finally {
      setRollbackLoading(false);
    }
  };

  /** 打开办理弹窗（转办 / 委派 / 回退 / 处理完成） */
  const openTaskAction = (next: TaskAction) => {
    if (!activeTask) {
      return;
    }
    actionForm.resetFields();
    setTaskAction(next);
    if (next === 'rollback') {
      setRollbackOptions([]);
      void loadRollbackNodes(activeTask);
    }
  };

  /** 关闭办理弹窗 */
  const closeTaskAction = () => {
    setTaskAction(null);
  };

  /** 提交办理弹窗 */
  const submitTaskAction = async () => {
    if (!activeTask || !taskAction) {
      return;
    }
    let values: ActionFormValues;
    try {
      values = await actionForm.validateFields();
    } catch {
      return;
    }
    setActionSubmitting(true);
    try {
      switch (taskAction) {
        case 'transfer':
          await workflowApi.taskTransfer(activeTask.id, {
            targetUser: values.targetUser?.trim() ?? '',
          });
          message.success('已转办');
          break;
        case 'delegate':
          await workflowApi.taskDelegate(activeTask.id, {
            targetUser: values.targetUser?.trim() ?? '',
          });
          message.success('已委派');
          break;
        case 'rollback':
          await workflowApi.taskRollback(activeTask.id, {
            targetNodeKey: values.targetNodeKey?.trim() ?? '',
          });
          message.success('已回退');
          break;
        case 'resolve':
          await workflowApi.taskResolve(activeTask.id, {
            comment: values.comment?.trim() || undefined,
          });
          message.success('已处理完成，任务已交回委派人');
          break;
        case 'addSign': {
          const assignees = parseAssignees(values.assignees);
          if (assignees.length === 0) {
            message.warning('请输入加签人');
            return;
          }
          await workflowApi.taskAddSign(activeTask.id, { assignees });
          message.success('已加签');
          break;
        }
        default:
          break;
      }
      setTaskAction(null);
      closeDrawer();
      await loadList();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setActionSubmitting(false);
    }
  };

  return {
    taskAction,
    actionForm,
    actionSubmitting,
    rollbackOptions,
    rollbackLoading,
    openTaskAction,
    submitTaskAction,
    closeTaskAction,
  };
}
