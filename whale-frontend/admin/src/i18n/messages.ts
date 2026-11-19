// 管理端文案字典：以中文为键集合基线，英文必须覆盖全部键

/** 中文文案（键集合基线） */
const zhCN = {
  'app.title': '后台管理平台',
  'app.loginTitle': '后台管理平台 · 统一身份认证',
  'app.logout': '退出登录',
  'app.logoutSuccess': '已退出登录，令牌已加入黑名单',
  'nav.dashboard': '仪表盘',
  'nav.users': '用户管理',
  'nav.roles': '角色管理',
  'nav.menus': '菜单管理',
  'nav.tenants': '租户管理',
  'nav.components': '组件示例',
  'tenant.label': '租户',
  'tenant.placeholder': '租户',
  'tenant.switchFailed': '切换租户失败',
  'login.success': '登录成功',
  'login.failed': '登录失败，请稍后重试',
  'login.locked': '账号已锁定，{remaining} 后可重试',
  'login.submit': '登录',
  'login.submitLocked': '已锁定，{remaining} 后可重试',
  'login.tenantPlaceholder': '租户编码（默认 default）',
  'login.usernamePlaceholder': '用户名（默认 admin）',
  'login.passwordPlaceholder': '密码',
  'login.passwordChanged': '密码修改成功，请使用新密码重新登录',
  'language.label': '语言',
  'language.zh-CN': '中文',
  'language.en-US': 'English',
} as const;

/** 文案键 */
export type MessageKey = keyof typeof zhCN;

/** 英文文案 */
const enUS: Record<MessageKey, string> = {
  'app.title': 'Admin Console',
  'app.loginTitle': 'Admin Console · Unified Identity',
  'app.logout': 'Sign out',
  'app.logoutSuccess': 'Signed out, token has been revoked',
  'nav.dashboard': 'Dashboard',
  'nav.users': 'Users',
  'nav.roles': 'Roles',
  'nav.menus': 'Menus',
  'nav.tenants': 'Tenants',
  'nav.components': 'Components',
  'tenant.label': 'Tenant',
  'tenant.placeholder': 'Tenant',
  'tenant.switchFailed': 'Failed to switch tenant',
  'login.success': 'Signed in',
  'login.failed': 'Sign-in failed, please try again later',
  'login.locked': 'Account locked, retry in {remaining}',
  'login.submit': 'Sign in',
  'login.submitLocked': 'Locked, retry in {remaining}',
  'login.tenantPlaceholder': 'Tenant code (default: default)',
  'login.usernamePlaceholder': 'Username (default: admin)',
  'login.passwordPlaceholder': 'Password',
  'login.passwordChanged': 'Password changed, please sign in again',
  'language.label': 'Language',
  'language.zh-CN': '中文',
  'language.en-US': 'English',
};

/** 全部语言字典 */
export const messages = {
  'zh-CN': zhCN,
  'en-US': enUS,
} as const;
