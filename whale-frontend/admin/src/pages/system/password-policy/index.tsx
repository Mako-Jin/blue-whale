// 租户口令策略配置页（REQ-SEC-023）
// 口令有效期（1–365 天）与到期提醒天数由租户管理员自助维护；未自定义时沿用全局默认。
// 接口：GET/PUT /tenant/password-policy（权限码 sys:tenant:pwdpolicy:list / edit）

import { Alert, App, Button, Card, Descriptions, Form, InputNumber, Space, Spin, Tag } from 'antd';
import { useEffect, useState, useCallback } from 'react';
import { usePermission } from '@bluewhale/whale-ui';
import {
  getTenantPasswordPolicy,
  updateTenantPasswordPolicy,
  type TenantPasswordPolicyVO,
} from '../../../api/tenant';

interface PolicyFormValues {
  maxAgeDays: number;
  warnDays: number;
}

/** 校验提醒天数不超过有效期（后端同样校验，此处提前提示） */
function validateWarnDays(_: unknown, value: number | undefined, maxAgeDays?: number) {
  if (value == null) {
    return Promise.reject(new Error('请输入到期提醒天数'));
  }
  if (maxAgeDays != null && value > maxAgeDays) {
    return Promise.reject(new Error('到期提醒天数不能大于口令有效期'));
  }
  return Promise.resolve();
}

export default function PasswordPolicy() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const canEdit = has('sys:tenant:pwdpolicy:edit');

  const [form] = Form.useForm<PolicyFormValues>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [policy, setPolicy] = useState<TenantPasswordPolicyVO | null>(null);

  const applyPolicy = useCallback(
    (data: TenantPasswordPolicyVO) => {
      setPolicy(data);
      form.setFieldsValue({ maxAgeDays: data.maxAgeDays, warnDays: data.warnDays });
    },
    [form],
  );

  useEffect(() => {
    getTenantPasswordPolicy()
      .then(applyPolicy)
      .catch((error: unknown) => {
        message.error(error instanceof Error ? error.message : '口令策略加载失败');
      })
      .finally(() => setLoading(false));
  }, [applyPolicy, message]);

  const handleSave = async (values: PolicyFormValues) => {
    setSaving(true);
    try {
      const updated = await updateTenantPasswordPolicy({
        maxAgeDays: values.maxAgeDays,
        warnDays: values.warnDays,
      });
      applyPolicy(updated);
      message.success('口令策略已保存，将在该租户用户下次登录时生效');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '口令策略保存失败');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <Card>
        <Spin tip="口令策略加载中" />
      </Card>
    );
  }

  return (
    <Space direction="vertical" size={16} style={{ display: 'flex' }}>
      <Card
        title="口令策略"
        extra={policy?.customized ? <Tag color="blue">已自定义</Tag> : <Tag>沿用全局默认</Tag>}
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="口令到期后将强制用户在登录后修改口令；管理员重置口令时系统生成一次性临时口令，仅在重置结果中展示一次。"
        />
        <Descriptions column={2} size="small" style={{ marginBottom: 16 }}>
          <Descriptions.Item label="生效口令有效期">
            {policy?.maxAgeDays ?? '-'} 天
          </Descriptions.Item>
          <Descriptions.Item label="生效到期提醒">{policy?.warnDays ?? '-'} 天</Descriptions.Item>
          <Descriptions.Item label="全局默认有效期">
            {policy?.defaultMaxAgeDays ?? '-'} 天
          </Descriptions.Item>
          <Descriptions.Item label="全局默认提醒">
            {policy?.defaultWarnDays ?? '-'} 天
          </Descriptions.Item>
        </Descriptions>
        <Form<PolicyFormValues>
          form={form}
          layout="vertical"
          onFinish={(values) => void handleSave(values)}
          disabled={!canEdit}
          style={{ maxWidth: 420 }}
        >
          <Form.Item
            name="maxAgeDays"
            label="口令有效期（天）"
            extra="取值范围 1–365 天，到期后强制修改"
            rules={[{ required: true, message: '请输入口令有效期' }]}
          >
            <InputNumber min={1} max={365} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="warnDays"
            label="到期前提醒天数"
            extra="0 表示不提前提醒；到期提醒不影响口令正常使用"
            dependencies={['maxAgeDays']}
            rules={[
              { required: true, message: '请输入到期提醒天数' },
              ({ getFieldValue }) => ({
                validator: (_rule, value: number | undefined) =>
                  validateWarnDays(_rule, value, getFieldValue('maxAgeDays') as number | undefined),
              }),
            ]}
          >
            <InputNumber min={0} max={365} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          {canEdit ? (
            <Form.Item>
              <Button type="primary" htmlType="submit" loading={saving}>
                保存策略
              </Button>
            </Form.Item>
          ) : (
            <Alert type="warning" showIcon message="当前账号没有租户口令策略的编辑权限。" />
          )}
        </Form>
      </Card>
    </Space>
  );
}
