// 部门管理页 - 树形表格 + 新增 / 编辑 / 删除
// 操作按钮均按权限码渲染（AuthButton / usePermission），无权限自动隐藏

import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  type TableProps,
} from 'antd';
import { useEffect, useState } from 'react';
import { AuthButton, FormModal, usePermission } from '@bluewhale/whale-ui';
import {
  createDept,
  deleteDept,
  treeDepts,
  updateDept,
  type DeptPayload,
  type DeptVO,
} from '../../api/dept';
import { useI18n } from '../../i18n';

interface DeptFormValues {
  /** 后端 Long 序列化为字符串 */
  parentId?: string;
  deptName: string;
  sort?: number;
  leader?: string;
}

/** 把部门树拍平，供上级部门下拉选择 */
function flattenDepts(nodes: DeptVO[], acc: DeptVO[] = []): DeptVO[] {
  nodes.forEach((node) => {
    acc.push(node);
    if (node.children?.length) {
      flattenDepts(node.children, acc);
    }
  });
  return acc;
}

export default function DeptList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const { t } = useI18n();

  const [depts, setDepts] = useState<DeptVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<DeptVO | null>(null);
  const [editorForm] = Form.useForm<DeptFormValues>();

  // 每次成功操作后重新拉取整棵树
  const load = async () => {
    setLoading(true);
    try {
      setDepts(await treeDepts());
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('dept.msg.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleDelete = async (row: DeptVO) => {
    try {
      await deleteDept(row.id);
      message.success(t('dept.msg.deleted'));
      void load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('dept.msg.deleteFailed'));
    }
  };

  const handleSubmit = async (values: DeptFormValues) => {
    try {
      const payload: DeptPayload = { ...values, parentId: values.parentId ?? '0' };
      if (editRecord) {
        await updateDept(editRecord.id, payload);
        message.success(t('dept.msg.updated'));
      } else {
        await createDept(payload);
        message.success(t('dept.msg.created'));
      }
      setEditorOpen(false);
      void load();
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : editRecord
            ? t('dept.msg.updateFailed')
            : t('dept.msg.createFailed'),
      );
      throw error;
    }
  };

  const parentOptions = flattenDepts(depts)
    .filter((dept) => dept.id !== editRecord?.id)
    .map((dept) => ({ value: dept.id, label: dept.deptName }));

  const columns: TableProps<DeptVO>['columns'] = [
    { title: t('dept.col.name'), dataIndex: 'deptName', width: 240 },
    {
      title: t('dept.col.leader'),
      dataIndex: 'leader',
      width: 140,
      render: (value: string | undefined) => value ?? '-',
    },
    { title: t('dept.col.sort'), dataIndex: 'sort', width: 80 },
    {
      title: t('dept.col.createTime'),
      dataIndex: 'createTime',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('dept.col.action'),
      key: 'action',
      width: 160,
      render: (_, row) => (
        <Space size={4}>
          <AuthButton
            perm="sys:dept:edit"
            type="link"
            size="small"
            onClick={() => {
              setEditRecord(row);
              setEditorOpen(true);
            }}
          >
            {t('dept.action.edit')}
          </AuthButton>
          {has('sys:dept:delete') && (
            <Popconfirm title={t('dept.confirm.delete')} onConfirm={() => void handleDelete(row)}>
              <Button type="link" size="small" danger>
                {t('dept.action.delete')}
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Table<DeptVO>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={depts}
        pagination={false}
        expandable={{ defaultExpandAllRows: true }}
        title={() => (
          <Space>
            <span>{t('dept.list.title')}</span>
            <AuthButton
              perm="sys:dept:add"
              type="primary"
              size="small"
              onClick={() => {
                setEditRecord(null);
                setEditorOpen(true);
              }}
            >
              {t('dept.action.create')}
            </AuthButton>
          </Space>
        )}
      />

      <FormModal<DeptFormValues>
        open={editorOpen}
        title={
          editRecord
            ? t('dept.form.editTitle', { name: editRecord.deptName })
            : t('dept.form.createTitle')
        }
        form={editorForm}
        initialValues={editRecord ?? undefined}
        onOk={handleSubmit}
        onCancel={() => setEditorOpen(false)}
      >
        <Form.Item name="parentId" label={t('dept.form.parent')}>
          <Select
            allowClear
            options={parentOptions}
            placeholder={t('dept.form.parentPlaceholder')}
            showSearch
            optionFilterProp="label"
          />
        </Form.Item>
        <Form.Item
          name="deptName"
          label={t('dept.form.name')}
          rules={[{ required: true, message: t('dept.form.nameRequired') }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="sort" label={t('dept.form.sort')} initialValue={1}>
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="leader" label={t('dept.form.leader')}>
          <Input />
        </Form.Item>
      </FormModal>
    </>
  );
}
