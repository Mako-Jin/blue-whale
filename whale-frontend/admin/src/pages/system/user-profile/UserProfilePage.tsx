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

const GENDER_OPTIONS = [
  { value: 'MALE', label: '男' },
  { value: 'FEMALE', label: '女' },
  { value: 'OTHER', label: '其他' },
];

const DEGREE_OPTIONS = [
  { value: 'DOCTOR', label: '博士' },
  { value: 'MASTER', label: '硕士' },
  { value: 'BACHELOR', label: '本科' },
  { value: 'ASSOCIATE', label: '专科' },
  { value: 'OTHER', label: '其他' },
];

const EMPLOYMENT_TYPE_OPTIONS = [
  { value: 'FORMAL', label: '正式' },
  { value: 'CONTRACT', label: '合同制' },
  { value: 'INTERN', label: '实习' },
  { value: 'OUTSOURCING', label: '外包' },
];

const RELATIONSHIP_OPTIONS = [
  { value: 'PARENT', label: '父母' },
  { value: 'SPOUSE', label: '配偶' },
  { value: 'CHILD', label: '子女' },
  { value: 'SIBLING', label: '兄弟姐妹' },
  { value: 'FRIEND', label: '朋友' },
  { value: 'OTHER', label: '其他' },
];

const CONTACT_TYPE_OPTIONS = [
  { value: 'MOBILE', label: '手机' },
  { value: 'EMAIL', label: '邮箱' },
  { value: 'WECHAT', label: '微信' },
  { value: 'DINGTALK', label: '钉钉' },
  { value: 'OTHER', label: '其他' },
];

