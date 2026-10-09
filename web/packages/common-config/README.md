# common-config

前端工作区的共享工程配置。规则只在这里维护一份，各子项目引用即可，避免每个包各写一套。

## 提供什么

| 导出路径                            | 内容                              |
| ----------------------------------- | --------------------------------- |
| `common-config/eslint`              | ESLint flat config 工厂函数       |
| `common-config/stylelint`           | Stylelint 配置对象（已适配 Less） |
| `common-config/tsconfig.base.json`  | TypeScript 基础配置               |
| `common-config/tsconfig.react.json` | React 应用 TypeScript 配置        |

## 子项目怎么用

**1. 声明依赖**

```json
"devDependencies": {
  "common-config": "workspace:*"
}
```

**2. `eslint.config.js`（一行）**

```js
export { default } from 'common-config/eslint';
```

需要定制时用工厂函数：

```js
import { createEslintConfig } from 'common-config/eslint';

export default createEslintConfig({
  ignores: ['src/generated/**'],
  react: true,
});
```

**3. `stylelint.config.js`（一行）**

```js
export { default } from 'common-config/stylelint';
```

**4. `tsconfig.json`**

```json
{
  "extends": "common-config/tsconfig.react.json",
  "compilerOptions": {
    "baseUrl": ".",
    "paths": { "@/*": ["./src/*"] }
  },
  "include": ["src"]
}
```

## 约定

- **Prettier 不上这里**。Prettier 会自动向上查找最近的配置文件，用工作区根目录的 `.prettierrc.json` 即可，无需每个包配置。
- **改规则只改这里**。不要在子项目里覆盖 `common-config` 的规则；确有特殊需求时，用工厂函数的 `options` 参数，而不是就地复制规则。
- **ESLint 用 flat config**（ESLint 9 起的默认形式），旧的 `.eslintrc` 格式不再使用。
