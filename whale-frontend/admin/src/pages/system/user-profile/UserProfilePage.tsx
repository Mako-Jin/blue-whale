// 用户身份档案管理页 - 选用户 → 档案查看/编辑（基本信息 + 教育经历 / 工作经历 /
// 家庭成员（紧急联系人）/ 联系方式 / 证件）→ 整体保存
// 权限码：sys:user:profile:view(2390) / save(2391) / delete(2392) / credential-view(2393)
// 脱敏口径：证件号 / 电话 / 联系方式为掩码——既有条目敏感字段留空 = 保留原值（后端按 id 回填），
// 证件号明文需专用权限走「明文」按钮；密保问答答案永不可读，本页不提供编辑（保存不动它）。

import {
  Alert,
  App,
  Button,
  Card,
  Checkbox,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tag,
  type TableProps,
} from 'antd';
import { useCallback, useState } from 'react';
import dayjs, { type Dayjs } from 'dayjs';
import { AuthButton, usePermission } from '@bluewhale/whale-ui';
import { ApiError } from '@bluewhale/http';
import { pageUsers } from '../../../api/user';
import { useI18n } from '../../../i18n';
import {
  deleteUserProfile,
  saveUserProfile,
  viewCredentialNo,
  viewUserProfile,
  type ProfileContactItem,
  type ProfileCredentialItem,
  type ProfileEducationItem,
  type ProfileEmergencyContactItem,
  type ProfileEmploymentItem,
  type UserProfileVO,
} from '../../../api/userProfile';

type Translate = ReturnType<typeof useI18n>['t'];

const genderOptions = (t: Translate) => [
  { value: 'MALE', label: t('systemOps.userProfile.gender.male') },
  { value: 'FEMALE', label: t('systemOps.userProfile.gender.female') },
  { value: 'OTHER', label: t('systemOps.common.other') },
];

const degreeOptions = (t: Translate) => [
  { value: 'DOCTOR', label: t('systemOps.userProfile.degree.doctor') },
  { value: 'MASTER', label: t('systemOps.userProfile.degree.master') },
  { value: 'BACHELOR', label: t('systemOps.userProfile.degree.bachelor') },
  { value: 'ASSOCIATE', label: t('systemOps.userProfile.degree.associate') },
  { value: 'OTHER', label: t('systemOps.common.other') },
];

const employmentTypeOptions = (t: Translate) => [
  { value: 'FORMAL', label: t('systemOps.userProfile.employmentType.formal') },
  { value: 'CONTRACT', label: t('systemOps.userProfile.employmentType.contract') },
  { value: 'INTERN', label: t('systemOps.userProfile.employmentType.intern') },
  { value: 'OUTSOURCING', label: t('systemOps.userProfile.employmentType.outsourcing') },
];

const relationshipOptions = (t: Translate) => [
  { value: 'PARENT', label: t('systemOps.userProfile.relationship.parent') },
  { value: 'SPOUSE', label: t('systemOps.userProfile.relationship.spouse') },
  { value: 'CHILD', label: t('systemOps.userProfile.relationship.child') },
  { value: 'SIBLING', label: t('systemOps.userProfile.relationship.sibling') },
  { value: 'FRIEND', label: t('systemOps.userProfile.relationship.friend') },
  { value: 'OTHER', label: t('systemOps.common.other') },
];

const contactTypeOptions = (t: Translate) => [
  { value: 'MOBILE', label: t('systemOps.userProfile.contactType.mobile') },
  { value: 'EMAIL', label: t('systemOps.userProfile.contactType.email') },
  { value: 'WECHAT', label: t('systemOps.userProfile.contactType.wechat') },
  { value: 'DINGTALK', label: t('systemOps.userProfile.contactType.dingtalk') },
  { value: 'OTHER', label: t('systemOps.common.other') },
];

