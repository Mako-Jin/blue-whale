// 审批抽屉：任务信息 + 关联表单（可编辑保存）+ 审批意见 + 办理动作入口与同意 / 驳回。
// 非待办任务只读展示。

import { Button, Descriptions, Drawer, Empty, Input, Space, Spin, Tag, Typography } from 'antd';
import { FormRenderer } from '@bluewhale/whale-ui';
import type { ApprovalController } from './useApprovalDrawer';
import type { TaskAction } from './constants';

interface ApprovalDrawerProps {
  approval: ApprovalController;
  /** 打开办理动作弹窗（转办 / 委派 / 回退 / 处理完成 / 加签） */
  onOpenTaskAction: (action: TaskAction) => void;
}

export default function ApprovalDrawer({ approval, onOpenTaskAction }: ApprovalDrawerProps) {
  const {
    drawerOpen,
    activeTask,
    drawerLoading,
    formReady,
    formFields,
    formValues,
    formError,
    formDataId,
    formDirty,
    savingForm,
    comment,
    setComment,
    submitting,
    closeDrawer,
    handleFormChange,
    saveForm,
    handleApproval,
  } = approval;

  const readOnly = activeTask !== null && activeTask.status !== 0;

  return (
    <Drawer
      title="任务审批"
      width={720}
      open={drawerOpen}
      onClose={closeDrawer}
      footer={
        readOnly ? null : (
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: 8,
              flexWrap: 'wrap',
            }}
          >
            <Space wrap>
              <Button onClick={() => onOpenTaskAction('transfer')}>转办</Button>
              <Button onClick={() => onOpenTaskAction('delegate')}>委派</Button>
              <Button onClick={() => onOpenTaskAction('rollback')}>回退</Button>
              <Button onClick={() => onOpenTaskAction('addSign')}>加签</Button>
              {activeTask?.delegateFrom ? (
                <Button type="primary" ghost onClick={() => onOpenTaskAction('resolve')}>
                  处理完成
                </Button>
              ) : null}
            </Space>
            <Space>
              <Button onClick={() => void handleApproval('reject')} loading={submitting}>
                驳回
              </Button>
              <Button
                type="primary"
                onClick={() => void handleApproval('approve')}
                loading={submitting}
              >
                同意
              </Button>
            </Space>
          </div>
        )
      }
    >
      <Descriptions
        column={1}
        bordered
        size="small"
        style={{ marginBottom: 16 }}
        items={[
          { key: 'nodeName', label: '节点名称', children: activeTask?.nodeName || '-' },
          { key: 'assignee', label: '审批人', children: activeTask?.assignee || '-' },
          { key: 'createTime', label: '创建时间', children: activeTask?.createTime || '-' },
          { key: 'comment', label: '已有意见', children: activeTask?.comment || '-' },
          ...(activeTask?.signType === 'COUNTER_SIGN'
            ? [
                {
                  key: 'signPolicy',
                  label: '会签策略',
                  children: activeTask?.signPolicy || 'ALL',
                },
              ]
            : []),
          ...(activeTask?.signType === 'ADD_SIGN'
            ? [{ key: 'signType', label: '任务类型', children: '加签' }]
            : []),
          ...(activeTask?.ownerId && activeTask.ownerId !== activeTask.assignee
            ? [{ key: 'ownerId', label: '任务归属人', children: activeTask.ownerId }]
            : []),
          ...(activeTask?.delegateFrom
            ? [{ key: 'delegateFrom', label: '委派来源', children: activeTask.delegateFrom }]
            : []),
        ]}
      />

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 8,
          gap: 8,
        }}
      >
        <Typography.Title level={5} style={{ margin: 0 }}>
          表单信息
        </Typography.Title>
        {readOnly || !formReady || !formDataId ? null : (
          <Space>
            {formDirty ? <Tag color="orange">有未保存修改</Tag> : null}
            <Button size="small" loading={savingForm} onClick={() => void saveForm()}>
              保存表单
            </Button>
          </Space>
        )}
      </div>
      <Spin spinning={drawerLoading}>
        {formReady ? (
          <FormRenderer
            fields={formFields}
            value={formValues}
            disabled={readOnly}
            onChange={readOnly ? undefined : handleFormChange}
          />
        ) : drawerLoading ? null : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={formError ?? '无表单数据'} />
        )}
      </Spin>

      {readOnly ? null : (
        <>
          <Typography.Title level={5} style={{ marginTop: 16 }}>
            审批意见
          </Typography.Title>
          <Input.TextArea
            rows={3}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="请输入审批意见（可选）"
          />
        </>
      )}
    </Drawer>
  );
}
