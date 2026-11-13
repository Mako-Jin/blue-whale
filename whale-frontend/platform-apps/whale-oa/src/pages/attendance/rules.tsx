// OA 考勤配置页：班次、规则、考勤组（分组绑定班次/规则/地点/设备与成员）
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tabs,
  TimePicker,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  createGroup,
  createRule,
  createShift,
  deleteGroup,
  deleteRule,
  deleteShift,
  listGroups,
  listRules,
  listShifts,
  updateGroup,
  updateRule,
  updateShift,
  type OaAttendanceGroup,
  type OaAttendanceRule,
  type OaAttendanceShift,
} from '../../api/attendance';
import { hasPermission } from '../../utils/permission';

/** 班次表单值 */
interface ShiftFormValues {
  code: string;
  name: string;
  workRange: [Dayjs, Dayjs];
  flexMinutes?: number;
  breakMinutes?: number;
  enabled: boolean;
  remark?: string;
}

/** 规则表单值 */
interface RuleFormValues {
  code: string;
  name: string;
  allowExternal: boolean;
  requireLocation: boolean;
  requireDevice: boolean;
  maxCorrectionsPerMonth?: number;
  correctionWindowDays?: number;
  enabled: boolean;
  remark?: string;
}

/** 考勤组表单值 */
interface GroupFormValues {
  code: string;
  name: string;
  shiftId: number;
  ruleId?: number;
  enabled: boolean;
  remark?: string;
  members?: { userId: number; userName?: string }[];
}

/** 时间转字符串 */
function timeText(value?: string): string {
  return value ? value.slice(0, 5) : '-';
}

