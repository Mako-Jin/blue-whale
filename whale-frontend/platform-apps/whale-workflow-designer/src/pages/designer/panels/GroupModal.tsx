// 分组 / 泳道管理弹窗：新建分组、就地改名、删除（成员回落未分组）。
// 容器矩形由画布按成员节点包围盒派生，这里只维护分组语义元数据。

import { Button, Input, Modal, Popconfirm, Space, Table, Typography, type TableProps } from 'antd';
import { useState } from 'react';
import type { CanvasGroup, CanvasModel } from '../model';

interface GroupModalProps {
  open: boolean;
  model: CanvasModel;
  onClose: () => void;
  /** 新建分组；名称为空时由上层提示并返回 false */
  onCreate: (name: string) => boolean;
  onRename: (key: string, name: string) => void;
  onDelete: (key: string) => void;
}

export default function GroupModal({
  open,
  model,
  onClose,
  onCreate,
  onRename,
  onDelete,
}: GroupModalProps) {
  const [groupName, setGroupName] = useState('');

  const handleCreate = () => {
    if (onCreate(groupName)) {
      setGroupName('');
    }
  };

  const columns: TableProps<CanvasGroup>['columns'] = [
    { title: 'key', dataIndex: 'key', width: 110 },
    {
      title: '名称',
      dataIndex: 'name',
      render: (_, record) => (
        <Input
          size="small"
          value={record.name}
          onChange={(event) => onRename(record.key, event.target.value)}
        />
      ),
    },
    {
      title: '节点数',
      key: 'count',
      width: 90,
      render: (_, record) => model.nodes.filter((node) => node.groupKey === record.key).length,
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_, record) => (
        <Popconfirm title="删除后成员将变为未分组" onConfirm={() => onDelete(record.key)}>
          <Button type="link" size="small" danger>
            删除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Modal title="分组 / 泳道" open={open} footer={null} width={520} onCancel={onClose}>
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <Space.Compact style={{ width: '100%' }}>
          <Input
            placeholder="新分组名称"
            value={groupName}
            onChange={(event) => setGroupName(event.target.value)}
            onPressEnter={handleCreate}
          />
          <Button type="primary" onClick={handleCreate}>
            新建
          </Button>
        </Space.Compact>
        <Table
          size="small"
          rowKey="key"
          pagination={false}
          dataSource={model.groups}
          columns={columns}
          locale={{ emptyText: '暂无分组' }}
        />
        <Typography.Text type="secondary">
          容器矩形按成员节点包围盒自动派生；在右侧属性面板为节点选择所属分组。
        </Typography.Text>
      </Space>
    </Modal>
  );
}