const credentialTypeOptions = (t: Translate) => [
  { value: 'ID_CARD', label: t('systemOps.userProfile.credentialType.idCard') },
  { value: 'PASSPORT', label: t('systemOps.userProfile.credentialType.passport') },
  { value: 'HK_MO_PASS', label: t('systemOps.userProfile.credentialType.hkMoPass') },
  { value: 'OTHER', label: t('systemOps.common.other') },
];

const EMPTY_PROFILE: UserProfileVO = {
  id: '',
  userId: '',
  contacts: [],
  credentials: [],
  educations: [],
  employments: [],
  emergencyContacts: [],
  secretQuestions: [],
};

/** 既有条目敏感值留空 = 保留原值：新增行（无 id）没有可保留的原值，编辑行可以 */
function keepHint(t: Translate, rowId?: string): string {
  return rowId ? t('systemOps.userProfile.keepHint') : t('systemOps.userProfile.required');
}

export default function UserProfilePage() {
  const { message, modal } = App.useApp();
  const { t } = useI18n();
  const { has } = usePermission();

  const [userOptions, setUserOptions] = useState<{ value: string; label: string }[]>([]);
  const [userSearching, setUserSearching] = useState(false);
  const [userId, setUserId] = useState<string | undefined>(undefined);
  const [profile, setProfile] = useState<UserProfileVO | null>(null);
  const [profileLoading, setProfileLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const [basicForm] = Form.useForm<{
    gender?: string;
    birthDate?: Dayjs;
    birthPlace?: string;
    homeAddress?: string;
    nation?: string;
    politicalStatus?: string;
    maritalStatus?: string;
    remarks?: string;
  }>();

  const [contacts, setContacts] = useState<ProfileContactItem[]>([]);
  const [credentials, setCredentials] = useState<ProfileCredentialItem[]>([]);
  const [educations, setEducations] = useState<ProfileEducationItem[]>([]);
  const [employments, setEmployments] = useState<ProfileEmploymentItem[]>([]);
  const [emergencyContacts, setEmergencyContacts] = useState<ProfileEmergencyContactItem[]>([]);

  const showError = (error: unknown, fallback: string) => {
    message.error(error instanceof Error ? error.message : fallback);
  };

  const searchUsers = async (keyword: string) => {
    setUserSearching(true);
    try {
      const page = await pageUsers({ keyword: keyword || undefined, current: 1, size: 50 });
      setUserOptions(
        page.records.map((user) => ({
          value: user.id,
          label: `${user.nickname || user.username}（${user.username}）`,
        })),
      );
    } catch (error) {
      showError(error, t('systemOps.common.searchUserFailed'));
    } finally {
      setUserSearching(false);
    }
  };

  const applyProfile = useCallback(
    (data: UserProfileVO) => {
      setProfile(data);
      basicForm.setFieldsValue({
        gender: data.gender,
        birthDate: data.birthDate ? dayjs(data.birthDate) : undefined,
        birthPlace: data.birthPlace,
        homeAddress: data.homeAddress,
        nation: data.nation,
        politicalStatus: data.politicalStatus,
        maritalStatus: data.maritalStatus,
        remarks: data.remarks,
      });
      // 联系方式读端的 contactValue 是掩码：转存到 contactValueMasked，明文位清空，
      // 避免「原样回传掩码 → 服务端当明文加密」把真实号码覆盖掉。
      setContacts(
        (data.contacts ?? []).map((row) => ({
          ...row,
          contactValueMasked: row.contactValue,
          contactValue: undefined,
        })),
      );
      setCredentials(data.credentials ?? []);
      setEducations(data.educations ?? []);
      setEmployments(data.employments ?? []);
      setEmergencyContacts(data.emergencyContacts ?? []);
    },
    [basicForm],
  );

  const loadProfile = async (targetUserId: string) => {
    setProfileLoading(true);
    try {
      const data = await viewUserProfile(targetUserId);
      applyProfile(data);
    } catch (error) {
      if (error instanceof ApiError && error.code === 404) {
        // 尚未建档：进入空白可建档状态
        applyProfile({ ...EMPTY_PROFILE, userId: targetUserId });
        message.info(t('systemOps.userProfile.notCreated'));
      } else {
        setProfile(null);
        showError(error, t('systemOps.userProfile.loadFailed'));
      }
    } finally {
      setProfileLoading(false);
    }
  };

  const handleUserSelect = (value: string) => {
    setUserId(value);
    void loadProfile(value);
  };

  const toDateStr = (value: Dayjs | undefined): string | undefined =>
    value ? value.format('YYYY-MM-DD') : undefined;

  const toDay = (value: string | undefined): Dayjs | undefined =>
    value ? dayjs(value) : undefined;

  const handleSave = async () => {
    if (!userId) {
      return;
    }
    // 新增行必须给明文（带 id 的行留空=保留原值，允许空）
    if (contacts.some((row) => !row.id && !row.contactValue)) {
      message.error(t('systemOps.userProfile.contactValueRequired'));
      return;
    }
    if (credentials.some((row) => !row.id && !row.credentialNo)) {
      message.error(t('systemOps.userProfile.credentialNoRequired'));
      return;
    }
    setSaving(true);
    try {
      const basic = await basicForm.validateFields();
      const saved = await saveUserProfile(userId, {
        gender: basic.gender,
        birthDate: toDateStr(basic.birthDate),
        birthPlace: basic.birthPlace,
        homeAddress: basic.homeAddress,
        nation: basic.nation,
        politicalStatus: basic.politicalStatus,
        maritalStatus: basic.maritalStatus,
        remarks: basic.remarks,
        // 密保问答不传（undefined = 保留既有），本页不提供编辑
        contacts: contacts.map((row) => ({
          id: row.id,
          contactType: row.contactType,
          contactValue: row.contactValue,
          primary: row.primary,
          verified: row.verified,
          sortNo: row.sortNo ?? 0,
        })),
        credentials: credentials.map((row) => ({
          id: row.id,
          credentialType: row.credentialType,
          credentialNo: row.credentialNo,
          holderName: row.holderName,
          primary: row.primary,
          issueDate: row.issueDate,
          expiryDate: row.expiryDate,
        })),
        educations: educations.map((row) => ({ ...row, sortNo: row.sortNo ?? 0 })),
        employments: employments.map((row) => ({ ...row, sortNo: row.sortNo ?? 0 })),
        emergencyContacts: emergencyContacts.map((row) => ({
          id: row.id,
          contactName: row.contactName,
          relationship: row.relationship,
          phone: row.phone,
          sortNo: row.sortNo ?? 0,
        })),
      });
      message.success(t('systemOps.userProfile.saved'));
      applyProfile(saved);
    } catch (error) {
      if (error instanceof Error && error.message !== 'Validation failed') {
        showError(error, t('systemOps.common.saveFailed'));
      }
      // 表单校验中断时静默返回
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteProfile = () => {
    if (!userId) {
      return;
    }
    modal.confirm({
      title: t('systemOps.userProfile.deleteConfirmTitle'),
      content: t('systemOps.userProfile.deleteConfirmContent'),
      okText: t('systemOps.common.delete'),
      okButtonProps: { danger: true },
      onOk: async () => {
        await deleteUserProfile(userId);
        message.success(t('systemOps.userProfile.deleted'));
        setProfile(null);
        basicForm.resetFields();
        setContacts([]);
        setCredentials([]);
        setEducations([]);
        setEmployments([]);
        setEmergencyContacts([]);
      },
    });
  };

  const handleViewCredentialNo = async (row: ProfileCredentialItem) => {
    if (!userId || !row.id) {
      return;
    }
    try {
      const plain = await viewCredentialNo(userId, row.id);
      modal.info({
        title: t('systemOps.userProfile.credentialPlainTitle'),
        content: `${row.credentialType}: ${plain}`,
      });
    } catch (error) {
      showError(error, t('systemOps.userProfile.credentialPlainFailed'));
    }
  };

  // ---------- 子表列定义（行内编辑） ----------

  const contactColumns: TableProps<ProfileContactItem>['columns'] = [
    {
      title: t('systemOps.common.type'),
      dataIndex: 'contactType',
      width: 110,
      render: (value: string, _row, index) => (
        <Select
          size="small"
          options={contactTypeOptions(t)}
          value={value}
          style={{ width: 100 }}
          onChange={(next) => mutateRow(setContacts, index, { contactType: next })}
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.value'),
      dataIndex: 'contactValueMasked',
      render: (value: string | undefined, row, index) => (
        <Space size={4}>
          {row.id ? <Tag title={t('systemOps.common.masked')}>{value || '-'}</Tag> : null}
          <Input
            size="small"
            style={{ width: 220 }}
            placeholder={keepHint(t, row.id)}
            onChange={(e) =>
              mutateRow(setContacts, index, { contactValue: e.target.value || undefined })
            }
          />
        </Space>
      ),
    },
    {
      title: t('systemOps.userProfile.col.primary'),
      dataIndex: 'primary',
      width: 60,
      align: 'center',
      render: (value: boolean, _row, index) => (
        <Checkbox
          checked={value}
          onChange={(e) => mutateRow(setContacts, index, { primary: e.target.checked })}
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.verified'),
      dataIndex: 'verified',
      width: 80,
      align: 'center',
      render: (value: boolean) =>
        value ? (
          <Tag color="success">{t('systemOps.common.yes')}</Tag>
        ) : (
          <Tag color="default">{t('systemOps.common.no')}</Tag>
        ),
    },
    {
      title: t('systemOps.common.sortNo'),
      dataIndex: 'sortNo',
      width: 80,
      render: (value: number | undefined, _row, index) => (
        <InputNumber
          size="small"
          min={0}
          value={value ?? 0}
          style={{ width: 70 }}
          onChange={(next) => mutateRow(setContacts, index, { sortNo: next ?? 0 })}
        />
      ),
    },
    {
      title: t('systemOps.common.action'),
      key: 'action',
      width: 70,
      render: (_, row) => (
        <Button type="link" size="small" danger onClick={() => removeRow(setContacts, row)}>
          {t('systemOps.common.remove')}
        </Button>
      ),
    },
  ];

  const credentialColumns: TableProps<ProfileCredentialItem>['columns'] = [
    {
      title: t('systemOps.common.type'),
      dataIndex: 'credentialType',
      width: 120,
      render: (value: string, _row, index) => (
        <Select
          size="small"
          options={credentialTypeOptions(t)}
          value={value}
          style={{ width: 110 }}
          onChange={(next) => mutateRow(setCredentials, index, { credentialType: next })}
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.credentialNo'),
      dataIndex: 'credentialNoMasked',
      render: (value: string | undefined, row, index) => (
        <Space size={4}>
          {row.id ? <Tag title={t('systemOps.common.masked')}>{value || '-'}</Tag> : null}
          <Input
            size="small"
            style={{ width: 200 }}
            placeholder={keepHint(t, row.id)}
            onChange={(e) =>
              mutateRow(setCredentials, index, { credentialNo: e.target.value || undefined })
            }
          />
        </Space>
      ),
    },
    {
      title: t('systemOps.userProfile.col.holder'),
      dataIndex: 'holderName',
      width: 110,
      render: (value: string | undefined, _row, index) => (
        <Input
          size="small"
          style={{ width: 100 }}
          value={value}
          onChange={(e) => mutateRow(setCredentials, index, { holderName: e.target.value })}
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.primary'),
      dataIndex: 'primary',
      width: 60,
      align: 'center',
      render: (value: boolean, _row, index) => (
        <Checkbox
          checked={value}
          onChange={(e) => mutateRow(setCredentials, index, { primary: e.target.checked })}
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.issueDate'),
      dataIndex: 'issueDate',
      width: 130,
      render: (value: string | undefined, _row, index) => (
        <DatePicker
          size="small"
          style={{ width: 120 }}
          value={toDay(value)}
          onChange={(next) =>
            mutateRow(setCredentials, index, { issueDate: toDateStr(next ?? undefined) })
          }
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.expiryDate'),
      dataIndex: 'expiryDate',
      width: 130,
      render: (value: string | undefined, _row, index) => (
        <DatePicker
          size="small"
          style={{ width: 120 }}
          value={toDay(value)}
          onChange={(next) =>
            mutateRow(setCredentials, index, { expiryDate: toDateStr(next ?? undefined) })
          }
        />
      ),
    },
    {
      title: t('systemOps.common.action'),
      key: 'action',
      width: 120,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:user:profile:credential-view') && row.id ? (
            <Button type="link" size="small" onClick={() => void handleViewCredentialNo(row)}>
              {t('systemOps.userProfile.action.plaintext')}
            </Button>
          ) : null}
          <Button type="link" size="small" danger onClick={() => removeRow(setCredentials, row)}>
            {t('systemOps.common.remove')}
          </Button>
        </Space>
      ),
    },
  ];

  const educationColumns: TableProps<ProfileEducationItem>['columns'] = [
    {
      title: t('systemOps.userProfile.col.school'),
      dataIndex: 'school',
      width: 180,
      render: (value: string, _row, index) => (
        <Input
          size="small"
          value={value}
          onChange={(e) => mutateRow(setEducations, index, { school: e.target.value })}
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.major'),
      dataIndex: 'major',
      width: 150,
      render: (value: string | undefined, _row, index) => (
        <Input
          size="small"
          value={value}
          onChange={(e) => mutateRow(setEducations, index, { major: e.target.value })}
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.degree'),
      dataIndex: 'degree',
      width: 110,
      render: (value: string | undefined, _row, index) => (
        <Select
          size="small"
          allowClear
          options={degreeOptions(t)}
          value={value}
          style={{ width: 100 }}
          onChange={(next) => mutateRow(setEducations, index, { degree: next })}
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.startDate'),
      dataIndex: 'startDate',
      width: 130,
      render: (value: string | undefined, _row, index) => (
        <DatePicker
          size="small"
          style={{ width: 120 }}
          value={toDay(value)}
          onChange={(next) =>
            mutateRow(setEducations, index, { startDate: toDateStr(next ?? undefined) })
          }
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.endDate'),
      dataIndex: 'endDate',
      width: 130,
      render: (value: string | undefined, _row, index) => (
        <DatePicker
          size="small"
          style={{ width: 120 }}
          value={toDay(value)}
          onChange={(next) =>
            mutateRow(setEducations, index, { endDate: toDateStr(next ?? undefined) })
          }
        />
      ),
    },
    {
      title: t('systemOps.common.sortNo'),
      dataIndex: 'sortNo',
      width: 80,
      render: (value: number | undefined, _row, index) => (
        <InputNumber
          size="small"
          min={0}
          value={value ?? 0}
          style={{ width: 70 }}
          onChange={(next) => mutateRow(setEducations, index, { sortNo: next ?? 0 })}
        />
      ),
    },
    {
      title: t('systemOps.common.action'),
      key: 'action',
      width: 70,
      render: (_, row) => (
        <Button type="link" size="small" danger onClick={() => removeRow(setEducations, row)}>
          {t('systemOps.common.remove')}
        </Button>
      ),
    },
  ];

  const employmentColumns: TableProps<ProfileEmploymentItem>['columns'] = [
    {
      title: t('systemOps.userProfile.col.company'),
      dataIndex: 'company',
      width: 180,
      render: (value: string, _row, index) => (
        <Input
          size="small"
          value={value}
          onChange={(e) => mutateRow(setEmployments, index, { company: e.target.value })}
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.department'),
      dataIndex: 'department',
      width: 130,
      render: (value: string | undefined, _row, index) => (
        <Input
          size="small"
          value={value}
          onChange={(e) => mutateRow(setEmployments, index, { department: e.target.value })}
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.position'),
      dataIndex: 'position',
      width: 130,
      render: (value: string | undefined, _row, index) => (
        <Input
          size="small"
          value={value}
          onChange={(e) => mutateRow(setEmployments, index, { position: e.target.value })}
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.employmentType'),
      dataIndex: 'employmentType',
      width: 110,
      render: (value: string | undefined, _row, index) => (
        <Select
          size="small"
          allowClear
          options={employmentTypeOptions(t)}
          value={value}
          style={{ width: 100 }}
          onChange={(next) => mutateRow(setEmployments, index, { employmentType: next })}
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.workStartDate'),
      dataIndex: 'startDate',
      width: 130,
      render: (value: string | undefined, _row, index) => (
        <DatePicker
          size="small"
          style={{ width: 120 }}
          value={toDay(value)}
          onChange={(next) =>
            mutateRow(setEmployments, index, { startDate: toDateStr(next ?? undefined) })
          }
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.workEndDate'),
      dataIndex: 'endDate',
      width: 130,
      render: (value: string | undefined, _row, index) => (
        <DatePicker
          size="small"
          style={{ width: 120 }}
          value={toDay(value)}
          onChange={(next) =>
            mutateRow(setEmployments, index, { endDate: toDateStr(next ?? undefined) })
          }
        />
      ),
    },
    {
      title: t('systemOps.common.action'),
      key: 'action',
      width: 70,
      render: (_, row) => (
        <Button type="link" size="small" danger onClick={() => removeRow(setEmployments, row)}>
          {t('systemOps.common.remove')}
        </Button>
      ),
    },
  ];

  const emergencyColumns: TableProps<ProfileEmergencyContactItem>['columns'] = [
    {
      title: t('systemOps.userProfile.col.name'),
      dataIndex: 'contactName',
      width: 140,
      render: (value: string, _row, index) => (
        <Input
          size="small"
          value={value}
          onChange={(e) => mutateRow(setEmergencyContacts, index, { contactName: e.target.value })}
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.relationship'),
      dataIndex: 'relationship',
      width: 120,
      render: (value: string, _row, index) => (
        <Select
          size="small"
          options={relationshipOptions(t)}
          value={value}
          style={{ width: 110 }}
          onChange={(next) => mutateRow(setEmergencyContacts, index, { relationship: next })}
        />
      ),
    },
    {
      title: t('systemOps.userProfile.col.phone'),
      dataIndex: 'phoneMasked',
      render: (value: string | undefined, row, index) => (
        <Space size={4}>
          {row.id ? <Tag title={t('systemOps.common.masked')}>{value || '-'}</Tag> : null}
          <Input
            size="small"
            style={{ width: 180 }}
            placeholder={keepHint(t, row.id)}
            onChange={(e) =>
              mutateRow(setEmergencyContacts, index, { phone: e.target.value || undefined })
            }
          />
        </Space>
      ),
    },
    {
      title: t('systemOps.common.sortNo'),
      dataIndex: 'sortNo',
      width: 80,
      render: (value: number | undefined, _row, index) => (
        <InputNumber
          size="small"
          min={0}
          value={value ?? 0}
          style={{ width: 70 }}
          onChange={(next) => mutateRow(setEmergencyContacts, index, { sortNo: next ?? 0 })}
        />
      ),
    },
    {
      title: t('systemOps.common.action'),
      key: 'action',
      width: 70,
      render: (_, row) => (
        <Button
          type="link"
          size="small"
          danger
          onClick={() => removeRow(setEmergencyContacts, row)}
        >
          {t('systemOps.common.remove')}
        </Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={12} style={{ width: '100%' }}>
      <Card title={t('systemOps.userProfile.card.selectUser')}>
        <Space>
          <Select
            showSearch
            allowClear
            filterOption={false}
            loading={userSearching}
            options={userOptions}
            value={userId}
            style={{ width: 320 }}
            onSearch={(keyword) => void searchUsers(keyword)}
            onChange={(value) => (value ? handleUserSelect(value) : setUserId(undefined))}
            placeholder={t('systemOps.userProfile.searchUserPlaceholder')}
          />
          {profileLoading ? <span>{t('systemOps.userProfile.loading')}</span> : null}
        </Space>
      </Card>

      {profile ? (
        <>
          <Card title={t('systemOps.userProfile.card.basic')}>
            <Form form={basicForm} layout="inline" style={{ rowGap: 8 }}>
              <Form.Item name="gender" label={t('systemOps.userProfile.field.gender')}>
                <Select allowClear options={genderOptions(t)} style={{ width: 100 }} />
              </Form.Item>
              <Form.Item name="birthDate" label={t('systemOps.userProfile.field.birthDate')}>
                <DatePicker style={{ width: 140 }} />
              </Form.Item>
              <Form.Item name="nation" label={t('systemOps.userProfile.field.nation')}>
                <Input style={{ width: 100 }} maxLength={32} />
              </Form.Item>
              <Form.Item
                name="politicalStatus"
                label={t('systemOps.userProfile.field.politicalStatus')}
              >
                <Input style={{ width: 120 }} maxLength={32} />
              </Form.Item>
              <Form.Item
                name="maritalStatus"
                label={t('systemOps.userProfile.field.maritalStatus')}
              >
                <Input style={{ width: 120 }} maxLength={32} />
              </Form.Item>
              <Form.Item name="birthPlace" label={t('systemOps.userProfile.field.birthPlace')}>
                <Input style={{ width: 160 }} maxLength={128} />
              </Form.Item>
              <Form.Item name="homeAddress" label={t('systemOps.userProfile.field.homeAddress')}>
                <Input style={{ width: 240 }} maxLength={255} />
              </Form.Item>
              <Form.Item name="remarks" label={t('systemOps.userProfile.field.remarks')}>
                <Input style={{ width: 240 }} maxLength={255} />
              </Form.Item>
            </Form>
          </Card>

          <Card
            title={t('systemOps.userProfile.card.education')}
            extra={
              <Button
                size="small"
                onClick={() =>
                  setEducations((prev) => [
                    ...prev,
                    { school: '', degree: undefined, sortNo: prev.length },
                  ])
                }
              >
                {t('systemOps.common.addRow')}
              </Button>
            }
          >
            <Table<ProfileEducationItem>
              rowKey={(row) => row.id ?? `edu-${row.school}-${row.sortNo}`}
              size="small"
              columns={educationColumns}
              dataSource={educations}
              pagination={false}
              locale={{ emptyText: t('systemOps.userProfile.empty.education') }}
            />
          </Card>

          <Card
            title={t('systemOps.userProfile.card.employment')}
            extra={
              <Button
                size="small"
                onClick={() =>
                  setEmployments((prev) => [
                    ...prev,
                    { company: '', employmentType: undefined, sortNo: prev.length },
                  ])
                }
              >
                {t('systemOps.common.addRow')}
              </Button>
            }
          >
            <Table<ProfileEmploymentItem>
              rowKey={(row) => row.id ?? `emp-${row.company}-${row.sortNo}`}
              size="small"
              columns={employmentColumns}
              dataSource={employments}
              pagination={false}
              locale={{ emptyText: t('systemOps.userProfile.empty.employment') }}
            />
          </Card>

          <Card
            title={t('systemOps.userProfile.card.emergency')}
            extra={
              <Button
                size="small"
                onClick={() =>
                  setEmergencyContacts((prev) => [
                    ...prev,
                    { contactName: '', relationship: 'OTHER', sortNo: prev.length },
                  ])
                }
              >
                {t('systemOps.common.addRow')}
              </Button>
            }
          >
            <Alert
              style={{ marginBottom: 8 }}
              type="info"
              showIcon
              message={t('systemOps.userProfile.alert.emergency')}
            />
            <Table<ProfileEmergencyContactItem>
              rowKey={(row) => row.id ?? `emg-${row.contactName}-${row.sortNo}`}
              size="small"
              columns={emergencyColumns}
              dataSource={emergencyContacts}
              pagination={false}
              locale={{ emptyText: t('systemOps.userProfile.empty.emergency') }}
            />
          </Card>

          <Card
            title={t('systemOps.userProfile.card.contacts')}
            extra={
              <Button
                size="small"
                onClick={() =>
                  setContacts((prev) => [
                    ...prev,
                    {
                      contactType: 'MOBILE',
                      contactValue: undefined,
                      primary: false,
                      verified: false,
                      sortNo: prev.length,
                    },
                  ])
                }
              >
                {t('systemOps.common.addRow')}
              </Button>
            }
          >
            <Alert
              style={{ marginBottom: 8 }}
              type="info"
              showIcon
              message={t('systemOps.userProfile.alert.contact')}
            />
            <Table<ProfileContactItem>
              rowKey={(row) => row.id ?? `ctt-${row.contactType}-${row.sortNo}`}
              size="small"
              columns={contactColumns}
              dataSource={contacts}
              pagination={false}
              locale={{ emptyText: t('systemOps.userProfile.empty.contact') }}
            />
          </Card>

          <Card
            title={t('systemOps.userProfile.card.credentials')}
            extra={
              <Button
                size="small"
                onClick={() =>
                  setCredentials((prev) => [
                    ...prev,
                    {
                      credentialType: 'ID_CARD',
                      credentialNo: undefined,
                      primary: false,
                      sortNo: prev.length,
                    },
                  ])
                }
              >
                {t('systemOps.common.addRow')}
              </Button>
            }
          >
            <Alert
              style={{ marginBottom: 8 }}
              type="info"
              showIcon
              message={t('systemOps.userProfile.alert.credential')}
            />
            <Table<ProfileCredentialItem>
              rowKey={(row) => row.id ?? `crd-${row.credentialType}-${row.holderName ?? ''}`}
              size="small"
              columns={credentialColumns}
              dataSource={credentials}
              pagination={false}
              locale={{ emptyText: t('systemOps.userProfile.empty.credential') }}
            />
          </Card>

          {profile.secretQuestions?.length ? (
            <Card title={t('systemOps.userProfile.card.secretQuestions')}>
              <Alert
                style={{ marginBottom: 8 }}
                type="warning"
                showIcon
                message={t('systemOps.userProfile.alert.secret')}
              />
              <Table
                size="small"
                rowKey={(row) => row.id ?? row.question}
                columns={[
                  { title: t('systemOps.userProfile.col.question'), dataIndex: 'question' },
                  { title: t('systemOps.common.sortNo'), dataIndex: 'sortNo', width: 80 },
                ]}
                dataSource={profile.secretQuestions}
                pagination={false}
              />
            </Card>
          ) : null}

          <Card>
            <Space>
              <AuthButton
                perm="sys:user:profile:save"
                type="primary"
                loading={saving}
                onClick={() => void handleSave()}
              >
                {t('systemOps.userProfile.action.save')}
              </AuthButton>
              {has('sys:user:profile:delete') && (
                <Button danger onClick={handleDeleteProfile}>
                  {t('systemOps.userProfile.action.delete')}
                </Button>
              )}
            </Space>
          </Card>
        </>
      ) : null}
    </Space>
  );
}

/** 按 index 原位更新行字段（不改变数组引用其余元素） */
function mutateRow<T>(
  setter: (updater: (prev: T[]) => T[]) => void,
  index: number,
  patch: Partial<T>,
) {
  setter((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));
}

/** 按 id 或引用移除行 */
function removeRow<T extends { id?: string }>(
  setter: (updater: (prev: T[]) => T[]) => void,
  row: T,
) {
  setter((prev) => prev.filter((item) => (row.id ? item.id !== row.id : item !== row)));
}
