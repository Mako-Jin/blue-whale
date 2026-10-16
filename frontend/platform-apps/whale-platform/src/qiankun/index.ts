// qiankun 基座入口 - 注册微应用并监听路由变化

import {
  registerMicroApps,
  start,
  addGlobalUncaughtErrorHandler,
  initGlobalState,
  type MicroAppStateActions,
} from 'qiankun';
import { apps } from './apps';

let actions: MicroAppStateActions;

export function initQiankun(): void {
  registerMicroApps(apps, {
    beforeLoad: [async (app) => console.log('[qiankun] before load', app.name)],
    beforeMount: [async (app) => console.log('[qiankun] before mount', app.name)],
    afterMount: [async (app) => console.log('[qiankun] after mount', app.name)],
    beforeUnmount: [async (app) => console.log('[qiankun] before unmount', app.name)],
    afterUnmount: [async (app) => console.log('[qiankun] after unmount', app.name)],
  });

  addGlobalUncaughtErrorHandler((event) => {
    console.error('[qiankun] global error:', event);
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
