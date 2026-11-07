// OA 用章申请页：申请编辑（印章授权校验）、用印登记、撤销与删除
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  createSealUse,
  deleteSealUse,
  listAvailableSeals,
  pageSealUses,
  registerSealUse,
  updateSealUse,
  updateSealUseStatus,
  type OaAdministrativeStatus,
  type OaSealInfo,
  type OaSealUse,
  type OaSealUseInput,
} from '../../api/administrative';
import { getCurrentUser } from '../../api/client';
import { hasPermission } from '../../utils/permission';

/** 审批状态文案 */
const STATUS_META: Record<OaAdministrativeStatus, { text: string; color: string }> = {
  PENDING: { text: '待审批', color: 'processing' },
  APPROVED: { text: '已通过', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  CANCELLED: { text: '已撤销', color: 'default' },
};

/** 用章表单值 */
interface SealFormValues {
  applicant: string;
  applicantId?: number;
  deptId?: number;
  sealId: number;
  reason: string;
  useTime?: Dayjs;
  remark?: string;
  files?: { fileId?: number; fileName: string }[];
}

/** 时间展示 */
function timeText(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

export default function SealAdminPage() {
  const { message } = App.useApp();
  const user = getCurrentUser();
  const [data, setData] = useState<OaSealUse[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<OaAdministrativeStatus | ''>('');
  const [loading, setLoading] = useState(false);
  const [seals, setSeals] = useState<OaSealInfo[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<OaSealUse | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<SealFormValues>();

  const canAdd = hasPermission('sys:oa:seal:add');
  const canEdit = hasPermission('sys:oa:seal:edit');
  const canDelete = hasPermission('sys:oa:seal:delete');
  const canRegister = hasPermission('sys:oa:seal:register');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageSealUses({
        current,
        size: 10,
        keyword: keyword.trim() || undefined,
        status,
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '用章申请查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, keyword, status, message]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    listAvailableSeals(typeof user?.userId === 'number' ? user.userId : undefined)
      .then(setSeals)
      .catch(() => setSeals([]));
  }, [user?.userId]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      applicant: user?.nickname ?? user?.username ?? '',
      applicantId: typeof user?.userId === 'number' ? user.userId : undefined,
      useTime: dayjs(),
      files: [],
    });
    setModalOpen(true);
  };

  const openEdit = (record: OaSealUse) => {
    setEditing(record);
    form.resetFields();
    form.setFieldsValue({
      applicant: record.applicant,
      applicantId: record.applicantId,
      deptId: record.deptId,
      sealId: record.sealId,
      reason: record.reason ?? '',
      useTime: record.useTime ? dayjs(record.useTime) : undefined,
      remark: record.remark,
      files: (record.files ?? []).map((file) => ({ fileId: file.fileId, fileName: file.fileName })),
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const payload: OaSealUseInput = {
      applicant: values.applicant.trim(),
      applicantId: values.applicantId,
      deptId: values.deptId,
      sealId: values.sealId,
      sealName: seals.find((item) => item.id === values.sealId)?.name,
      reason: values.reason.trim(),
      useTime: values.useTime?.format('YYYY-MM-DDTHH:mm:ss'),
      remark: values.remark?.trim() || undefined,
      files: (values.files ?? [])
        .filter((file) => file.fileName?.trim())
        .map((file) => ({ fileId: file.fileId, fileName: file.fileName.trim() })),
    };
    setSaving(true);
    try {
      if (editing) {
        await updateSealUse(editing.id, payload);
        message.success('用章申请已更新');
      } else {
        await createSealUse(payload);
        message.success('用章申请已创建并提交审批');
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '用章申请保存失败（请确认印章授权有效）');
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = async (record: OaSealUse) => {
    try {
      await updateSealUseStatus(record.id, 'CANCELLED');
      message.success('用章申请已撤销');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '用章申请撤销失败');
    }
  };

  const handleDelete = async (record: OaSealUse) => {
    try {
      await deleteSealUse(record.id);
      message.success('用章申请已删除');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '用章申请删除失败');
    }
  };

  const handleRegister = async (record: OaSealUse) => {
    try {
      await registerSealUse(record.id);
      message.success('用印已登记');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '用印登记失败');
    }
  };

  const sealName = (record: OaSealUse) =>
    record.sealName ?? seals.find((item) => item.id === record.sealId)?.name ?? record.sealId;

  const columns: TableProps<OaSealUse>['columns'] = [
    { title: '用章单号', dataIndex: 'code', key: 'code', width: 170 },
    { title: '申请人', dataIndex: 'applicant', key: 'applicant', width: 110 },
    { title: '印章', key: 'seal', width: 140, render: (_value, record) => sealName(record) },
    { title: '用章事由', dataIndex: 'reason', key: 'reason', ellipsis: true },
    {
      title: '用章时间',
      dataIndex: 'useTime',
      key: 'useTime',
      width: 170,
      render: (value: string) => timeText(value),
    },
    {
      title: '文件数',
      key: 'files',
      width: 90,
      render: (_value, record) => record.fileCount ?? record.files?.length ?? 0,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: OaAdministrativeStatus) => {
        const meta = STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '用印登记',
      dataIndex: 'registeredTime',
      key: 'registeredTime',
      width: 170,
      render: (value?: string) => (value ? timeText(value) : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 230,
      render: (_value, record) => (
        <Space size={4}>
          {record.status === 'PENDING' && canEdit ? (
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {record.status === 'PENDING' && canEdit ? (
            <Popconfirm title="确认撤销该用章申请？" onConfirm={() => void handleCancel(record)}>
              <Button type="link" size="small">
                撤销
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'PENDING' && canDelete ? (
            <Popconfirm title="确认删除该用章申请？" onConfirm={() => void handleDelete(record)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'APPROVED' && !record.registeredTime && canRegister ? (
            <Popconfirm title="确认完成用印登记？" onConfirm={() => void handleRegister(record)}>
              <Button type="link" size="small">
                用印登记
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input
          allowClear
          placeholder="单号/申请人"
          style={{ width: 200 }}
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 130 }}
          value={status || undefined}
          options={Object.entries(STATUS_META).map(([value, meta]) => ({ label: meta.text, value }))}
          onChange={(value) => {
            setStatus((value as OaAdministrativeStatus) ?? '');
            setCurrent(1);
          }}
        />
        {canAdd ? (
          <Button type="primary" onClick={openCreate}>
            新建用章申请
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<OaSealUse>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        scroll={{ x: 1400 }}
        pagination={{
          current,
          pageSize: 10,
          total,
          onChange: (page) => setCurrent(page),
        }}
      />
      <Modal
        open={modalOpen}
        title={editing ? '编辑用章申请' : '新建用章申请'}
        width={680}
        onOk={() => void handleSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="applicant" label="申请人" rules={[{ required: true, message: '请输入申请人' }]} style={{ flex: 1 }}>
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="applicantId" label="申请人ID" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} />
            </Form.Item>
            <Form.Item name="deptId" label="部门ID" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="sealId"
              label="印章"
              rules={[{ required: true, message: '请选择印章' }]}
              style={{ flex: 1 }}
              extra="仅显示当前用户已获授权的印章"
            >
              <Select
                placeholder={seals.length ? '请选择' : '暂无可用印章'}
                options={seals.map((item) => ({ label: item.name, value: item.id }))}
              />
            </Form.Item>
            <Form.Item name="useTime" label="用章时间" style={{ flex: 1 }}>
              <DatePicker showTime style={{ width: '100%' }} format="YYYY-MM-DD HH:mm" />
            </Form.Item>
          </Space>
          <Form.Item name="reason" label="用章事由" rules={[{ required: true, message: '请输入用章事由' }]}>
            <Input.TextArea rows={3} maxLength={512} showCount />
          </Form.Item>
          <Form.List name="files">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                <Space>
                  <span>用章文件</span>
                  <Button size="small" onClick={() => add()}>
                    添加文件
                  </Button>
                </Space>
                {fields.map((field) => (
                  <Space key={field.key} size="small" align="baseline">
                    <Form.Item name={[field.name, 'fileName']} rules={[{ required: true, message: '文件名称必填' }]}>
                      <Input style={{ width: 260 }} placeholder="文件名称" maxLength={128} />
                    </Form.Item>
                    <Form.Item name={[field.name, 'fileId']}>
                      <InputNumber style={{ width: 160 }} min={1} precision={0} placeholder="文件ID（选填）" />
                    </Form.Item>
                    <Button type="link" size="small" danger onClick={() => remove(field.name)}>
                      移除
                    </Button>
                  </Space>
                ))}
              </Space>
            )}
          </Form.List>
          <Form.Item name="remark" label="备注" style={{ marginTop: 12 }}>
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
