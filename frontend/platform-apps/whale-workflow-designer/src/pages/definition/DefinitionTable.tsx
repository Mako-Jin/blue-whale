// 流程定义列表区：查询表单 + 受控分页表格，行内提供设计 / 发起 / 校验 / BPMN 导出 / 编辑 / 删除。

import { Button, Card, Form, Input, Popconfirm, Space, Table, Tag, type TableProps } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { ProcessDefinition } from '../../api/workflow';
import type { SearchValues } from './constants';
import type { DefinitionListController } from './useDefinitionList';

interface DefinitionTableProps {
  controller: DefinitionListController;
}

export default function DefinitionTable({ controller }: DefinitionTableProps) {
  const navigate = useNavigate();
  const {
    rows,
    total,
    query,
    loading,
    searchForm,
    handleSearch,
    handleReset,
    handleTableChange,
    openCreate,
    openBpmn,
    openValidate,
    handleExport,
    openEdit,
    handleDelete,
    exportingId,
  } = controller;

  const columns: TableProps<ProcessDefinition>['columns'] = [
    { title: '编码', dataIndex: 'code', width: 180 },
    { title: '名称', dataIndex: 'name', width: 180 },
    {
      title: '版本',
      dataIndex: 'version',
      width: 80,
      render: (value: number | undefined) => value ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: number | undefined) =>
        value === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 340,
      render: (_, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => navigate(`/design/${row.id}`)}>
            设计
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() => navigate(`/start?definitionId=${row.id}`)}
          >
            发起
          </Button>
          <Button type="link" size="small" onClick={() => void openValidate(row)}>
            校验
          </Button>
          <Button
            type="link"
            size="small"
            loading={exportingId === row.id}
            onClick={() => void handleExport(row)}
          >
            BPMN导出
          </Button>
          <Button type="link" size="small" onClick={() => openEdit(row)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该流程定义？"
            okText="删除"
            cancelText="取消"
            onConfirm={() => void handleDelete(row)}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card size="small">
        <Form<SearchValues> form={searchForm} layout="inline" onFinish={handleSearch}>
          <Form.Item name="code" label="编码">
            <Input allowClear placeholder="精确匹配" style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="name" label="名称">
            <Input allowClear placeholder="模糊匹配" style={{ width: 180 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                查询
              </Button>
              <Button onClick={handleReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      <Card
        size="small"
        title="流程定义列表"
        extra={
          <Space>
            <Button onClick={openBpmn}>BPMN导入</Button>
            <Button type="primary" onClick={openCreate}>
              新建流程定义
            </Button>
          </Space>
        }
      >
        <Table<ProcessDefinition>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={rows}
          onChange={handleTableChange}
          pagination={{
            current: query.current,
            pageSize: query.size,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
          }}
        />
      </Card>
    </Space>
  );
}
