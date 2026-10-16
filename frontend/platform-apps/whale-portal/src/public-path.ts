// public-path.ts - 处理 qiankun 子应用资源路径
// 在子应用独立运行时与被基座加载时使用不同的 public path

import qiankunWindow from 'vite-plugin-qiankun/dist/helper';

// qiankunWindow.__POWERED_BY_QIANKUN__ === true 表示被基座加载
// 否则为独立运行
export const isInQiankun = (qiankunWindow as unknown as { __POWERED_BY_QIANKUN__: boolean })
  .__POWERED_BY_QIANKUN__;

// 如果被 qiankun 加载，动态设置 publicPath
export function applyPublicPath(): void {
  if (isInQiankun) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (window as any).__POWERED_BY_QIANKUN__ = true;
  }
}
