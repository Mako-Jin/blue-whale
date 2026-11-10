// 定义元信息栏：code / name 受控输入 + 已保存 ID（或「未保存」标记）。

import { Input, Space, Tag, Typography } from 'antd';
import type { Id } from '../../../api/workflow';

interface DefinitionMetaProps {
  code: string;
  name: string;
  onCodeChange: (value: string) => void;
  onNameChange: (value: string) => void;
  definitionId: Id | null;
}

export default function DefinitionMeta({
  code,
  name,
  onCodeChange,
  onNameChange,
  definitionId,
}: DefinitionMetaProps) {
  return (
    <Space style={{ marginBottom: 8 }} wrap>
      <Input
        addonBefore="code"
        style={{ width: 240 }}
        placeholder="流程编码"
        value={code}
        onChange={(event) => onCodeChange(event.target.value)}
      />
      <Input
        addonBefore="name"
        style={{ width: 280 }}
        placeholder="流程名称"
        value={name}
        onChange={(event) => onNameChange(event.target.value)}
      />
      {definitionId ? (
        <Typography.Text type="secondary">ID：{definitionId}</Typography.Text>
      ) : (
        <Tag color="orange">未保存</Tag>
      )}
    </Space>
  );
}
