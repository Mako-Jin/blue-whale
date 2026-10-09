import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import prettier from 'eslint-config-prettier';

/**
 * 生成 ESLint flat config。
 *
 * 各子项目在自身目录下建一个一行的 eslint.config.js 引用本配置即可，
 * 规则统一在此维护，避免每个项目各写一份。
 *
 * @param {object} [options]
 * @param {string[]} [options.ignores] 需要额外忽略的路径
 * @param {boolean} [options.react] 是否启用 React 相关规则，默认 true
 * @param {string} [options.tsconfigRootDir] TypeScript 配置根目录，默认取 process.cwd()
 */
export function createEslintConfig(options = {}) {
  const { ignores = [], react = true, tsconfigRootDir = process.cwd() } = options;

  const reactConfigs = react
    ? [
        {
          files: ['**/*.{ts,tsx}'],
          plugins: {
            'react-hooks': reactHooks,
            'react-refresh': reactRefresh,
          },
          rules: {
            ...reactHooks.configs.recommended.rules,
            'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
          },
        },
      ]
    : [];

  return tseslint.config(
    {
      ignores: ['**/dist/**', '**/coverage/**', '**/node_modules/**', '**/.turbo/**', ...ignores],
    },
    {
      files: ['**/*.{js,mjs,cjs}'],
      extends: [js.configs.recommended],
      languageOptions: {
        ecmaVersion: 2022,
        sourceType: 'module',
        globals: { ...globals.node },
      },
    },
    {
      files: ['**/*.{ts,tsx}'],
      extends: [js.configs.recommended, ...tseslint.configs.recommended],
      languageOptions: {
        ecmaVersion: 2022,
        sourceType: 'module',
        globals: { ...globals.browser, ...globals.node },
        parserOptions: {
          // monorepo 中同时存在多个 tsconfig（如 web/ 与 web/platform/platform-portal/），
          // typescript-eslint 无法自动推断根目录，会抛出
          // "multiple candidate TSConfigRootDirs" 解析错误，故必须显式指定。
          // 默认取 process.cwd()：从工作区根运行时为 web/，从子项目运行时为该子项目目录。
          tsconfigRootDir,
        },
      },
      rules: {
        '@typescript-eslint/no-unused-vars': [
          'warn',
          { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' },
        ],
        '@typescript-eslint/no-explicit-any': 'warn',
        'no-console': ['warn', { allow: ['warn', 'error'] }],
        eqeqeq: ['error', 'always', { null: 'ignore' }],
      },
    },
    ...reactConfigs,
    prettier,
  );
}

export default createEslintConfig;
