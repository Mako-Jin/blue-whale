// 空间列表：全部 / 我的空间，创建、编辑、归档入口

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Col,
  Dropdown,
  Empty,
  Form,
  Input,
  Modal,
  Pagination,
  Row,
  Segmented,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { EllipsisOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { hasPermission } from '../api/client';
import { spaceApi, type SpaceSavePayload } from '../api/space';
import type { DocSpace, DocVisibility } from '../api/types';

const { Text } = Typography;

/** 可见性文案 */
const VISIBILITY_LABEL: Record<DocVisibility, string> = {
  PUBLIC: '公开',
  MEMBER: '成员可见',
};

/** 角色文案 */
const ROLE_LABEL: Record<string, string> = {
  OWNER: '所有者',
  ADMIN: '管理员',
  EDITOR: '编辑者',
  READER: '阅读者',
};

/** 空间卡片：名称、描述、可见性、我的角色 */
export default function SpaceList() {
  const { message, modal } = App.useApp();
  const [form] = Form.useForm<SpaceSavePayload>();
  const [scope, setScope] = useState<'all' | 'mine'>('all');
  const [keyword, setKeyword] = useState('');
  const [current, setCurrent] = useState(1);
  const [spaces, setSpaces] = useState<DocSpace[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DocSpace | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const canCreate = useMemo(() => hasPermission('sys:doc:space:add'), []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await spaceApi.page({ current, size: 12, keyword, scope });
      setSpaces(result.records);
      setTotal(result.total);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [current, keyword, scope, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ visibility: 'MEMBER' });
    setModalOpen(true);
  };

  const openEdit = (space: DocSpace) => {
    setEditing(space);
    form.setFieldsValue({
      spaceKey: space.spaceKey,
      name: space.name,
      description: space.description ?? '',
      icon: space.icon ?? '',
      visibility: space.visibility,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      if (editing) {
        await spaceApi.update(editing.id, values);
        message.success('空间已更新');
      } else {
        await spaceApi.create(values);
        message.success('空间已创建');
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  const archive = (space: DocSpace) => {
    modal.confirm({
      title: `归档空间「${space.name}」？`,
      content: '归档后空间不再出现在列表与知识源同步中，页面数据保留。',
      okText: '归档',
      okButtonProps: { danger: true },
      onOk: async () => {
        await spaceApi.archive(space.id);
        message.success('空间已归档');
        await load();
      },
    });
  };

  return (
    <div>
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }} wrap>
        <Space wrap>
          <Segmented
            options={[
              { label: '全部空间', value: 'all' },
              { label: '我的空间', value: 'mine' },
            ]}
            value={scope}
            onChange={(value) => {
              setScope(value as 'all' | 'mine');
              setCurrent(1);
            }}
          />
          <Input.Search
            allowClear
            placeholder="搜索空间名称或描述"
            prefix={<SearchOutlined />}
            style={{ width: 260 }}
            onSearch={(value) => {
              setKeyword(value.trim());
              setCurrent(1);
            }}
          />
        </Space>
        {canCreate && (
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新建空间
          </Button>
        )}
      </Space>

      <Spin spinning={loading}>
        {spaces.length === 0 ? (
          <Empty description={scope === 'mine' ? '还没有加入任何空间' : '暂无可见空间'} style={{ padding: 48 }} />
        ) : (
          <Row gutter={[16, 16]}>
            {spaces.map((space) => {
              const manage = space.memberRole === 'OWNER' || space.memberRole === 'ADMIN';
              const items = [
                ...(manage ? [{ key: 'edit', label: '编辑空间' }] : []),
                ...(space.memberRole === 'OWNER'
                  ? [{ key: 'archive', label: '归档空间', danger: true }]
                  : []),
              ];
              return (
                <Col key={space.id} xs={24} sm={12} lg={8} xl={6}>
                  <Card
                    hoverable
                    styles={{ body: { minHeight: 150 } }}
                    actions={items.length > 0
                      ? [
                          <Dropdown
                            key="more"
                            menu={{
                              items,
                              onClick: ({ key, domEvent }) => {
                                domEvent.stopPropagation();
                                if (key === 'edit') {
                                  openEdit(space);
                                } else {
                                  archive(space);
                                }
                              },
                            }}
                          >
                            <Button type="text" icon={<EllipsisOutlined />} />
                          </Dropdown>,
                        ]
                      : undefined}
                  >
                    <Card.Meta
                      title={
                        <Space size={8}>
                          <span>{space.icon || '📄'}</span>
                          <Link to={`/space/${space.id}`}>{space.name}</Link>
                        </Space>
                      }
                      description={
                        <Space direction="vertical" size={8} style={{ width: '100%' }}>
                          <Text type="secondary" ellipsis={{ tooltip: space.description }}>
                            {space.description || '暂无描述'}
                          </Text>
                          <Space size={4} wrap>
                            <Tag color={space.visibility === 'PUBLIC' ? 'blue' : 'default'}>
                              {VISIBILITY_LABEL[space.visibility]}
                            </Tag>
                            {space.memberRole && <Tag color="green">{ROLE_LABEL[space.memberRole]}</Tag>}
                            {typeof space.memberCount === 'number' && (
                              <Text type="secondary">{space.memberCount} 人</Text>
                            )}
                          </Space>
                        </Space>
                      }
                    />
                  </Card>
                </Col>
              );
            })}
          </Row>
        )}
      </Spin>

      {total > 0 && (
        <div style={{ marginTop: 24, textAlign: 'right' }}>
          <Pagination
            current={current}
            pageSize={12}
            total={total}
            showSizeChanger={false}
            onChange={(page) => setCurrent(page)}
          />
        </div>
      )}

      <Modal
        title={editing ? '编辑空间' : '新建空间'}
        open={modalOpen}
        confirmLoading={submitting}
        onOk={() => void submit()}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="spaceKey" label="空间编码" tooltip="留空自动生成；创建后不可修改">
            <Input disabled={Boolean(editing)} placeholder="例如 product-docs" maxLength={32} />
          </Form.Item>
          <Form.Item
            name="name"
            label="空间名称"
            rules={[{ required: true, message: '请输入空间名称' }, { max: 100, message: '最多 100 字' }]}
          >
            <Input placeholder="例如 产品文档" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} maxLength={500} showCount placeholder="空间用途说明" />
          </Form.Item>
          <Form.Item name="icon" label="图标">
            <Input placeholder="可填 emoji 或图标名" maxLength={64} />
          </Form.Item>
          <Form.Item name="visibility" label="可见性" initialValue="MEMBER">
            <Select
              options={[
                { value: 'MEMBER', label: '成员可见（仅空间成员可访问）' },
                { value: 'PUBLIC', label: '公开（已发布页面全员可读）' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
