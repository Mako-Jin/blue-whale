// 页面级单测公共工具：antd App 上下文 + 权限上下文注入 + i18n + 分页结果构造
import { App } from 'antd';
import { render, type RenderOptions } from '@testing-library/react';
import { PermissionProvider } from '@bluewhale/whale-ui';
import type { ReactElement } from 'react';
import { I18nProvider } from '../i18n';

/** 与 @bluewhale/http PageResult 字段一致（后端 Long→String 序列化，测试内联，避免牵出真实 HTTP 客户端） */
interface PageResult<T> {
  records: T[];
  total: string;
  size: string;
  current: string;
}

/** 渲染页面组件：包 i18n + App（message 上下文）+ PermissionProvider（按钮级权限） */
export function renderPage(ui: ReactElement, permissions: string[] = [], options?: RenderOptions) {
  return render(
    <I18nProvider>
      <App>
        <PermissionProvider permissions={permissions}>{ui}</PermissionProvider>
      </App>
    </I18nProvider>,
    options,
  );
}

/** 构造 ProTable.request 的分页返回体 */
export function pageResult<T>(records: T[]): PageResult<T> {
  return { records, total: String(records.length), size: '10', current: '1' };
}

/** 音乐运营模块权限码全集 */
export const MUSIC_PERMS = [
  'sys:music:song:add',
  'sys:music:song:edit',
  'sys:music:song:delete',
  'sys:music:song:publish',
  'sys:music:comment:audit',
  'sys:music:appeal:audit',
];
