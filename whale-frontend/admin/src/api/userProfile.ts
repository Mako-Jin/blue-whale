// 用户身份档案 API（经网关 → whale-service-auth /user-profile）
// 权限码：sys:user:profile:view/save/delete/credential-view（2390–2393）
// 读端为脱敏视图（证件号/电话/联系方式掩码）；保存时证件号与密保答案为明文，服务端加密；
// 子集合整体替换：保存请求即为目标状态（空数组=清空该类子表）。

import { del, get, put } from './client';

/** 联系方式条目 */
export interface ProfileContactItem {
  id?: string;
  /** MOBILE / EMAIL / WECHAT / DINGTALK / OTHER */
  contactType: string;
  /** 明文值（读端为空：后端把掩码放在 contactValue，这里落 contactValueMasked） */
  contactValue?: string;
  /** 读端掩码（仅展示，禁止回传） */
  contactValueMasked?: string;
  primary: boolean;
  verified: boolean;
  sortNo?: number;
}

/** 证件条目（读端证件号为掩码 credentialNoMasked；保存传明文 credentialNo） */
export interface ProfileCredentialItem {
  id?: string;
  /** ID_CARD / PASSPORT / HK_MO_PASS / OTHER */
  credentialType: string;
  credentialNoMasked?: string;
  /** 仅保存时传明文 */
  credentialNo?: string;
  holderName?: string;
  primary: boolean;
  issueDate?: string;
  expiryDate?: string;
}

/** 教育经历条目 */
export interface ProfileEducationItem {
  id?: string;
  school: string;
  major?: string;
  /** DOCTOR / MASTER / BACHELOR / ASSOCIATE / OTHER */
  degree?: string;
  startDate?: string;
  endDate?: string;
  sortNo?: number;
}

/** 工作经历条目 */
export interface ProfileEmploymentItem {
  id?: string;
  company: string;
  department?: string;
  position?: string;
  /** FORMAL / CONTRACT / INTERN / OUTSOURCING */
  employmentType?: string;
  startDate?: string;
  endDate?: string;
  sortNo?: number;
}

/** 紧急联系人条目（读端电话为掩码 phoneMasked；保存传明文 phone） */
export interface ProfileEmergencyContactItem {
  id?: string;
  contactName: string;
  /** PARENT / SPOUSE / CHILD / SIBLING / FRIEND / OTHER */
  relationship: string;
  phoneMasked?: string;
  /** 仅保存时传明文 */
  phone?: string;
  sortNo?: number;
}

/** 密保问题条目（读端不含答案；保存需传答案明文） */
export interface ProfileSecretQuestionItem {
  id?: string;
  question: string;
  /** 仅保存时传明文 */
  answer?: string;
  sortNo?: number;
}

/** 用户身份档案（脱敏视图，字段对齐后端 UserProfileResponse） */
export interface UserProfileVO {
  id: string;
  tenantId?: string;
  userId: string;
  gender?: string;
  birthDate?: string;
  birthPlace?: string;
  homeAddress?: string;
  nation?: string;
  politicalStatus?: string;
  maritalStatus?: string;
  remarks?: string;
  contacts: ProfileContactItem[];
  credentials: ProfileCredentialItem[];
  educations: ProfileEducationItem[];
  employments: ProfileEmploymentItem[];
  emergencyContacts: ProfileEmergencyContactItem[];
  secretQuestions: ProfileSecretQuestionItem[];
  createTime?: string;
  updateTime?: string;
}

/** 保存身份档案入参（整体替换；字段对齐后端 SaveUserProfileRequest） */
export interface SaveUserProfilePayload {
  gender?: string;
  birthDate?: string;
  birthPlace?: string;
  homeAddress?: string;
  nation?: string;
  politicalStatus?: string;
  maritalStatus?: string;
  remarks?: string;
  contacts?: ProfileContactItem[];
  credentials?: ProfileCredentialItem[];
  educations?: ProfileEducationItem[];
  employments?: ProfileEmploymentItem[];
  emergencyContacts?: ProfileEmergencyContactItem[];
  secretQuestions?: ProfileSecretQuestionItem[];
}

/** 查询用户身份档案（脱敏视图；无档案返回空结构由调用方处理 404） */
export function viewUserProfile(userId: string): Promise<UserProfileVO> {
  return get<UserProfileVO>(`/user-profile/v1/${userId}/view`);
}

/** 保存用户身份档案（无档案则建，子集合整体替换） */
export function saveUserProfile(
  userId: string,
  payload: SaveUserProfilePayload,
): Promise<UserProfileVO> {
  return put<UserProfileVO>(`/user-profile/v1/${userId}`, payload);
}

/** 删除用户身份档案（逻辑删除主表并清理子表） */
export function deleteUserProfile(userId: string): Promise<void> {
  return del<void>(`/user-profile/v1/${userId}`);
}

/** 查看证件号明文（专用权限 sys:user:profile:credential-view，产生审计） */
export function viewCredentialNo(userId: string, credentialId: string): Promise<string> {
  return get<string>(`/user-profile/v1/${userId}/credentials/${credentialId}`);
}
