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

**2. `eslint.config.js` —— 必须调用工厂函数**

```js
import { createEslintConfig } from 'common-config/eslint';

export default createEslintConfig();
```

> ⚠️ **不要写成** `export { default } from 'common-config/eslint'`。
> 本模块的默认导出是**工厂函数**，而 ESLint 要求配置文件默认导出**配置数组**。
> 直接 re-export 会报 `TypeError: Unexpected function`。

需要定制时传参：

```js
import { createEslintConfig } from 'common-config/eslint';

export default createEslintConfig({
  ignores: ['src/generated/**'],
  react: true,
});
```

**可用选项**

| 选项              | 默认值          | 说明                                       |
| ----------------- | --------------- | ------------------------------------------ |
| `ignores`         | `[]`            | 追加忽略路径                               |
| `react`           | `true`          | 是否启用 React / Hooks / Refresh 规则      |
| `tsconfigRootDir` | `process.cwd()` | typescript-eslint 的 TypeScript 配置根目录 |

> **`tsconfigRootDir` 为什么必须显式指定**：monorepo 中同时存在多个 `tsconfig`（如 `web/`
> 与 `web/platform/platform-portal/`），typescript-eslint 无法自动推断，会抛出
> `multiple candidate TSConfigRootDirs`。默认取 `process.cwd()` 已覆盖两种场景——子项目内
> 运行取该子项目目录，工作区根运行取 `web/`。

**3. `stylelint.config.js` —— 一行 re-export**

```js
export { default } from 'common-config/stylelint';
```

**4. `tsconfig.json`**

```json
{
  "extends": "common-config/tsconfig.react.json",
  "compilerOptions": {
    "paths": { "@/*": ["./src/*"] }
  },
  "include": ["src"]
}
```

> **不要写 `baseUrl`**。它在 TypeScript 6 中已弃用（7.0 将移除），`paths` 现已相对
> tsconfig 自身解析。

## 约定

- **Prettier 不上这里。** Prettier 会自动向上查找最近的配置文件，用工作区根目录的
  `.prettierrc.json` 即可，无需每个包配置。
- **改规则只改这里。** 不要在子项目里覆盖 `common-config` 的规则；确有特殊需求时，用工厂
  函数的 `options` 参数，而不是就地复制规则。
- **ESLint 用 flat config**（ESLint 9 起的默认形式），旧的 `.eslintrc` 格式不再使用。
- **工作区根也有一份 `eslint.config.js`**（`web/eslint.config.js`），同样只是引用本包。
  它必须存在的原因是：ESLint 只从**当前工作目录**向上查找配置，而 lint-staged 是在
  `web/` 根目录执行 `eslint` 的。
