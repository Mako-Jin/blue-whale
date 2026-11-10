// public-path.ts - 处理 qiankun 子应用资源路径

import qiankunWindow from 'vite-plugin-qiankun/dist/helper';

export const isInQiankun = (qiankunWindow as unknown as { __POWERED_BY_QIANKUN__: boolean })
  .__POWERED_BY_QIANKUN__;
