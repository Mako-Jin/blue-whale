import { createEslintConfig } from 'common-config/eslint';

export default [
  ...createEslintConfig(),
  {
    // 本包是日志能力的实现方，必须直接调用 console；
    // 其余项目仍受 common-config 的 no-console 规则约束。
    files: ['src/**/*.ts', 'tests/**/*.ts', 'playground/**/*.ts'],
    rules: {
      'no-console': 'off',
    },
  },
];
