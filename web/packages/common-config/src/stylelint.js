import { createRequire } from 'node:module';

/**
 * Stylelint 共享配置。子项目 stylelint.config.js 直接 re-export 本对象即可。
 *
 * 已针对 Less 做了适配（customSyntax 指向 postcss-less）。
 * 若将来引入 CSS Modules、Sass 等，在此追加 overrides。
 *
 * 注意：pnpm 严格依赖隔离下，子项目目录看不到本包的依赖，
 * 故 extends / customSyntax 统一用 require.resolve 解析为绝对路径。
 */
const require = createRequire(import.meta.url);

const stylelintConfig = {
  extends: [require.resolve('stylelint-config-standard')],
  overrides: [
    {
      files: ['**/*.less'],
      customSyntax: require.resolve('postcss-less'),
      rules: {
        // Less 变量（@var）不被该规则识别，会产生误报
        'declaration-property-value-no-unknown': null,
      },
    },
  ],
  rules: {
    'selector-class-pattern': null,
    'custom-property-pattern': null,
    'keyframes-name-pattern': null,
    'no-descending-specificity': null,
    'selector-pseudo-class-no-unknown': [true, { ignorePseudoClasses: ['global', 'local'] }],
    'at-rule-no-unknown': null,
    'import-notation': null,
  },
  ignoreFiles: ['**/dist/**', '**/coverage/**', '**/node_modules/**', '**/.turbo/**'],
};

export default stylelintConfig;
