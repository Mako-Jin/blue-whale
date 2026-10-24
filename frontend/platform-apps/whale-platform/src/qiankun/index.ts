// qiankun 基座入口 - 注册微应用并监听路由变化

import { AppEvents, createLogger, eventBus } from '@bluewhale/whale-common-tools';
import {
  registerMicroApps,
  start,
  addGlobalUncaughtErrorHandler,
  initGlobalState,
  type MicroAppStateActions,
} from 'qiankun';
import { apps } from './apps';

const logger = createLogger('whale-platform › qiankun');

type SubAppErrorListener = (appName: string) => void;

const subAppErrorListeners = new Set<SubAppErrorListener>();

// 订阅子应用加载失败事件，返回取消订阅函数（供 SubAppContainer 渲染错误兜底 UI）
export function onSubAppError(listener: SubAppErrorListener): () => void {
  subAppErrorListeners.add(listener);
  return () => {
    subAppErrorListeners.delete(listener);
  };
}

function notifySubAppError(message: string): void {
  const failed = apps.find((app) => message.includes(app.name));
  if (!failed) return;
  logger.error(`子应用 ${failed.name} 加载失败`, message);
  // 审计：子应用加载失败必须留痕，便于定位发布/资源 404 问题
  eventBus.emit(AppEvents.SUB_APP_ERROR, { appName: failed.name, message });
  subAppErrorListeners.forEach((listener) => listener(failed.name));
}

let actions: MicroAppStateActions;

export function initQiankun(): void {
  registerMicroApps(apps);

  addGlobalUncaughtErrorHandler((event) => {
    const msg = event instanceof Error ? event.message : typeof event === 'string' ? event : '';
    if (msg) {
      notifySubAppError(msg);
    } else {
      logger.error('qiankun global error', event);
    }
  });

  actions = initGlobalState({ token: 'bluewhale-platform-token' });

  start({
    sandbox: { experimentalStyleIsolation: true },
    singular: true,
    prefetch: 'all',
  });
}

export function getQiankunActions(): MicroAppStateActions {
  return actions;
}
