// 看板：看板选择、执行视图、卡片流转（走状态机）与看板列维护

import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd';
import { Link, useParams } from 'react-router-dom';
import { boardApi, type Board, type BoardView, type WorkItem } from '../api/pm';
import { hasPermission, priorityColor, priorityLabel, typeLabel } from '../utils';
import { useWorkspace } from '../workspace-context';

export default function BoardPage() {
  const { projectId = '' } = useParams();
  const { statuses } = useWorkspace();
  const { message } = App.useApp();
  const [boards, setBoards] = useState<Board[]>([]);
  const [activeBoardId, setActiveBoardId] = useState<string>();
  const [view, setView] = useState<BoardView | null>(null);
  const [boardForm] = Form.useForm();
  const [columnForm] = Form.useForm();
  const [boardModalOpen, setBoardModalOpen] = useState(false);
  const [columnModalOpen, setColumnModalOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [dragged, setDragged] = useState<WorkItem | null>(null);

  const loadBoards = useCallback(async () => {
    try {
      const list = await boardApi.list(projectId);
      setBoards(list);
      setActiveBoardId((previous) => previous ?? list[0]?.id);
    } catch (error) {
      message.error((error as Error).message);
    }
  }, [projectId, message]);

  const loadView = useCallback(async () => {
    if (!activeBoardId) {
      setView(null);
      return;
    }
    setLoading(true);
    try {
      setView(await boardApi.view(activeBoardId));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [activeBoardId, message]);

  useEffect(() => {
    void loadBoards();
  }, [loadBoards]);

  useEffect(() => {
    void loadView();
  }, [loadView]);

  const createBoard = async () => {
    const values = await boardForm.validateFields();
    try {
      await boardApi.create({
        projectId: Number(projectId),
        name: values.name,
        boardType: values.boardType,
      });
      message.success('看板已创建');
      setBoardModalOpen(false);
      await loadBoards();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const addColumn = async () => {
    if (!activeBoardId) {
      return;
    }
    const values = await columnForm.validateFields();
    try {
      await boardApi.addColumn(activeBoardId, {
        statusId: Number(values.statusId),
        name: values.name,
        wipLimit: values.wipLimit ?? 0,
        sortNo: values.sortNo,
      });
      message.success('看板列已新增');
      setColumnModalOpen(false);
      await loadView();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const removeColumn = async (columnId: string) => {
    if (!activeBoardId) {
      return;
    }
    try {
      await boardApi.removeColumn(activeBoardId, columnId);
      message.success('看板列已删除');
      await loadView();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const moveItem = async (item: WorkItem, targetStatusId: string) => {
    if (!activeBoardId) {
      return;
    }
    try {
      await boardApi.drag(activeBoardId, {
        workItemId: Number(item.id),
        targetStatusId: Number(targetStatusId),
      });
      message.success('卡片已移动');
      await loadView();
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  return (
    <div>
      <Space style={{ marginBottom: 16 }} wrap>
        <Typography.Text strong>看板</Typography.Text>
        <Select
          style={{ minWidth: 200 }}
          value={activeBoardId}
          placeholder="选择看板"
          options={boards.map((board) => ({ value: board.id, label: board.name }))}
          onChange={setActiveBoardId}
        />
        {hasPermission('sys:pm:board:manage') && (
          <>
            <Button
              onClick={() => {
                boardForm.resetFields();
                boardForm.setFieldsValue({ boardType: 'SCRUM' });
                setBoardModalOpen(true);
              }}
            >
              新建看板
            </Button>
            <Button
              disabled={!activeBoardId}
              onClick={() => {
                columnForm.resetFields();
                columnForm.setFieldsValue({ wipLimit: 0 });
                setColumnModalOpen(true);
              }}
            >
              新增列
            </Button>
          </>
        )}
      </Space>

      {!view ? (
        <Empty description="暂无看板，请先创建" />
      ) : (
        <div
          style={{
            display: 'flex',
            gap: 12,
            overflowX: 'auto',
            alignItems: 'flex-start',
            opacity: loading ? 0.6 : 1,
          }}
        >
          {view.columns.map((column) => (
            <Card
              key={column.id}
              size="small"
              style={{ width: 280, flex: '0 0 auto' }}
              title={
                <Space size={6}>
                  <span>{column.name}</span>
                  <Tag color={column.overloaded ? 'red' : 'default'}>{column.itemCount}</Tag>
                  {column.wipLimit > 0 && (
                    <Typography.Text type="secondary">WIP {column.wipLimit}</Typography.Text>
                  )}
                  {hasPermission('sys:pm:board:manage') && (
                    <Popconfirm title="删除该列？" onConfirm={() => void removeColumn(column.id)}>
                      <Button type="link" size="small" danger>
                        删除
                      </Button>
                    </Popconfirm>
                  )}
                </Space>
              }
            >
              <div
                style={{ minHeight: 60 }}
                onDragOver={(event) => event.preventDefault()}
                onDrop={() => {
                  if (dragged) {
                    void moveItem(dragged, column.statusId);
                    setDragged(null);
                  }
                }}
              >
                <Space direction="vertical" style={{ width: '100%' }} size={8}>
                  {column.items.length === 0 && (
                    <Typography.Text type="secondary">暂无工作项</Typography.Text>
                  )}
                  {column.items.map((item) => (
                    <div
                      key={item.id}
                      draggable
                      onDragStart={() => setDragged(item)}
                      onDragEnd={() => setDragged(null)}
                    >
                      <Card size="small" bodyStyle={{ padding: 10, cursor: 'grab' }}>
                        <Space direction="vertical" size={4} style={{ width: '100%' }}>
                          <Space size={6}>
                            <Tag>{typeLabel(item.type)}</Tag>
                            <Tag color={priorityColor(item.priority)}>
                              {priorityLabel(item.priority)}
                            </Tag>
                            <Link to={`/project/${projectId}/item/${item.id}`}>{item.key}</Link>
                          </Space>
                          <Typography.Text ellipsis>{item.title}</Typography.Text>
                          <Select
                            size="small"
                            placeholder="移动到"
                            value={undefined}
                            style={{ width: '100%' }}
                            options={view.columns
                              .filter((target) => target.id !== column.id)
                              .map((target) => ({ value: target.statusId, label: target.name }))}
                            onChange={(value: string) => value && void moveItem(item, value)}
                          />
                        </Space>
                      </Card>
                    </div>
                  ))}
                </Space>
              </div>
            </Card>
          ))}
        </div>
      )}

      <Modal
        open={boardModalOpen}
        title="新建看板"
        onCancel={() => setBoardModalOpen(false)}
        onOk={() => void createBoard()}
        okText="创建"
        cancelText="取消"
      >
        <Form form={boardForm} layout="vertical">
          <Form.Item
            name="name"
            label="看板名称"
            rules={[{ required: true, message: '请输入看板名称' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="boardType" label="类型">
            <Select
              options={[
                { value: 'SCRUM', label: 'Scrum' },
                { value: 'KANBAN', label: 'Kanban' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={columnModalOpen}
        title="新增看板列"
        onCancel={() => setColumnModalOpen(false)}
        onOk={() => void addColumn()}
        okText="新增"
        cancelText="取消"
      >
        <Form form={columnForm} layout="vertical">
          <Form.Item
            name="statusId"
            label="映射状态"
            rules={[{ required: true, message: '请选择状态' }]}
          >
            <Select
              options={statuses.map((status) => ({ value: status.id, label: status.name }))}
            />
          </Form.Item>
          <Form.Item
            name="name"
            label="列名称"
            rules={[{ required: true, message: '请输入列名称' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="wipLimit" label="WIP 上限（0 表示不限）">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="sortNo" label="排序号">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
