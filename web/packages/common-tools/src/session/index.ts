/**
 * session · 会话存取
 *
 * 只管"令牌与身份放在哪、变了通知谁"，**不认识任何接口、不碰任何界面、
 * 不 import qiankun** —— 因此同一份实现既能被基座驱动，也能被子应用单独驱动。
 * 归属与分工见 `docs/decisions/0018-权限在微前端中的归属.md`。
 */

export { createMemoryStorage, createSessionStore } from './store';
export type { SessionStoreConfig } from './store';

export type {
  SessionListener,
  SessionSnapshot,
  SessionStorage,
  SessionStore,
  SessionTokens,
  SessionUpdate,
  SessionUser,
} from './types';
