/**
 * `common-config/vite` 的类型声明。
 *
 * 刻意**不 import `vite` 的类型**：本包只把 vite 列为 peerDependency，
 * 在 pnpm 的隔离布局下 `vite` 不一定能从本文件的位置解析到，
 * 写 `import type { Plugin } from 'vite'` 反而会让消费方的类型检查挂掉。
 * 这里按结构把用到的部分描述清楚即可 —— Vite 的 `Plugin` 本身也是结构类型。
 */
export declare function qiankunDevPreamble(): {
  name: string;
  apply: 'serve';
  transformIndexHtml: {
    order: 'post';
    handler(html: string): string;
  };
};
