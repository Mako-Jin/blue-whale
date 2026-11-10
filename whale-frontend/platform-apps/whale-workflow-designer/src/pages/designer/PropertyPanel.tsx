// 右侧属性面板：编辑选中节点或流转。
// 节点：key / name / type / assignee（仅 USER_TASK）/ 绑定表单（仅 USER_TASK）/
//       assignees + counterSignPolicy（仅 COUNTER_SIGN）/ 删除 / 连线起点；
// 流转：condition（SpEL 文本）+ isDefault（仅源节点为 EXCLUSIVE_GATEWAY）/ 删除。

import {
  Alert,
  Button,
  Empty,
  Input,
  InputNumber,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd';
import { useEffect, useState, type ReactNode } from 'react';
import FormPicker from './FormPicker';
import {
  NODE_TYPE_COLORS,
  NODE_TYPE_LABELS,
  NODE_TYPE_OPTIONS,
  findNode,
  type CanvasFlow,
  type CanvasModel,
  type CanvasNode,
  type Selection,
} from './model';

/** 简单表单项：标签 + 控件（可选提示文案） */
function Item({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <div style={{ marginBottom: 12 }}>
      <div style={{ marginBottom: 4, color: '#666' }}>{label}</div>
      {children}
      {hint ? (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {hint}
        </Typography.Text>
      ) : null}
    </div>
  );
}

/** 会签策略下拉项（比例项在选中时展开百分比输入） */
const COUNTER_SIGN_POLICY_OPTIONS = [
  { value: 'ALL', label: '全部同意（ALL）' },
  { value: 'ANY', label: '任一同意（ANY）' },
  { value: 'RATIO', label: '按比例（RATIO:n）' },
];

type CounterSignPolicyKind = 'ALL' | 'ANY' | 'RATIO';

/** 策略文本 → 下拉值（RATIO:n 归一为 RATIO，缺省/非法按 ALL） */
function toPolicyKind(policy: string): CounterSignPolicyKind {
  if (policy.startsWith('RATIO:')) {
    return 'RATIO';
  }
  return policy === 'ANY' ? 'ANY' : 'ALL';
}

/** 从 RATIO:n 中取百分比（缺省/非法回落 50，夹取 1~100） */
function toPolicyRatio(policy: string): number {
  const matched = /^RATIO:(\d{1,3})$/.exec(policy.trim().toUpperCase());
  const parsed = matched ? Number(matched[1]) : 50;
  return Math.min(100, Math.max(1, Number.isFinite(parsed) ? parsed : 50));
}

/** 会签人文本 → 账号数组：换行或中英文逗号分隔，去空去重 */
function parseAssignees(text: string): string[] {
  return Array.from(
    new Set(
      text
        .split(/[,，\n]/)
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  );
}

interface PropertyPanelProps {
  model: CanvasModel;
  selection: Selection | null;
  linkFrom: string | null;
  onChangeNode: (key: string, patch: Partial<CanvasNode>) => void;
  /** 重命名节点 key（同步改流转 from/to）；非法或重复时返回 false 并保持原值 */
  onRenameNode: (oldKey: string, newKey: string) => boolean;
  onDeleteNode: (key: string) => void;
  onChangeFlow: (key: string, patch: Partial<CanvasFlow>) => void;
  onDeleteFlow: (key: string) => void;
  onStartLink: (key: string) => void;
  onCancelLink: () => void;
}

export default function PropertyPanel({
  model,
  selection,
  linkFrom,
  onChangeNode,
  onRenameNode,
  onDeleteNode,
  onChangeFlow,
  onDeleteFlow,
  onStartLink,
  onCancelLink,
}: PropertyPanelProps) {
  const node = selection?.kind === 'node' ? findNode(model, selection.key) : undefined;
  const flow =
    selection?.kind === 'flow' ? model.flows.find((item) => item.key === selection.key) : undefined;

  // key 编辑走本地草稿，避免每输入一个字符就重建流转引用
  const [keyDraft, setKeyDraft] = useState('');
  useEffect(() => {
    setKeyDraft(node?.key ?? '');
  }, [node?.key]);

  // 会签人走本地草稿，失焦时统一解析为 string[]，避免输入逗号/换行时被立即重排
  const assigneesText = node?.assignees?.join('\n') ?? '';
  const [assigneesDraft, setAssigneesDraft] = useState('');
  useEffect(() => {
    setAssigneesDraft(assigneesText);
  }, [node?.key, assigneesText]);

  if (node) {
    const isUserTask = node.type === 'USER_TASK';
    const isCounterSign = node.type === 'COUNTER_SIGN';
    const commitKey = () => {
      if (keyDraft !== node.key && !onRenameNode(node.key, keyDraft)) {
        setKeyDraft(node.key);
      }
    };
    return (
      <div>
        <Space style={{ marginBottom: 8 }}>
          <Tag color={NODE_TYPE_COLORS[node.type]}>{NODE_TYPE_LABELS[node.type]}</Tag>
          <Typography.Text type="secondary">{node.key}</Typography.Text>
        </Space>

        <Item label="节点 key" hint="仅限字母 / 数字 / _ / -，1~64 位；失焦或回车提交">
          <Input
            value={keyDraft}
            onChange={(event) => setKeyDraft(event.target.value)}
            onBlur={commitKey}
            onPressEnter={commitKey}
          />
        </Item>

        <Item label="名称">
          <Input
            value={node.name}
            onChange={(event) => onChangeNode(node.key, { name: event.target.value })}
          />
        </Item>

        <Item label="类型">
          <Select
            style={{ width: '100%' }}
            value={node.type}
            options={NODE_TYPE_OPTIONS}
            onChange={(value) => onChangeNode(node.key, { type: value })}
          />
        </Item>

        <Item label="所属分组" hint="分组容器由成员节点位置自动包围；在顶栏「分组」中管理">
          <Select
            allowClear
            style={{ width: '100%' }}
            placeholder="未分组"
            value={node.groupKey ?? undefined}
            options={model.groups.map((group) => ({
              value: group.key,
              label: group.name || group.key,
            }))}
            onChange={(value) => onChangeNode(node.key, { groupKey: value ?? null })}
          />
        </Item>

        {isUserTask ? (
          <>
            <Item label="审批人 (assignee)" hint="USER_TASK 必填，账号名">
              <Input
                value={node.assignee}
                onChange={(event) => onChangeNode(node.key, { assignee: event.target.value })}
              />
            </Item>
            <Item label="绑定表单" hint="选中后同时回填 formId 与 formCode">
              <FormPicker value={node.formId} onChange={(patch) => onChangeNode(node.key, patch)} />
            </Item>
          </>
        ) : null}

        {isCounterSign ? (
          <>
            <Item
              label="会签人 (assignees)"
              hint="账号列表，换行或中英文逗号分隔；COUNTER_SIGN 必填"
            >
              <Input.TextArea
                rows={3}
                value={assigneesDraft}
                placeholder={'zhangsan\nlisi'}
                onChange={(event) => setAssigneesDraft(event.target.value)}
                onBlur={() => onChangeNode(node.key, { assignees: parseAssignees(assigneesDraft) })}
              />
            </Item>
            <Item
              label="会签策略 (counterSignPolicy)"
              hint="ALL：全部同意；ANY：任一同意；RATIO:n：同意比例达到 n%"
            >
              <Select
                style={{ width: '100%' }}
                value={toPolicyKind(node.counterSignPolicy)}
                options={COUNTER_SIGN_POLICY_OPTIONS}
                onChange={(kind: CounterSignPolicyKind) => {
                  if (kind === 'RATIO') {
                    onChangeNode(node.key, {
                      counterSignPolicy: `RATIO:${toPolicyRatio(node.counterSignPolicy)}`,
                    });
                  } else {
                    onChangeNode(node.key, { counterSignPolicy: kind });
                  }
                }}
              />
              {toPolicyKind(node.counterSignPolicy) === 'RATIO' ? (
                <InputNumber
                  style={{ width: '100%', marginTop: 8 }}
                  min={1}
                  max={100}
                  precision={0}
                  addonAfter="%"
                  value={toPolicyRatio(node.counterSignPolicy)}
                  onChange={(value: number | null) => {
                    if (typeof value !== 'number') {
                      return;
                    }
                    const ratio = Math.min(100, Math.max(1, Math.round(value)));
                    onChangeNode(node.key, { counterSignPolicy: `RATIO:${ratio}` });
                  }}
                />
              ) : null}
            </Item>
          </>
        ) : null}

        <Space style={{ marginTop: 8 }} wrap>
          {linkFrom === node.key ? (
            <Button onClick={onCancelLink}>取消连线</Button>
          ) : (
            <Button type="primary" onClick={() => onStartLink(node.key)}>
              以此为起点连线
            </Button>
          )}
          <Button danger onClick={() => onDeleteNode(node.key)}>
            删除节点
          </Button>
        </Space>
      </div>
    );
  }

  if (flow) {
    const from = findNode(model, flow.from);
    const gatewayDefaultEditable = from?.type === 'EXCLUSIVE_GATEWAY';
    return (
      <div>
        <Space style={{ marginBottom: 8 }} wrap>
          <Tag color="blue">{flow.key}</Tag>
          <Typography.Text type="secondary">
            {from?.name ?? flow.from} → {findNode(model, flow.to)?.name ?? flow.to}
          </Typography.Text>
        </Space>

        <Item
          label="条件表达式 (condition)"
          hint="仅支持比较 / 逻辑运算，变量用 #varName（如 #amount > 1000、#dept == 'IT'）；留空表示无条件默认流。"
        >
          <Input.TextArea
            rows={3}
            value={flow.condition}
            placeholder="#amount > 1000"
            onChange={(event) => onChangeFlow(flow.key, { condition: event.target.value })}
          />
        </Item>

        {gatewayDefaultEditable ? (
          <Item label="默认流 (isDefault)" hint="同源仅允许一条默认流，设置后自动取消其它默认">
            <Switch
              checked={flow.isDefault}
              onChange={(checked) => onChangeFlow(flow.key, { isDefault: checked })}
            />
          </Item>
        ) : (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message="仅排他网关的出流可设为默认流"
          />
        )}

        <Button danger onClick={() => onDeleteFlow(flow.key)}>
          删除连线
        </Button>
      </div>
    );
  }

  return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选中节点或连线以编辑属性" />;
}
