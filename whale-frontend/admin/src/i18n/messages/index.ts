// 文案字典聚合：按业务模块拆分，中文为键集合基线，英文必须覆盖全部键

import type { Language } from '../language';
import { archiveMessages } from './archive';
import { assistantMessages } from './assistant';
import { configGatewayMessages } from './configGateway';
import { coreMessages } from './core';
import { demoMessages } from './demo';
import { deptMessages } from './dept';
import { fileMessages } from './file';
import { jobMessages } from './job';
import { menuPermissionMessages } from './menuPermission';
import { messageMessages } from './message';
import { musicMessages } from './music';
import { payMessages } from './pay';
import { systemAuthMessages } from './systemAuth';
import { systemOpsMessages } from './systemOps';
import { tenantMessages } from './tenant';
import { videoMessages } from './video';
import { videoCatalogMessages } from './videoCatalog';

/** 中文文案（各模块合并后的键集合基线） */
const zhCN = {
  ...coreMessages.zh,
  ...deptMessages.zh,
  ...menuPermissionMessages.zh,
  ...tenantMessages.zh,
  ...systemAuthMessages.zh,
  ...systemOpsMessages.zh,
  ...configGatewayMessages.zh,
  ...archiveMessages.zh,
  ...assistantMessages.zh,
  ...messageMessages.zh,
  ...fileMessages.zh,
  ...jobMessages.zh,
  ...musicMessages.zh,
  ...videoMessages.zh,
  ...videoCatalogMessages.zh,
  ...payMessages.zh,
  ...demoMessages.zh,
};

/** 文案键 */
export type MessageKey = keyof typeof zhCN;

/** 英文文案：键集合由中文基线约束，缺失即编译报错 */
const enUS: Record<MessageKey, string> = {
  ...coreMessages.en,
  ...deptMessages.en,
  ...menuPermissionMessages.en,
  ...tenantMessages.en,
  ...systemAuthMessages.en,
  ...systemOpsMessages.en,
  ...configGatewayMessages.en,
  ...archiveMessages.en,
  ...assistantMessages.en,
  ...messageMessages.en,
  ...fileMessages.en,
  ...jobMessages.en,
  ...musicMessages.en,
  ...videoMessages.en,
  ...videoCatalogMessages.en,
  ...payMessages.en,
  ...demoMessages.en,
};

/** 全部语言字典 */
export const messages: Record<Language, Record<MessageKey, string>> = {
  'zh-CN': zhCN,
  'en-US': enUS,
};
