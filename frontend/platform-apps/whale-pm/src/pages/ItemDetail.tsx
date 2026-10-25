// 工作项详情：字段、状态流转、评论、动态、附件与关联

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  List,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Link, useParams } from 'react-router-dom';
import {
  itemApi,
  workflowApi,
  type Activity,
  type Attachment,
  type Comment,
  type Status,
  type Transition,
  type WorkItem,
  type WorkItemLink,
} from '../api/pm';
import {
  LINK_TYPE_OPTIONS,
  formatDate,
  formatTime,
  hasPermission,
  priorityColor,
  priorityLabel,
  statusColor,
  typeLabel,
} from '../utils';

export default function ItemDetail() {
  const { projectId = '', itemId = '' } = useParams();
  const { message } = App.useApp();
  const [item, setItem] = useState<WorkItem | null>(null);
  const [statuses, setStatuses] = useState<Status[]>([]);
  const [transitions, setTransitions] = useState<Transition[]>([]);
  const [activities, setActivities] = useState<Activity[]>([]);
  const [comments, setComments] = useState<Comment[]>([]);
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [links, setLinks] = useState<WorkItemLink[]>([]);
  const [loading, setLoading] = useState(true);
  const [commentText, setCommentText] = useState('');
  const [transitionModalOpen, setTransitionModalOpen] = useState(false);
  const [attachmentModalOpen, setAttachmentModalOpen] = useState(false);
  const [linkModalOpen, setLinkModalOpen] = useState(false);
  const [transitionForm] = Form.useForm();
  const [attachmentForm] = Form.useForm();
  const [linkForm] = Form.useForm();

  const load = useCallback(async () => {
    if (!itemId) {
      return;
    }
    setLoading(true);
    try {
      const [detail, statusList, transitionList, activityList, commentList, attachmentList, linkList] =
        await Promise.all([
          itemApi.detail(itemId),
          workflowApi.statuses(projectId),
          workflowApi.transitions(projectId),
          itemApi.activities(itemId),
          itemApi.comments(itemId),
          itemApi.attachments(itemId),
          itemApi.links(itemId),
        ]);
      setItem(detail);
      setStatuses(statusList);
      setTransitions(transitionList);
      setActivities(activityList);
      setComments(commentList);
      setAttachments(attachmentList);
      setLinks(linkList);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [itemId, projectId, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleTransition = async () => {
    if (!item) {
      return;
    }
    const values = await transitionForm.validateFields();
    try {
      await itemApi.transition(item.id, {
        targetStatusId: values.targetStatusId,
        comment: values.comment,
      });
      message.success('状态已流转');
      setTransitionModalOpen(false);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const submitComment = async () => {
    if (!item || !commentText.trim()) {
      return;
    }
    try {
      await itemApi.addComment(item.id, { content: commentText.trim() });
      setCommentText('');
      message.success('评论已发布');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const removeComment = async (comment: Comment) => {
    try {
      await itemApi.removeComment(comment.id);
      message.success('评论已删除');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const addAttachment = async () => {
    if (!item) {
      return;
    }
    const values = await attachmentForm.validateFields();
    try {
      await itemApi.addAttachment(item.id, { fileId: Number(values.fileId), fileName: values.fileName });
      message.success('附件已登记');
      setAttachmentModalOpen(false);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const removeAttachment = async (attachment: Attachment) => {
    try {
      await itemApi.removeAttachment(attachment.id);
      message.success('附件已解除');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const addLink = async () => {
    if (!item) {
      return;
    }
    const values = await linkForm.validateFields();
    try {
      await itemApi.addLink(item.id, {
        targetItemId: Number(values.targetItemId),
        linkType: values.linkType,
      });
      message.success('关联已新增');
      setLinkModalOpen(false);
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const removeLink = async (link: WorkItemLink) => {
    try {
      await itemApi.removeLink(link.id);
      message.success('关联已删除');
      await load();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const targetOptions = () => {
    if (!item) {
      return [];
    }
    const allowed = transitions.filter((transition) => transition.fromStatusId === item.statusId);
    const source = allowed.length
      ? statuses.filter((status) => allowed.some((transition) => transition.toStatusId === status.id))
      : statuses.filter((status) => status.id !== item.statusId);
    return source.map((status) => ({ value: status.id, label: status.name }));
  };

  const attachmentColumns: ColumnsType<Attachment> = [
    { title: '文件名', dataIndex: 'fileName' },
    { title: '文件ID', dataIndex: 'fileId', width: 140 },
    { title: '上传人', dataIndex: 'uploaderId', width: 120, render: (value?: string) => value ?? '-' },
    { title: '时间', dataIndex: 'createTime', width: 170, render: formatTime },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        hasPermission('sys:pm:attachment:edit') && (
          <Button type="link" size="small" danger onClick={() => void removeAttachment(record)}>
            解除
          </Button>
        ),
    },
  ];

  const linkColumns: ColumnsType<WorkItemLink> = [
    { title: '类型', dataIndex: 'linkType', width: 140 },
    { title: '目标编号', dataIndex: 'targetKey', width: 140, render: (value?: string) => value ?? '-' },
    { title: '目标标题', dataIndex: 'targetTitle', render: (value?: string) => value ?? '-' },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        hasPermission('sys:pm:link:edit') && (
          <Button type="link" size="small" danger onClick={() => void removeLink(record)}>
            删除
          </Button>
        ),
    },
  ];

  if (loading && !item) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16 }} wrap>
        <Link to={`/project/${projectId}/backlog`}>← 返回 Backlog</Link>
        {item && (
          <>
            <Typography.Title level={5} style={{ margin: 0 }}>
              {item.key} · {item.title}
            </Typography.Title>
            <Tag>{typeLabel(item.type)}</Tag>
            <Tag color={statusColor(item.statusCategory)}>{item.statusName}</Tag>
            <Tag color={priorityColor(item.priority)}>{priorityLabel(item.priority)}</Tag>
            {hasPermission('sys:pm:item:transition') && (
              <Button
                onClick={() => {
                  transitionForm.resetFields();
                  setTransitionModalOpen(true);
                }}
              >
                状态流转
              </Button>
            )}
          </>
        )}
      </Space>

      {item && (
        <Card size="small" style={{ marginBottom: 16 }}>
          <Descriptions column={4} size="small">
            <Descriptions.Item label="描述" span={4}>
              {item.description ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="负责人">{item.assigneeId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="报告人">{item.reporterId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="故事点">{item.storyPoints ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="截止日期">{formatDate(item.dueDate)}</Descriptions.Item>
            <Descriptions.Item label="所属迭代">{item.sprintId ?? '未加入迭代'}</Descriptions.Item>
            <Descriptions.Item label="父工作项">{item.parentId ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{formatTime(item.createTime)}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{formatTime(item.updateTime)}</Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      <Tabs
        items={[
          {
            key: 'comment',
            label: `评论 ${comments.length}`,
            children: (
              <div>
                <Space.Compact style={{ width: '100%', marginBottom: 12 }}>
                  <Input
                    placeholder="写下评论…"
                    value={commentText}
                    onChange={(event) => setCommentText(event.target.value)}
                    onPressEnter={() => void submitComment()}
                  />
                  <Button type="primary" onClick={() => void submitComment()}>
                    发布
                  </Button>
                </Space.Compact>
                <List
                  size="small"
                  dataSource={comments}
                  locale={{ emptyText: '暂无评论' }}
                  renderItem={(comment) => (
                    <List.Item
                      actions={
                        hasPermission('sys:pm:comment:delete')
                          ? [
                              <Button
                                key="remove"
                                type="link"
                                size="small"
                                danger
                                onClick={() => void removeComment(comment)}
                              >
                                删除
                              </Button>,
                            ]
                          : []
                      }
                    >
                      <Space direction="vertical" size={2}>
                        <Typography.Text type="secondary">
                          {comment.authorId ?? '-'} · {formatTime(comment.createTime)}
                        </Typography.Text>
                        <Typography.Text>{comment.content}</Typography.Text>
                      </Space>
                    </List.Item>
                  )}
                />
              </div>
            ),
          },
          {
            key: 'activity',
            label: `动态 ${activities.length}`,
            children: (
              <List
                size="small"
                dataSource={activities}
                locale={{ emptyText: '暂无动态' }}
                renderItem={(activity) => (
                  <List.Item>
                    <Typography.Text type="secondary" style={{ marginRight: 8 }}>
                      {formatTime(activity.createTime)}
                    </Typography.Text>
                    <Tag>{activity.action}</Tag>
                    {activity.field
                      ? `${activity.field}: ${activity.oldValue ?? '-'} → ${activity.newValue ?? '-'}`
                      : activity.newValue}
                  </List.Item>
                )}
              />
            ),
          },
          {
            key: 'attachment',
            label: `附件 ${attachments.length}`,
            children: (
              <div>
                {hasPermission('sys:pm:attachment:edit') && (
                  <Button
                    type="primary"
                    style={{ marginBottom: 12 }}
                    onClick={() => {
                      attachmentForm.resetFields();
                      setAttachmentModalOpen(true);
                    }}
                  >
                    登记附件
                  </Button>
                )}
                <Table
                  rowKey="id"
                  size="small"
                  columns={attachmentColumns}
                  dataSource={attachments}
                  pagination={false}
                />
              </div>
            ),
          },
          {
            key: 'link',
            label: `关联 ${links.length}`,
            children: (
              <div>
                {hasPermission('sys:pm:link:edit') && (
                  <Button
                    type="primary"
                    style={{ marginBottom: 12 }}
                    onClick={() => {
                      linkForm.resetFields();
                      linkForm.setFieldsValue({ linkType: 'RELATES' });
                      setLinkModalOpen(true);
                    }}
                  >
                    新增关联
                  </Button>
                )}
                <Table rowKey="id" size="small" columns={linkColumns} dataSource={links} pagination={false} />
              </div>
            ),
          },
        ]}
      />

      <Modal
        open={transitionModalOpen}
        title="状态流转"
        onCancel={() => setTransitionModalOpen(false)}
        onOk={() => void handleTransition()}
        okText="提交"
        cancelText="取消"
      >
        <Form form={transitionForm} layout="vertical">
          <Form.Item name="targetStatusId" label="目标状态" rules={[{ required: true, message: '请选择目标状态' }]}>
            <Select options={targetOptions()} />
          </Form.Item>
          <Form.Item name="comment" label="备注">
            <Input.TextArea rows={3} maxLength={512} showCount />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={attachmentModalOpen}
        title="登记附件"
        onCancel={() => setAttachmentModalOpen(false)}
        onOk={() => void addAttachment()}
        okText="登记"
        cancelText="取消"
      >
        <Form form={attachmentForm} layout="vertical">
          <Form.Item name="fileId" label="文件ID" rules={[{ required: true, message: '请输入文件ID' }]}>
            <Input placeholder="文件服务中的数字文件ID" />
          </Form.Item>
          <Form.Item name="fileName" label="文件名" rules={[{ required: true, message: '请输入文件名' }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={linkModalOpen}
        title="新增关联"
        onCancel={() => setLinkModalOpen(false)}
        onOk={() => void addLink()}
        okText="新增"
        cancelText="取消"
      >
        <Form form={linkForm} layout="vertical">
          <Form.Item name="targetItemId" label="目标工作项ID" rules={[{ required: true, message: '请输入目标工作项ID' }]}>
            <Input placeholder="同项目的数字工作项ID" />
          </Form.Item>
          <Form.Item name="linkType" label="关联类型" rules={[{ required: true }]}>
            <Select options={LINK_TYPE_OPTIONS} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
