// 选路模拟弹窗：按当前画布定义与流程变量真实求值选路（不创建实例），并回放节点路径。
// 变量以 JSON 文本输入；「定位」把选中态交回页面并关闭弹窗。

import { Alert, App, Button, Input, Modal, Space, Steps, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import {
  workflowApi,
  type ProcessDefinitionSimulate,
  type ValidationIssue,
} from '../../../api/workflow';
import { toMessage } from '../errors';
import { serializeDefinition, type CanvasModel } from '../model';

/** 模拟结构错误列表 */
function SimulateIssueList({ issues }: { issues: ValidationIssue[] }) {
  return (
    <ul style={{ margin: 0, paddingLeft: 18 }}>
      {issues.map((issue, index) => (
        <li key={`${issue.code}-${index}`}>
          {issue.code}：{issue.message}
          {issue.refKey ? `（${issue.refKey}）` : ''}
        </li>
      ))}
    </ul>
  );
}

interface SimulateModalProps {
  open: boolean;
  model: CanvasModel;
  onClose: () => void;
  /** 定位到模拟路径中的节点 */
  onLocate: (key: string) => void;
}

export default function SimulateModal({ open, model, onClose, onLocate }: SimulateModalProps) {
  const { message } = App.useApp();
  const [variables, setVariables] = useState('{}');
  const [simulating, setSimulating] = useState(false);
  const [simulation, setSimulation] = useState<ProcessDefinitionSimulate | null>(null);

  // 每次打开都重置上次结果与变量文本
  useEffect(() => {
    if (open) {
      setSimulation(null);
      setVariables('{}');
    }
  }, [open]);

  /** 解析变量 JSON 文本；非法时提示并返回 null */
  const parseVariables = (): Record<string, unknown> | null => {
    const raw = variables.trim();
    if (!raw) {
      return {};
    }
    try {
      const parsed: unknown = JSON.parse(raw);
      if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
        message.warning('流程变量必须是 JSON 对象，如 {"amount": 200}');
        return null;
      }
      return parsed as Record<string, unknown>;
    } catch {
      message.error('流程变量不是合法 JSON');
      return null;
    }
  };

  const handleSimulate = async () => {
    const parsed = parseVariables();
    if (parsed === null) {
      return;
    }
    setSimulating(true);
    try {
      const result = await workflowApi.definitionSimulate({
        definitionJson: serializeDefinition(model),
        variables: parsed,
      });
      setSimulation(result);
      if (result.valid && !result.errorCode && result.reachedEnd) {
        message.success('模拟完成：已抵达结束事件');
      }
    } catch (error) {
      message.error(toMessage(error));
    } finally {
      setSimulating(false);
    }
  };

  /** 定位到模拟路径中的节点并关闭弹窗 */
  const locateStep = (key: string) => {
    onLocate(key);
    onClose();
  };

  return (
    <Modal
      title="选路模拟"
      open={open}
      width={760}
      okText={simulation ? '关闭' : '开始模拟'}
      confirmLoading={simulating}
      onOk={() => {
        if (simulation) {
          onClose();
          return;
        }
        void handleSimulate();
      }}
      onCancel={onClose}
    >
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <Typography.Text type="secondary">
          按当前画布定义与流程变量真实求值选路（不创建实例）。变量键可直接写在条件里，如 amount &gt;
          100。
        </Typography.Text>
        <Input.TextArea
          rows={5}
          placeholder='{"amount": 200}'
          value={variables}
          onChange={(event) => setVariables(event.target.value)}
        />
        {simulation && !simulation.valid ? (
          <Alert
            type="error"
            showIcon
            message={`定义结构非法（${simulation.errors.length} 个错误）`}
            description={<SimulateIssueList issues={simulation.errors} />}
          />
        ) : null}
        {simulation && simulation.valid && simulation.errorCode ? (
          <Alert
            type="error"
            showIcon
            message={`选路失败：${simulation.errorCode}`}
            description={simulation.errorMessage ?? '流程变量不足以选出分支'}
          />
        ) : null}
        {simulation && simulation.valid && !simulation.errorCode ? (
          <Alert
            type={simulation.reachedEnd ? 'success' : 'warning'}
            showIcon
            message={
              simulation.reachedEnd
                ? `已抵达结束事件（mode=${simulation.mode}）`
                : `停在节点 ${simulation.terminalNodeKey ?? '-'}`
            }
            description={`模拟路径 ${simulation.steps.length} 步`}
          />
        ) : null}
        {simulation && simulation.steps.length > 0 ? (
          <Steps
            direction="vertical"
            size="small"
            items={simulation.steps.map((step) => ({
              title: `#${step.order} ${step.nodeName || step.nodeKey}`,
              description: (
                <Space size={4} wrap>
                  <Tag>{step.nodeType ?? '-'}</Tag>
                  <Typography.Text type="secondary">{step.nodeKey}</Typography.Text>
                  {step.flowKey ? (
                    <Typography.Text type="secondary">入边 {step.flowKey}</Typography.Text>
                  ) : null}
                  {step.condition ? <Tag color="blue">{step.condition}</Tag> : null}
                  {step.isDefault ? <Tag color="orange">默认流</Tag> : null}
                  <Button size="small" type="link" onClick={() => locateStep(step.nodeKey)}>
                    定位
                  </Button>
                </Space>
              ),
            }))}
          />
        ) : null}
      </Space>
    </Modal>
  );
}
