// 强制改密弹窗的双语文案。
//
// 该弹窗被 6 个带登录页的应用共用，其中只有 admin 接入了 I18nProvider。
// 文案集中在此处，组件本身不含任何硬编码中文；服务端返回的 payload.message
// 由后端按请求 Locale 解析，前端原样透传，不在本表内。

import type { UiLang } from './language';

/** 弹窗文案键集合（中英文必须一一对应） */
export interface ChangePasswordMessages {
  title: string;
  description: string;
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
  strengthHint: string;
  submit: string;
  submitting: string;
  errorOldPasswordRequired: string;
  errorTooShort: string;
  errorNeedLower: string;
  errorNeedUpper: string;
  errorNeedDigit: string;
  errorNeedSpecial: string;
  errorConfirmMismatch: string;
  errorSameAsOld: string;
  errorTokenInvalid: string;
  errorReauth: string;
  errorNetwork: string;
  /** 带 {status} 占位符 */
  errorHttp: string;
}

const zhCN: ChangePasswordMessages = {
  title: '请修改密码',
  description: '首次登录、密码被重置或密码已到期时，必须设置新密码后才能继续使用系统。',
  oldPassword: '原密码',
  newPassword: '新密码',
  confirmPassword: '确认新密码',
  strengthHint: '至少 12 位，且包含大写字母、小写字母、数字和特殊字符',
  submit: '修改密码并重新登录',
  submitting: '提交中…',
  errorOldPasswordRequired: '请输入原密码',
  errorTooShort: '密码长度至少 12 位',
  errorNeedLower: '密码需包含小写字母',
  errorNeedUpper: '密码需包含大写字母',
  errorNeedDigit: '密码需包含数字',
  errorNeedSpecial: '密码需包含特殊字符',
  errorConfirmMismatch: '两次输入的新密码不一致',
  errorSameAsOld: '新密码不能与原密码相同',
  errorTokenInvalid: '登录态已失效，请重新登录',
  errorReauth: '当前会话需要重新验证身份，请退出后重新登录再试',
  errorNetwork: '网络异常，请稍后重试',
  errorHttp: '密码修改失败（HTTP {status}）',
};

const enUS: ChangePasswordMessages = {
  title: 'Change password required',
  description:
    'On first sign-in, after a password reset, or when your password has expired, you must set a new password before continuing.',
  oldPassword: 'Current password',
  newPassword: 'New password',
  confirmPassword: 'Confirm new password',
  strengthHint: 'At least 12 characters, including uppercase and lowercase letters, digits, and special characters',
  submit: 'Change password and sign in',
  submitting: 'Submitting…',
  errorOldPasswordRequired: 'Please enter your current password',
  errorTooShort: 'Password must be at least 12 characters',
  errorNeedLower: 'Password must contain a lowercase letter',
  errorNeedUpper: 'Password must contain an uppercase letter',
  errorNeedDigit: 'Password must contain a digit',
  errorNeedSpecial: 'Password must contain a special character',
  errorConfirmMismatch: 'The two new passwords do not match',
  errorSameAsOld: 'New password must differ from the current password',
  errorTokenInvalid: 'Your session has expired. Please sign in again.',
  errorReauth: 'This session needs re-authentication. Please sign out and sign in again.',
  errorNetwork: 'Network error. Please try again later.',
  errorHttp: 'Failed to change password (HTTP {status})',
};

/** 按语言取弹窗文案 */
export const changePasswordMessages: Record<UiLang, ChangePasswordMessages> = {
  'zh-CN': zhCN,
  'en-US': enUS,
};