const CREDENTIAL_TYPE_OPTIONS = [
  { value: 'ID_CARD', label: '身份证' },
  { value: 'PASSPORT', label: '护照' },
  { value: 'HK_MO_PASS', label: '港澳通行证' },
  { value: 'OTHER', label: '其他' },
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
function keepHint(rowId?: string): string {
  return rowId ? '留空保留原值' : '必填';
}

export default function UserProfilePage() {
  const { message, modal } = App.useApp();
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
      showError(error, '用户搜索失败');
    } finally {
      setUserSearching(false);
    }
  };

  const applyProfile = useCallback((data: UserProfileVO) => {
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
  }, [basicForm]);

  const loadProfile = async (targetUserId: string) => {
    setProfileLoading(true);
    try {
      const data = await viewUserProfile(targetUserId);
      applyProfile(data);
    } catch (error) {
      if (error instanceof ApiError && error.code === 404) {
        // 尚未建档：进入空白可建档状态
        applyProfile({ ...EMPTY_PROFILE, userId: targetUserId });
        message.info('该用户尚未建档，填写后保存即可创建');
      } else {
        setProfile(null);
        showError(error, '档案加载失败');
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
      message.error('新增的联系方式需要填写具体值');
      return;
    }
    if (credentials.some((row) => !row.id && !row.credentialNo)) {
      message.error('新增的证件需要填写证件号');
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
      message.success('档案已保存');
      applyProfile(saved);
    } catch (error) {
      if (error instanceof Error && error.message !== 'Validation failed') {
        showError(error, '保存失败');
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
      title: '确认删除该用户的身份档案？',
      content: '主表逻辑删除并清理全部子表数据，不可恢复。',
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        await deleteUserProfile(userId);
        message.success('档案已删除');
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
        title: '证件号明文（已审计）',
        content: `${row.credentialType}: ${plain}`,
      });
    } catch (error) {
      showError(error, '明文查看失败');
    }
  };

  // ---------- 子表列定义（行内编辑） ----------

  const contactColumns: TableProps<ProfileContactItem>['columns'] = [
    {
      title: '类型',
      dataIndex: 'contactType',
      width: 110,
      render: (value: string, _row, index) => (
        <Select
          size="small"
          options={CONTACT_TYPE_OPTIONS}
          value={value}
          style={{ width: 100 }}
          onChange={(next) => mutateRow(setContacts, index, { contactType: next })}
        />
      ),
    },
    {
      title: '值',
      dataIndex: 'contactValueMasked',
      render: (value: string | undefined, row, index) => (
        <Space size={4}>
          {row.id ? <Tag title="系统掩码">{value || '-'}</Tag> : null}
          <Input
            size="small"
            style={{ width: 220 }}
            placeholder={keepHint(row.id)}
            onChange={(e) => mutateRow(setContacts, index, { contactValue: e.target.value || undefined })}
          />
        </Space>
      ),
    },
    {
      title: '主',
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
      title: '已验证',
      dataIndex: 'verified',
      width: 80,
      align: 'center',
      render: (value: boolean) =>
        value ? <Tag color="success">是</Tag> : <Tag color="default">否</Tag>,
    },
    {
      title: '排序',
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
      title: '操作',
      key: 'action',
      width: 70,
      render: (_, row) => (
        <Button type="link" size="small" danger onClick={() => removeRow(setContacts, row)}>
          移除
        </Button>
      ),
    },
  ];

  const credentialColumns: TableProps<ProfileCredentialItem>['columns'] = [
    {
      title: '类型',
      dataIndex: 'credentialType',
      width: 120,
      render: (value: string, _row, index) => (
        <Select
          size="small"
          options={CREDENTIAL_TYPE_OPTIONS}
          value={value}
          style={{ width: 110 }}
          onChange={(next) => mutateRow(setCredentials, index, { credentialType: next })}
        />
      ),
    },
    {
      title: '证件号',
      dataIndex: 'credentialNoMasked',
      render: (value: string | undefined, row, index) => (
        <Space size={4}>
          {row.id ? <Tag title="系统掩码">{value || '-'}</Tag> : null}
          <Input
            size="small"
            style={{ width: 200 }}
            placeholder={keepHint(row.id)}
            onChange={(e) =>
              mutateRow(setCredentials, index, { credentialNo: e.target.value || undefined })
            }
          />
        </Space>
      ),
    },
    {
      title: '持有人',
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
      title: '主',
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
      title: '签发日期',
      dataIndex: 'issueDate',
      width: 130,
      render: (value: string | undefined, _row, index) => (
        <DatePicker
          size="small"
          style={{ width: 120 }}
          value={toDay(value)}
          onChange={(next) => mutateRow(setCredentials, index, { issueDate: toDateStr(next ?? undefined) })}
        />
      ),
    },
    {
      title: '有效期至',
      dataIndex: 'expiryDate',
      width: 130,
      render: (value: string | undefined, _row, index) => (
        <DatePicker
          size="small"
          style={{ width: 120 }}
          value={toDay(value)}
          onChange={(next) => mutateRow(setCredentials, index, { expiryDate: toDateStr(next ?? undefined) })}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:user:profile:credential-view') && row.id ? (
            <Button type="link" size="small" onClick={() => void handleViewCredentialNo(row)}>
              明文
            </Button>
          ) : null}
          <Button type="link" size="small" danger onClick={() => removeRow(setCredentials, row)}>
            移除
          </Button>
        </Space>
      ),
    },
  ];

  const educationColumns: TableProps<ProfileEducationItem>['columns'] = [
    {
      title: '学校',
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
      title: '专业',
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
      title: '学历',
      dataIndex: 'degree',
      width: 110,
      render: (value: string | undefined, _row, index) => (
        <Select
          size="small"
          allowClear
          options={DEGREE_OPTIONS}
          value={value}
          style={{ width: 100 }}
          onChange={(next) => mutateRow(setEducations, index, { degree: next })}
        />
      ),
    },
    {
      title: '入学时间',
      dataIndex: 'startDate',
      width: 130,
      render: (value: string | undefined, _row, index) => (
        <DatePicker
          size="small"
          style={{ width: 120 }}
          value={toDay(value)}
          onChange={(next) => mutateRow(setEducations, index, { startDate: toDateStr(next ?? undefined) })}
        />
      ),
    },
    {
      title: '毕业时间',
      dataIndex: 'endDate',
      width: 130,
      render: (value: string | undefined, _row, index) => (
        <DatePicker
          size="small"
          style={{ width: 120 }}
          value={toDay(value)}
          onChange={(next) => mutateRow(setEducations, index, { endDate: toDateStr(next ?? undefined) })}
        />
      ),
    },
    {
      title: '排序',
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
      title: '操作',
      key: 'action',
      width: 70,
      render: (_, row) => (
        <Button type="link" size="small" danger onClick={() => removeRow(setEducations, row)}>
          移除
        </Button>
      ),
    },
  ];

  const employmentColumns: TableProps<ProfileEmploymentItem>['columns'] = [
    {
      title: '公司',
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
      title: '部门',
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
      title: '职位',
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
      title: '用工性质',
      dataIndex: 'employmentType',
      width: 110,
      render: (value: string | undefined, _row, index) => (
        <Select
          size="small"
          allowClear
          options={EMPLOYMENT_TYPE_OPTIONS}
          value={value}
          style={{ width: 100 }}
          onChange={(next) => mutateRow(setEmployments, index, { employmentType: next })}
        />
      ),
    },
    {
      title: '入职时间',
      dataIndex: 'startDate',
      width: 130,
      render: (value: string | undefined, _row, index) => (
        <DatePicker
          size="small"
          style={{ width: 120 }}
          value={toDay(value)}
          onChange={(next) => mutateRow(setEmployments, index, { startDate: toDateStr(next ?? undefined) })}
        />
      ),
    },
    {
      title: '离职时间',
      dataIndex: 'endDate',
      width: 130,
      render: (value: string | undefined, _row, index) => (
        <DatePicker
          size="small"
          style={{ width: 120 }}
          value={toDay(value)}
          onChange={(next) => mutateRow(setEmployments, index, { endDate: toDateStr(next ?? undefined) })}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 70,
      render: (_, row) => (
        <Button type="link" size="small" danger onClick={() => removeRow(setEmployments, row)}>
          移除
        </Button>
      ),
    },
  ];

  const emergencyColumns: TableProps<ProfileEmergencyContactItem>['columns'] = [
    {
      title: '姓名',
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
      title: '关系',
      dataIndex: 'relationship',
      width: 120,
      render: (value: string, _row, index) => (
        <Select
          size="small"
          options={RELATIONSHIP_OPTIONS}
          value={value}
          style={{ width: 110 }}
          onChange={(next) => mutateRow(setEmergencyContacts, index, { relationship: next })}
        />
      ),
    },
    {
      title: '电话',
      dataIndex: 'phoneMasked',
      render: (value: string | undefined, row, index) => (
        <Space size={4}>
          {row.id ? <Tag title="系统掩码">{value || '-'}</Tag> : null}
          <Input
            size="small"
            style={{ width: 180 }}
            placeholder={keepHint(row.id)}
            onChange={(e) => mutateRow(setEmergencyContacts, index, { phone: e.target.value || undefined })}
          />
        </Space>
      ),
    },
    {
      title: '排序',
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
      title: '操作',
      key: 'action',
      width: 70,
      render: (_, row) => (
        <Button type="link" size="small" danger onClick={() => removeRow(setEmergencyContacts, row)}>
          移除
        </Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={12} style={{ width: '100%' }}>
      <Card title="选择用户">
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
            placeholder="输入关键字搜索用户（昵称 / 用户名）"
          />
          {profileLoading ? <span>档案加载中…</span> : null}
        </Space>
      </Card>

      {profile ? (
        <>
          <Card title="基本信息">
            <Form form={basicForm} layout="inline" style={{ rowGap: 8 }}>
              <Form.Item name="gender" label="性别">
                <Select allowClear options={GENDER_OPTIONS} style={{ width: 100 }} />
              </Form.Item>
              <Form.Item name="birthDate" label="出生日期">
                <DatePicker style={{ width: 140 }} />
              </Form.Item>
              <Form.Item name="nation" label="民族">
                <Input style={{ width: 100 }} maxLength={32} />
              </Form.Item>
              <Form.Item name="politicalStatus" label="政治面貌">
                <Input style={{ width: 120 }} maxLength={32} />
              </Form.Item>
              <Form.Item name="maritalStatus" label="婚姻状况">
                <Input style={{ width: 120 }} maxLength={32} />
              </Form.Item>
              <Form.Item name="birthPlace" label="籍贯">
                <Input style={{ width: 160 }} maxLength={128} />
              </Form.Item>
              <Form.Item name="homeAddress" label="家庭住址">
                <Input style={{ width: 240 }} maxLength={255} />
              </Form.Item>
              <Form.Item name="remarks" label="备注">
                <Input style={{ width: 240 }} maxLength={255} />
              </Form.Item>
            </Form>
          </Card>

          <Card
            title="教育经历"
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
                添加一行
              </Button>
            }
          >
            <Table<ProfileEducationItem>
              rowKey={(row) => row.id ?? `edu-${row.school}-${row.sortNo}`}
              size="small"
              columns={educationColumns}
              dataSource={educations}
              pagination={false}
              locale={{ emptyText: '暂无教育经历' }}
            />
          </Card>

          <Card
            title="工作经历"
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
                添加一行
              </Button>
            }
          >
            <Table<ProfileEmploymentItem>
              rowKey={(row) => row.id ?? `emp-${row.company}-${row.sortNo}`}
              size="small"
              columns={employmentColumns}
              dataSource={employments}
              pagination={false}
              locale={{ emptyText: '暂无工作经历' }}
            />
          </Card>

          <Card
            title="家庭成员（紧急联系人）"
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
                添加一行
              </Button>
            }
          >
            <Alert
              style={{ marginBottom: 8 }}
              type="info"
              showIcon
              message="既有联系人电话为掩码显示：新电话留空 = 保留原值；清空请整行移除"
            />
            <Table<ProfileEmergencyContactItem>
              rowKey={(row) => row.id ?? `emg-${row.contactName}-${row.sortNo}`}
              size="small"
              columns={emergencyColumns}
              dataSource={emergencyContacts}
              pagination={false}
              locale={{ emptyText: '暂无紧急联系人' }}
            />
          </Card>

          <Card
            title="联系方式"
            extra={
              <Button
                size="small"
                onClick={() =>
                  setContacts((prev) => [
                    ...prev,
                    { contactType: 'MOBILE', contactValue: undefined, primary: false, verified: false, sortNo: prev.length },
                  ])
                }
              >
                添加一行
              </Button>
            }
          >
            <Alert
              style={{ marginBottom: 8 }}
              type="info"
              showIcon
              message="既有联系方式为掩码显示：新值留空 = 保留原值；清空请整行移除"
            />
            <Table<ProfileContactItem>
              rowKey={(row) => row.id ?? `ctt-${row.contactType}-${row.sortNo}`}
              size="small"
              columns={contactColumns}
              dataSource={contacts}
              pagination={false}
              locale={{ emptyText: '暂无联系方式' }}
            />
          </Card>

          <Card
            title="证件"
            extra={
              <Button
                size="small"
                onClick={() =>
                  setCredentials((prev) => [
                    ...prev,
                    { credentialType: 'ID_CARD', credentialNo: undefined, primary: false, sortNo: prev.length },
                  ])
                }
              >
                添加一行
              </Button>
            }
          >
            <Alert
              style={{ marginBottom: 8 }}
              type="info"
              showIcon
              message="既有证件号为掩码显示：新号码留空 = 保留原值；明文查看需专用权限并产生审计"
            />
            <Table<ProfileCredentialItem>
              rowKey={(row) => row.id ?? `crd-${row.credentialType}-${row.holderName ?? ''}`}
              size="small"
              columns={credentialColumns}
              dataSource={credentials}
              pagination={false}
              locale={{ emptyText: '暂无证件' }}
            />
          </Card>

          {profile.secretQuestions?.length ? (
            <Card title="密保问答（只读）">
              <Alert
                style={{ marginBottom: 8 }}
                type="warning"
                showIcon
                message="密保答案不可读回，管理端不提供编辑；保存档案不会影响既有问答（保留原值）"
              />
              <Table
                size="small"
                rowKey={(row) => row.id ?? row.question}
                columns={[{ title: '问题', dataIndex: 'question' }, { title: '排序', dataIndex: 'sortNo', width: 80 }]}
                dataSource={profile.secretQuestions}
                pagination={false}
              />
            </Card>
          ) : null}

          <Card>
            <Space>
              <AuthButton perm="sys:user:profile:save" type="primary" loading={saving} onClick={() => void handleSave()}>
                保存档案
              </AuthButton>
              {has('sys:user:profile:delete') && (
                <Button danger onClick={handleDeleteProfile}>
                  删除档案
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
function mutateRow<T>(setter: (updater: (prev: T[]) => T[]) => void, index: number, patch: Partial<T>) {
  setter((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));
}

/** 按 id 或引用移除行 */
function removeRow<T extends { id?: string }>(
  setter: (updater: (prev: T[]) => T[]) => void,
  row: T,
) {
  setter((prev) => prev.filter((item) => (row.id ? item.id !== row.id : item !== row)));
}
