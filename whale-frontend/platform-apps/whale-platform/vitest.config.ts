import { defineConfig } from 'vitest/config';

// 基座测试以纯函数为主（菜单本地化、权限裁剪），node 环境即可，无需 jsdom/antd。
export default defineConfig({
  test: {
    environment: 'node',
    css: false,
  },
});
