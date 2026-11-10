// 证书模板页：定义发证口径（依据 / 有效期 / 编号前缀）+ 启停 + 删除。
//
// 权限码与 migration-5.87-lms-m4a.sql 对齐（sys:lms:cert:template）。
// 口径：模板决定发证前校验什么——「考试通过」发证时服务端会核实成绩，
// 前端在这里只负责把依据说清楚，不重复实现校验。

import { Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { hasPermission } from '../api/client';
import {
  changeCertTemplateStatus,
  createCertTemplate,
  deleteCertTemplate,
  pageCertTemplates,
  updateCertTemplate,
  type CertSource,
  type CertTemplateItem,
} from '../api/cert';

/** 发证依据展示 */
const BASIS_LABEL: Record<CertSource, string> = {
  MANUAL: '手工发放',
  COURSE: '课程完成',
  EXAM: '考试通过',
  TRAINING: '培训完成',
};

/** 状态展示 */
const STATUS_TAG: Record<string, { color: string; label: string }> = {
  ENABLED: { color: 'green', label: '启用' },
  DISABLED: { color: 'default', label: '停用' },
};

/** 表单值 */
interface TemplateValues {
  templateCode: string;
  name: string;
  category?: string;
  issueBasis: CertSource;
  validMonths: number;
  certPrefix: string;
  content?: string;
  remark?: string;
}

/** 列表行 */
interface Row extends CertTemplateItem {
  key: string;
}

export default function CertTemplatePage() {
  const [form] = Form.useForm<TemplateValues>();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<Row[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState<string | undefined>();
  const [status, setStatus] = useState<string | undefined>();
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<CertTemplateItem | null>(null);

  const canManage = hasPermission('sys:lms:cert:template');

  const load = useCallback(async (page = 1, nextKeyword = keyword, nextStatus = status) => {
    setLoading(true);
    try {
      const data = await pageCertTemplates({ keyword: nextKeyword, status: nextStatus, current: page, size: 10 });
      setRows((data.records ?? []).map((item) => ({ ...item, key: String(item.id) })));
      setTotal(data.total ?? 0);
      setCurrent(page);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load(1, undefined, undefined);
    // 首次加载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ issueBasis: 'MANUAL', validMonths: 0, certPrefix: 'CERT', category: 'GENERAL' });
    setOpen(true);
  };

  const openEdit = (row: CertTemplateItem) => {
    setEditing(row);
    form.setFieldsValue({
      templateCode: row.templateCode,
      name: row.name,
      category: row.category,
      issueBasis: row.issueBasis,
      validMonths: row.validMonths,
      certPrefix: row.certPrefix,
      content: row.content,
      remark: row.remark,
    });
    setOpen(true);
  };

  const onSubmit = async (values: TemplateValues) => {
    setSaving(true);
    try {
      if (editing) {
        await updateCertTemplate(editing.id, values);
        message.success('模板已更新');
      } else {
        await createCertTemplate(values);
        message.success('模板已创建');
      }
      setOpen(false);
      await load(editing ? current : 1, keyword, status);
    } finally {
      setSaving(false);
    }
  };

  const onToggle = async (row: Row) => {
    await changeCertTemplateStatus(row.id, row.status !== 'ENABLED');
    message.success(row.status === 'ENABLED' ? '模板已停用' : '模板已启用');
    await load(current, keyword, status);
  };

  const onDelete = async (row: Row) => {
    await deleteCertTemplate(row.id);
    message.success('模板已删除');
    await load(current, keyword, status);
  };

  return (
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <Space style={{ marginBottom: 12 }} wrap>
        <Input.Search
          placeholder="模板编码 / 名称"
          allowClear
          style={{ width: 240 }}
          onSearch={(value) => {
            setKeyword(value || undefined);
            void load(1, value || undefined, status);
          }}
        />
        <Select
          placeholder="状态"
          allowClear
          style={{ width: 120 }}
          options={Object.entries(STATUS_TAG).map(([value, item]) => ({ value, label: item.label }))}
          onChange={(value) => {
            setStatus(value || undefined);
            void load(1, keyword, value || undefined);
          }}
        />
        {canManage && (
          <Button type="primary" onClick={openCreate}>
            新建模板
          </Button>
        )}
      </Space>

      <Table<Row>
        rowKey="key"
        loading={loading}
        dataSource={rows}
        pagination={{ current, total, pageSize: 10, onChange: (page) => void load(page, keyword, status) }}
        columns={[
          { title: '模板编码', dataIndex: 'templateCode', width: 150 },
          { title: '证书名称', dataIndex: 'name' },
          {
            title: '发证依据',
            dataIndex: 'issueBasis',
            width: 110,
            render: (value: CertSource) => BASIS_LABEL[value] ?? value,
          },
          {
            title: '有效期',
            dataIndex: 'validMonths',
            width: 100,
            render: (value: number, row) => (row.longTerm ? '长期' : `${value} 个月`),
          },
          { title: '编号前缀', dataIndex: 'certPrefix', width: 100 },
          {
            title: '状态',
            dataIndex: 'status',
            width: 90,
            render: (value: string) => {
              const tag = STATUS_TAG[value] ?? { color: 'default', label: value };
              return <Tag color={tag.color}>{tag.label}</Tag>;
            },
          },
          {
            title: '操作',
            width: 190,
            render: (_, row) => (
              <Space size="small">
                {canManage && (
                  <Button size="small" type="link" onClick={() => openEdit(row)}>
                    编辑
                  </Button>
                )}
                {canManage && (
                  <Popconfirm
                    title={row.status === 'ENABLED' ? '停用后不可用于新的发证，确认？' : '确认启用该模板？'}
                    onConfirm={() => void onToggle(row)}
                  >
                    <Button size="small" type="link">
                      {row.status === 'ENABLED' ? '停用' : '启用'}
                    </Button>
                  </Popconfirm>
                )}
                {canManage && (
                  <Popconfirm title="确认删除该模板？（已发过证的模板会被拒绝）" onConfirm={() => void onDelete(row)}>
                    <Button size="small" type="link" danger>
                      删除
                    </Button>
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={editing ? '编辑证书模板' : '新建证书模板'}
        open={open}
        forceRender
        confirmLoading={saving}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        width={640}
      >
        <Form form={form} layout="vertical" onFinish={onSubmit}>
          <Form.Item name="templateCode" label="模板编码" rules={[{ required: true, message: '请输入模板编码' }]}>
            <Input placeholder="如 CERT-SAFETY（自动大写归一）" />
          </Form.Item>
          <Form.Item name="name" label="证书名称" rules={[{ required: true, message: '请输入证书名称' }]}>
            <Input placeholder="如 安全生产培训合格证" />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="category" label="类别" style={{ flex: 1 }}>
              <Input placeholder="如 SAFETY / SKILL" />
            </Form.Item>
            <Form.Item
              name="issueBasis"
              label="发证依据"
              style={{ flex: 1 }}
              extra="选择考试 / 课程 / 培训时，发证必须提供来源业务 ID 并核实条件"
            >
              <Select
                options={Object.entries(BASIS_LABEL).map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="validMonths" label="有效期（月）" style={{ flex: 1 }} extra="0 表示长期有效">
              <InputNumber min={0} max={600} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="certPrefix" label="编号前缀" style={{ flex: 1 }} extra="2–16 位大写字母或数字">
              <Input placeholder="CERT" />
            </Form.Item>
          </Space>
          <Form.Item name="content" label="证书正文">
            <Input.TextArea rows={3} maxLength={4000} placeholder="可写颁发词，编号与持证人由系统填充" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
