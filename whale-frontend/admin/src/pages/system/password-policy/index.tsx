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
import { useI18n } from '../../../i18n';

interface PolicyFormValues {
  maxAgeDays: number;
  warnDays: number;
}

type Translate = ReturnType<typeof useI18n>['t'];

/** 校验提醒天数不超过有效期（后端同样校验，此处提前提示） */
function validateWarnDays(
  _: unknown,
  value: number | undefined,
  maxAgeDays: number | undefined,
  t: Translate,
) {
  if (value == null) {
    return Promise.reject(new Error(t('systemAuth.passwordPolicy.field.warnDaysRequired')));
  }
  if (maxAgeDays != null && value > maxAgeDays) {
    return Promise.reject(new Error(t('systemAuth.passwordPolicy.field.warnDaysTooLarge')));
  }
  return Promise.resolve();
}

export default function PasswordPolicy() {
  const { message } = App.useApp();
  const { t } = useI18n();
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
        message.error(
          error instanceof Error ? error.message : t('systemAuth.passwordPolicy.loadFailed'),
        );
      })
      .finally(() => setLoading(false));
  }, [applyPolicy, message, t]);

  const handleSave = async (values: PolicyFormValues) => {
    setSaving(true);
    try {
      const updated = await updateTenantPasswordPolicy({
        maxAgeDays: values.maxAgeDays,
        warnDays: values.warnDays,
      });
      applyPolicy(updated);
      message.success(t('systemAuth.passwordPolicy.saved'));
    } catch (error) {
      message.error(
        error instanceof Error ? error.message : t('systemAuth.passwordPolicy.saveFailed'),
      );
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <Card>
        <Spin tip={t('systemAuth.passwordPolicy.loading')} />
      </Card>
    );
  }

  return (
    <Space direction="vertical" size={16} style={{ display: 'flex' }}>
      <Card
        title={t('systemAuth.passwordPolicy.title')}
        extra={
          policy?.customized ? (
            <Tag color="blue">{t('systemAuth.passwordPolicy.customized')}</Tag>
          ) : (
            <Tag>{t('systemAuth.passwordPolicy.defaultInherited')}</Tag>
          )
        }
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={t('systemAuth.passwordPolicy.notice')}
        />
        <Descriptions column={2} size="small" style={{ marginBottom: 16 }}>
          <Descriptions.Item label={t('systemAuth.passwordPolicy.effectiveMaxAge')}>
            {policy?.maxAgeDays ?? '-'} {t('systemAuth.passwordPolicy.days')}
          </Descriptions.Item>
          <Descriptions.Item label={t('systemAuth.passwordPolicy.effectiveWarnDays')}>
            {policy?.warnDays ?? '-'} {t('systemAuth.passwordPolicy.days')}
          </Descriptions.Item>
          <Descriptions.Item label={t('systemAuth.passwordPolicy.defaultMaxAge')}>
            {policy?.defaultMaxAgeDays ?? '-'} {t('systemAuth.passwordPolicy.days')}
          </Descriptions.Item>
          <Descriptions.Item label={t('systemAuth.passwordPolicy.defaultWarnDays')}>
            {policy?.defaultWarnDays ?? '-'} {t('systemAuth.passwordPolicy.days')}
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
            label={t('systemAuth.passwordPolicy.field.maxAgeDays')}
            extra={t('systemAuth.passwordPolicy.field.maxAgeDaysExtra')}
            rules={[
              {
                required: true,
                message: t('systemAuth.passwordPolicy.field.maxAgeDaysRequired'),
              },
            ]}
          >
            <InputNumber min={1} max={365} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="warnDays"
            label={t('systemAuth.passwordPolicy.field.warnDays')}
            extra={t('systemAuth.passwordPolicy.field.warnDaysExtra')}
            dependencies={['maxAgeDays']}
            rules={[
              { required: true, message: t('systemAuth.passwordPolicy.field.warnDaysRequired') },
              ({ getFieldValue }) => ({
                validator: (_rule, value: number | undefined) =>
                  validateWarnDays(
                    _rule,
                    value,
                    getFieldValue('maxAgeDays') as number | undefined,
                    t,
                  ),
              }),
            ]}
          >
            <InputNumber min={0} max={365} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          {canEdit ? (
            <Form.Item>
              <Button type="primary" htmlType="submit" loading={saving}>
                {t('systemAuth.passwordPolicy.save')}
              </Button>
            </Form.Item>
          ) : (
            <Alert
              type="warning"
              showIcon
              message={t('systemAuth.passwordPolicy.noPermission')}
            />
          )}
        </Form>
      </Card>
    </Space>
  );
}