export default function AttendanceRulesPage() {
  const { message } = App.useApp();
  const [shifts, setShifts] = useState<OaAttendanceShift[]>([]);
  const [rules, setRules] = useState<OaAttendanceRule[]>([]);
  const [groups, setGroups] = useState<OaAttendanceGroup[]>([]);
  const [loading, setLoading] = useState(false);

  const [shiftModal, setShiftModal] = useState(false);
  const [editingShift, setEditingShift] = useState<OaAttendanceShift | null>(null);
  const [ruleModal, setRuleModal] = useState(false);
  const [editingRule, setEditingRule] = useState<OaAttendanceRule | null>(null);
  const [groupModal, setGroupModal] = useState(false);
  const [editingGroup, setEditingGroup] = useState<OaAttendanceGroup | null>(null);
  const [saving, setSaving] = useState(false);

  const [shiftForm] = Form.useForm<ShiftFormValues>();
  const [ruleForm] = Form.useForm<RuleFormValues>();
  const [groupForm] = Form.useForm<GroupFormValues>();

  const canAdd = hasPermission('sys:oa:attendance:config:add');
  const canEdit = hasPermission('sys:oa:attendance:config:edit');
  const canDelete = hasPermission('sys:oa:attendance:config:delete');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [shiftList, ruleList, groupList] = await Promise.all([
        listShifts(),
        listRules(),
        listGroups(),
      ]);
      setShifts(shiftList);
      setRules(ruleList);
      setGroups(groupList);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '考勤配置查询失败');
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openShift = (record: OaAttendanceShift | null) => {
    setEditingShift(record);
    shiftForm.resetFields();
    shiftForm.setFieldsValue({
      code: record?.code,
      name: record?.name,
      workRange: [
        dayjs(record?.workStart ?? '09:00', 'HH:mm:ss'),
        dayjs(record?.workEnd ?? '18:00', 'HH:mm:ss'),
      ],
      flexMinutes: record?.flexMinutes ?? 0,
      breakMinutes: record?.breakMinutes ?? 0,
      enabled: record?.enabled === undefined ? true : record.enabled === 1,
      remark: record?.remark,
    });
    setShiftModal(true);
  };

  const saveShift = async () => {
    const values = await shiftForm.validateFields();
    const payload = {
      code: values.code.trim(),
      name: values.name.trim(),
      workStart: values.workRange[0].format('HH:mm:ss'),
      workEnd: values.workRange[1].format('HH:mm:ss'),
      flexMinutes: values.flexMinutes ?? 0,
      breakMinutes: values.breakMinutes ?? 0,
      enabled: values.enabled ? 1 : 0,
      remark: values.remark?.trim() || undefined,
    };
    setSaving(true);
    try {
      if (editingShift) {
        await updateShift(editingShift.id, payload);
        message.success('班次已更新');
      } else {
        await createShift(payload);
        message.success('班次已创建');
      }
      setShiftModal(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '班次保存失败');
    } finally {
      setSaving(false);
    }
  };

  const openRule = (record: OaAttendanceRule | null) => {
    setEditingRule(record);
    ruleForm.resetFields();
    ruleForm.setFieldsValue({
      code: record?.code,
      name: record?.name,
      allowExternal: record?.allowExternal === 1,
      requireLocation: record?.requireLocation === 1,
      requireDevice: record?.requireDevice === 1,
      maxCorrectionsPerMonth: record?.maxCorrectionsPerMonth ?? 3,
      correctionWindowDays: record?.correctionWindowDays ?? 3,
      enabled: record?.enabled === undefined ? true : record.enabled === 1,
      remark: record?.remark,
    });
    setRuleModal(true);
  };

  const saveRule = async () => {
    const values = await ruleForm.validateFields();
    const payload = {
      code: values.code.trim(),
      name: values.name.trim(),
      allowExternal: values.allowExternal ? 1 : 0,
      requireLocation: values.requireLocation ? 1 : 0,
      requireDevice: values.requireDevice ? 1 : 0,
      maxCorrectionsPerMonth: values.maxCorrectionsPerMonth ?? 0,
      correctionWindowDays: values.correctionWindowDays ?? 0,
      enabled: values.enabled ? 1 : 0,
      remark: values.remark?.trim() || undefined,
    };
    setSaving(true);
    try {
      if (editingRule) {
        await updateRule(editingRule.id, payload);
        message.success('规则已更新');
      } else {
        await createRule(payload);
        message.success('规则已创建');
      }
      setRuleModal(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '规则保存失败');
    } finally {
      setSaving(false);
    }
  };

  const openGroup = (record: OaAttendanceGroup | null) => {
    setEditingGroup(record);
    groupForm.resetFields();
    groupForm.setFieldsValue({
      code: record?.code,
      name: record?.name,
      shiftId: record?.shiftId,
      ruleId: record?.ruleId,
      enabled: record?.enabled === undefined ? true : record.enabled === 1,
      remark: record?.remark,
      members: record?.members ?? [],
    });
    setGroupModal(true);
  };

  const saveGroup = async () => {
    const values = await groupForm.validateFields();
    const payload = {
      code: values.code.trim(),
      name: values.name.trim(),
      shiftId: values.shiftId,
      ruleId: values.ruleId,
      enabled: values.enabled ? 1 : 0,
      remark: values.remark?.trim() || undefined,
      members: values.members ?? [],
    };
    setSaving(true);
    try {
      if (editingGroup) {
        await updateGroup(editingGroup.id, payload);
        message.success('考勤组已更新');
      } else {
        await createGroup(payload);
        message.success('考勤组已创建');
      }
      setGroupModal(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '考勤组保存失败');
    } finally {
      setSaving(false);
    }
  };

  /** 删除动作统一处理 */
  const handleDelete = async (action: () => Promise<void>, text: string) => {
    try {
      await action();
      message.success(`${text}已删除`);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const shiftColumns: TableProps<OaAttendanceShift>['columns'] = [
    { title: '编码', dataIndex: 'code', key: 'code', width: 130 },
    { title: '名称', dataIndex: 'name', key: 'name', width: 150 },
    {
      title: '上下班时间',
      key: 'work',
      width: 160,
      render: (_value, record) => `${timeText(record.workStart)} ~ ${timeText(record.workEnd)}`,
    },
    { title: '弹性(分)', dataIndex: 'flexMinutes', key: 'flexMinutes', width: 100 },
    { title: '休息(分)', dataIndex: 'breakMinutes', key: 'breakMinutes', width: 100 },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (value: number) => (value === 1 ? '是' : '否'),
    },
    { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_value, record) => (
        <Space size={4}>
          {canEdit ? (
            <Button type="link" size="small" onClick={() => openShift(record)}>
              编辑
            </Button>
          ) : null}
          {canDelete ? (
            <Popconfirm
              title="确认删除该班次？"
              onConfirm={() => void handleDelete(() => deleteShift(record.id), '班次')}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const ruleColumns: TableProps<OaAttendanceRule>['columns'] = [
    { title: '编码', dataIndex: 'code', key: 'code', width: 130 },
    { title: '名称', dataIndex: 'name', key: 'name', width: 150 },
    {
      title: '允许外勤',
      dataIndex: 'allowExternal',
      key: 'allowExternal',
      width: 100,
      render: (value: number) => (value === 1 ? '是' : '否'),
    },
    {
      title: '需定位',
      dataIndex: 'requireLocation',
      key: 'requireLocation',
      width: 100,
      render: (value: number) => (value === 1 ? '是' : '否'),
    },
    {
      title: '需设备',
      dataIndex: 'requireDevice',
      key: 'requireDevice',
      width: 100,
      render: (value: number) => (value === 1 ? '是' : '否'),
    },
    {
      title: '月补卡上限',
      dataIndex: 'maxCorrectionsPerMonth',
      key: 'maxCorrectionsPerMonth',
      width: 110,
    },
    {
      title: '补卡窗口(天)',
      dataIndex: 'correctionWindowDays',
      key: 'correctionWindowDays',
      width: 120,
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (value: number) => (value === 1 ? '是' : '否'),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_value, record) => (
        <Space size={4}>
          {canEdit ? (
            <Button type="link" size="small" onClick={() => openRule(record)}>
              编辑
            </Button>
          ) : null}
          {canDelete ? (
            <Popconfirm
              title="确认删除该规则？"
              onConfirm={() => void handleDelete(() => deleteRule(record.id), '规则')}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const groupColumns: TableProps<OaAttendanceGroup>['columns'] = [
    { title: '编码', dataIndex: 'code', key: 'code', width: 130 },
    { title: '名称', dataIndex: 'name', key: 'name', width: 150 },
    {
      title: '班次',
      dataIndex: 'shiftId',
      key: 'shiftId',
      width: 140,
      render: (value: number) => shifts.find((shift) => shift.id === value)?.name ?? value,
    },
    {
      title: '规则',
      dataIndex: 'ruleId',
      key: 'ruleId',
      width: 140,
      render: (value?: number) =>
        value ? (rules.find((rule) => rule.id === value)?.name ?? value) : '-',
    },
    {
      title: '成员',
      key: 'members',
      width: 160,
      render: (_value, record) =>
        (record.members ?? []).map((member) => member.userName || member.userId).join('、') || '-',
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (value: number) => (value === 1 ? '是' : '否'),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_value, record) => (
        <Space size={4}>
          {canEdit ? (
            <Button type="link" size="small" onClick={() => openGroup(record)}>
              编辑
            </Button>
          ) : null}
          {canDelete ? (
            <Popconfirm
              title="确认删除该考勤组？"
              onConfirm={() => void handleDelete(() => deleteGroup(record.id), '考勤组')}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Tabs
        items={[
          {
            key: 'shifts',
            label: '班次',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space>
                  {canAdd ? (
                    <Button type="primary" onClick={() => openShift(null)}>
                      新增班次
                    </Button>
                  ) : null}
                  <Button loading={loading} onClick={() => void load()}>
                    刷新
                  </Button>
                </Space>
                <Table<OaAttendanceShift>
                  rowKey="id"
                  loading={loading}
                  columns={shiftColumns}
                  dataSource={shifts}
                  scroll={{ x: 1100 }}
                  pagination={false}
                />
              </Space>
            ),
          },
          {
            key: 'rules',
            label: '考勤规则',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space>
                  {canAdd ? (
                    <Button type="primary" onClick={() => openRule(null)}>
                      新增规则
                    </Button>
                  ) : null}
                  <Button loading={loading} onClick={() => void load()}>
                    刷新
                  </Button>
                </Space>
                <Table<OaAttendanceRule>
                  rowKey="id"
                  loading={loading}
                  columns={ruleColumns}
                  dataSource={rules}
                  scroll={{ x: 1100 }}
                  pagination={false}
                />
              </Space>
            ),
          },
          {
            key: 'groups',
            label: '考勤组',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space>
                  {canAdd ? (
                    <Button type="primary" onClick={() => openGroup(null)}>
                      新增考勤组
                    </Button>
                  ) : null}
                  <Button loading={loading} onClick={() => void load()}>
                    刷新
                  </Button>
                </Space>
                <Table<OaAttendanceGroup>
                  rowKey="id"
                  loading={loading}
                  columns={groupColumns}
                  dataSource={groups}
                  scroll={{ x: 1000 }}
                  pagination={false}
                />
              </Space>
            ),
          },
        ]}
      />
      <Modal
        open={shiftModal}
        title={editingShift ? '编辑班次' : '新增班次'}
        onOk={() => void saveShift()}
        confirmLoading={saving}
        onCancel={() => setShiftModal(false)}
        destroyOnClose
      >
        <Form form={shiftForm} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="code"
              label="班次编码"
              rules={[{ required: true, message: '请输入班次编码' }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={32} />
            </Form.Item>
            <Form.Item
              name="name"
              label="班次名称"
              rules={[{ required: true, message: '请输入班次名称' }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={64} />
            </Form.Item>
          </Space>
          <Form.Item
            name="workRange"
            label="上下班时间"
            rules={[{ required: true, message: '请选择上下班时间' }]}
          >
            <TimePicker.RangePicker style={{ width: '100%' }} format="HH:mm" />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="flexMinutes" label="弹性(分钟)" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={0} precision={0} />
            </Form.Item>
            <Form.Item name="breakMinutes" label="休息(分钟)" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={0} precision={0} />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked" style={{ flex: 1 }}>
              <Switch />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={ruleModal}
        title={editingRule ? '编辑规则' : '新增规则'}
        onOk={() => void saveRule()}
        confirmLoading={saving}
        onCancel={() => setRuleModal(false)}
        destroyOnClose
      >
        <Form form={ruleForm} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="code"
              label="规则编码"
              rules={[{ required: true, message: '请输入规则编码' }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={32} />
            </Form.Item>
            <Form.Item
              name="name"
              label="规则名称"
              rules={[{ required: true, message: '请输入规则名称' }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={64} />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="allowExternal"
              label="允许外勤"
              valuePropName="checked"
              style={{ flex: 1 }}
            >
              <Switch />
            </Form.Item>
            <Form.Item
              name="requireLocation"
              label="需定位"
              valuePropName="checked"
              style={{ flex: 1 }}
            >
              <Switch />
            </Form.Item>
            <Form.Item
              name="requireDevice"
              label="需设备"
              valuePropName="checked"
              style={{ flex: 1 }}
            >
              <Switch />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked" style={{ flex: 1 }}>
              <Switch />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="maxCorrectionsPerMonth" label="月补卡上限" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={0} precision={0} />
            </Form.Item>
            <Form.Item name="correctionWindowDays" label="补卡窗口(天)" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={0} precision={0} />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={groupModal}
        title={editingGroup ? '编辑考勤组' : '新增考勤组'}
        width={640}
        onOk={() => void saveGroup()}
        confirmLoading={saving}
        onCancel={() => setGroupModal(false)}
        destroyOnClose
      >
        <Form form={groupForm} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="code"
              label="考勤组编码"
              rules={[{ required: true, message: '请输入考勤组编码' }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={32} />
            </Form.Item>
            <Form.Item
              name="name"
              label="考勤组名称"
              rules={[{ required: true, message: '请输入考勤组名称' }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={64} />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="shiftId"
              label="班次"
              rules={[{ required: true, message: '请选择班次' }]}
              style={{ flex: 1 }}
            >
              <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="班次ID" />
            </Form.Item>
            <Form.Item name="ruleId" label="规则ID" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="选填" />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked" style={{ flex: 1 }}>
              <Switch />
            </Form.Item>
          </Space>
          <Form.List name="members">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size="small" style={{ width: '100%' }}>
                <Space>
                  <span>成员</span>
                  <Button size="small" onClick={() => add()}>
                    添加成员
                  </Button>
                </Space>
                {fields.map((field) => (
                  <Space key={field.key} size="small" align="baseline">
                    <Form.Item
                      name={[field.name, 'userId']}
                      rules={[{ required: true, message: '用户ID' }]}
                    >
                      <InputNumber
                        style={{ width: 140 }}
                        min={1}
                        precision={0}
                        placeholder="用户ID"
                      />
                    </Form.Item>
                    <Form.Item name={[field.name, 'userName']}>
                      <Input style={{ width: 180 }} placeholder="姓名" maxLength={64} />
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
    </>
  );
}
