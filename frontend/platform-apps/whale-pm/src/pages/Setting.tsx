// 项目设置：成员、状态配置与流转配置

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Switch,
  Table,
  Tabs,
  Tag,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  memberApi,
  workflowApi,
  type ProjectMember,
  type Status,
  type Transition,
} from '../api/pm';
import { CATEGORY_OPTIONS, ROLE_OPTIONS, hasPermission, statusColor } from '../utils';
import { useWorkspace } from '../workspace-context';

export default function Setting() {
  const { project } = useWorkspace();
  const { message } = App.useApp();
  const [members, setMembers] = useState<ProjectMember[]>([]);
  const [statuses, setStatuses] = useState<Status[]>([]);
  const [transitions, setTransitions] = useState<Transition[]>([]);
  const [memberForm] = Form.useForm();
  const [statusForm] = Form.useForm();
  const [transitionForm] = Form.useForm();
  const [memberModalOpen, setMemberModalOpen] = useState(false);
  const [statusModalOpen, setStatusModalOpen] = useState(false);
  const [transitionModalOpen, setTransitionModalOpen] = useState(false);

  const load = useCallback(async () => {
    try {
      const [memberList, statusList, transitionList] = await Promise.all([
        memberApi.list(project.id),
        workflowApi.statuses(project.id),
        workflowApi.transitions(project.id),
      ]);
      setMembers(memberList);
      setStatuses(statusList);
      setTransitions(transitionList);
    } catch (error) {
      message.error((error as Error).message);
    }
  }, [project.id, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const addMember = async () => {
    const values = await memberForm.validateFields();
    try {
      await memberApi.add(project.id, { userId: Number(values.userId), projectRole: values.projectRole });
      message.success('成员已添加');
      setMemberModalOpen(false);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const changeRole = async (member: ProjectMember, projectRole: string) => {
    try {
      await memberApi.updateRole(member.id, projectRole);
      message.success('角色已更新');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const removeMember = async (member: ProjectMember) => {
    try {
      await memberApi.remove(member.id);
      message.success('成员已移除');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const addStatus = async () => {
    const values = await statusForm.validateFields();
    try {
      await workflowApi.addStatus(project.id, values);
      message.success('状态已新增');
      setStatusModalOpen(false);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const removeStatus = async (status: Status) => {
    try {
      await workflowApi.removeStatus(status.id);
      message.success('状态已删除');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const addTransition = async () => {
    const values = await transitionForm.validateFields();
    try {
      await workflowApi.addTransition(project.id, {
        fromStatusId: values.fromStatusId,
        toStatusId: values.toStatusId,
        name: values.name,
      });
      message.success('流转已新增');
      setTransitionModalOpen(false);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const removeTransition = async (transition: Transition) => {
    try {
      await workflowApi.removeTransition(transition.id);
      message.success('流转已删除');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const memberColumns: ColumnsType<ProjectMember> = [
    { title: '用户ID', dataIndex: 'userId', width: 120 },
    {
      title: '项目角色',
      dataIndex: 'projectRole',
      width: 180,
      render: (value: string, record) =>
        hasPermission('sys:pm:member:edit') ? (
          <Select
            size="small"
            value={value}
            options={ROLE_OPTIONS}
            style={{ width: 120 }}
            onChange={(role) => void changeRole(record, role)}
          />
        ) : (
          <Tag>{value}</Tag>
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        hasPermission('sys:pm:member:delete') && (
          <Popconfirm title="移除该成员？" onConfirm={() => void removeMember(record)}>
            <Button type="link" size="small" danger>
              移除
            </Button>
          </Popconfirm>
        ),
    },
  ];

  const statusColumns: ColumnsType<Status> = [
    { title: '编码', dataIndex: 'code', width: 140 },
    { title: '名称', dataIndex: 'name', width: 140 },
    {
      title: '分类',
      dataIndex: 'category',
      width: 120,
      render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag>,
    },
    {
      title: '默认',
      dataIndex: 'isDefault',
      width: 80,
      render: (value?: boolean) => (value ? <Tag color="blue">是</Tag> : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        hasPermission('sys:pm:status:delete') && !record.isDefault && (
          <Popconfirm title="删除该状态？" onConfirm={() => void removeStatus(record)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        ),
    },
  ];

  const statusName = (id: string) => statuses.find((status) => status.id === id)?.name ?? id;

  const transitionColumns: ColumnsType<Transition> = [
    { title: '动作', dataIndex: 'name', width: 140 },
    { title: '源状态', dataIndex: 'fromStatusId', width: 140, render: (value: string) => statusName(value) },
    { title: '目标状态', dataIndex: 'toStatusId', width: 140, render: (value: string) => statusName(value) },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        hasPermission('sys:pm:transition:delete') && (
          <Popconfirm title="删除该流转？" onConfirm={() => void removeTransition(record)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        ),
    },
  ];

  return (
    <Card>
      <Tabs
        items={[
          {
            key: 'member',
            label: '成员',
            children: (
              <div>
                {hasPermission('sys:pm:member:add') && (
                  <Button
                    type="primary"
                    style={{ marginBottom: 12 }}
                    onClick={() => {
                      memberForm.resetFields();
                      memberForm.setFieldsValue({ projectRole: 'MEMBER' });
                      setMemberModalOpen(true);
                    }}
                  >
                    添加成员
                  </Button>
                )}
                <Table rowKey="id" size="small" columns={memberColumns} dataSource={members} pagination={false} />
              </div>
            ),
          },
          {
            key: 'status',
            label: '状态',
            children: (
              <div>
                {hasPermission('sys:pm:status:add') && (
                  <Button
                    type="primary"
                    style={{ marginBottom: 12 }}
                    onClick={() => {
                      statusForm.resetFields();
                      statusForm.setFieldsValue({ category: 'TODO', sortNo: statuses.length + 1 });
                      setStatusModalOpen(true);
                    }}
                  >
                    新增状态
                  </Button>
                )}
                <Table rowKey="id" size="small" columns={statusColumns} dataSource={statuses} pagination={false} />
              </div>
            ),
          },
          {
            key: 'transition',
            label: '流转',
            children: (
              <div>
                {hasPermission('sys:pm:transition:add') && (
                  <Button
                    type="primary"
                    style={{ marginBottom: 12 }}
                    onClick={() => {
                      transitionForm.resetFields();
                      setTransitionModalOpen(true);
                    }}
                  >
                    新增流转
                  </Button>
                )}
                <Table
                  rowKey="id"
                  size="small"
                  columns={transitionColumns}
                  dataSource={transitions}
                  pagination={false}
                />
              </div>
            ),
          },
        ]}
      />

      <Modal
        open={memberModalOpen}
        title="添加成员"
        onCancel={() => setMemberModalOpen(false)}
        onOk={() => void addMember()}
        okText="添加"
        cancelText="取消"
      >
        <Form form={memberForm} layout="vertical">
          <Form.Item name="userId" label="用户ID" rules={[{ required: true, message: '请输入用户ID' }]}>
            <Input placeholder="数字用户ID" />
          </Form.Item>
          <Form.Item name="projectRole" label="项目角色" rules={[{ required: true }]}>
            <Select options={ROLE_OPTIONS} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={statusModalOpen}
        title="新增状态"
        onCancel={() => setStatusModalOpen(false)}
        onOk={() => void addStatus()}
        okText="保存"
        cancelText="取消"
      >
        <Form form={statusForm} layout="vertical">
          <Form.Item name="code" label="状态编码" rules={[{ required: true, message: '请输入状态编码' }]}>
            <Input placeholder="如 REVIEW" />
          </Form.Item>
          <Form.Item name="name" label="状态名称" rules={[{ required: true, message: '请输入状态名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="category" label="分类" rules={[{ required: true }]}>
            <Select options={CATEGORY_OPTIONS} />
          </Form.Item>
          <Form.Item name="isDefault" label="是否默认" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="sortNo" label="排序号">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={transitionModalOpen}
        title="新增流转"
        onCancel={() => setTransitionModalOpen(false)}
        onOk={() => void addTransition()}
        okText="保存"
        cancelText="取消"
      >
        <Form form={transitionForm} layout="vertical">
          <Form.Item name="fromStatusId" label="源状态" rules={[{ required: true }]}>
            <Select options={statuses.map((status) => ({ value: status.id, label: status.name }))} />
          </Form.Item>
          <Form.Item name="toStatusId" label="目标状态" rules={[{ required: true }]}>
            <Select options={statuses.map((status) => ({ value: status.id, label: status.name }))} />
          </Form.Item>
          <Form.Item name="name" label="动作名称" rules={[{ required: true, message: '请输入动作名称' }]}>
            <Input placeholder="如 评审通过" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
