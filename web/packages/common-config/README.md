# common-config

前端工作区的共享工程配置。规则只在这里维护一份，各子项目引用即可，避免每个包各写一套。

## 提供什么

| 导出路径                            | 内容                              |
| ----------------------------------- | --------------------------------- |
| `common-config/eslint`              | ESLint flat config 工厂函数       |
| `common-config/stylelint`           | Stylelint 配置对象（已适配 Less） |
| `common-config/vite`                | 跨应用共享的 Vite 插件片段        |
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

**5. `vite.config.ts` —— 需要时引用共享片段（可选）**

共享片段用**函数**形式导出，调用后得到插件对象，放进 `plugins` 数组即可：

```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import qiankun from 'vite-plugin-qiankun';
import { qiankunDevPreamble } from 'common-config/vite';

export default defineConfig(({ command }) => ({
  // dev 用 `/`、build 才带前缀：见下方说明
  base: command === 'build' ? '/log-console/' : '/',
  plugins: [react(), qiankun('platform-log-console', { useDevMode: true }), qiankunDevPreamble()],
}));
```

| 导出                   | 作用                                                                               | 谁会用            |
| ---------------------- | ---------------------------------------------------------------------------------- | ----------------- |
| `qiankunDevPreamble()` | 让 React Fast Refresh preamble 能在 qiankun 里执行，**并让入口脚本等它装好再启动** | 每个 React 子应用 |

**为什么需要 `qiankunDevPreamble()`**：`@vitejs/plugin-react` 开发期会往 `index.html` 的 `<head>` 塞一段**内联 module 脚本**；qiankun 通过 `import-html-entry` 取脚本**文本**用 `eval` 执行，而 `eval` 出来的是普通脚本，静态 `import` 直接抛 `SyntaxError: Cannot use import statement outside a module`，Fast Refresh 的全局钩子装不上。这个插件把该脚本的静态 `import` 改写成 `await import()` 并去掉 `type="module"`，语义不变但可被 `eval` 执行。它只认含 `@react-refresh` 的内联脚本，不会误伤其他内联脚本，且 `apply: 'serve'` —— **生产构建不注入这段 preamble，因此不需要这个插件**。背景见 `docs/decisions/0012` 第 10 条。

**它还做第二件事：把顺序保证补回来。** 模块脚本按文档顺序求值是规范保证的，而改写出来的 `(async () => {…})()` 不是 —— 它一上来就 `await`，于是入口那段 `import('…/src/main.tsx')` 会与 `import('/@react-refresh')` **抢跑**。冷启动的开发服务器上 `/@react-refresh` 要先做依赖预构建、明显更慢，抢输的症状是**整个应用白屏** + `can't detect preamble. Something is wrong.`（刷新一次就好，因为服务器已经热了）。

所以插件同时：① 把 preamble 那次的 Promise 挂到 `window.__bwDevPreambleReady`；② 让 `vite-plugin-qiankun` 注入的入口脚本先 `await` 它再 `import` 应用。两段都是普通脚本、按文档顺序执行，读到的就是 preamble 已经**发起**的那次导入；`?? Promise.resolve()` 是兜底（找不到那段脚本时行为退回改写前）。**这条只在独立运行时（直接开 `:3300` 这类地址）会暴露** —— 跑在基座里时中间隔了几跳，`@react-refresh` 反而先到。详细论证见 `docs/decisions/0035`。

> **`base` 不要"顺手统一"。** 子应用的 `base` 在 dev 下必须是 `/`：`vite-plugin-qiankun` 的 `useDevMode` 靠 `base` 定位 `/@vite/client`，dev 期加前缀会让热更新**静默失效**。前缀只在 build 时才需要（供基座按静态路径取资源）。

## 约定

- **Prettier 不上这里。** Prettier 会自动向上查找最近的配置文件，用工作区根目录的
  `.prettierrc.json` 即可，无需每个包配置。
- **改规则只改这里。** 不要在子项目里覆盖 `common-config` 的规则；确有特殊需求时，用工厂
  函数的 `options` 参数，而不是就地复制规则。
- **ESLint 用 flat config**（ESLint 9 起的默认形式），旧的 `.eslintrc` 格式不再使用。
- **工作区根也有一份 `eslint.config.js`**（`web/eslint.config.js`），同样只是引用本包。
  它必须存在的原因是：ESLint 只从**当前工作目录**向上查找配置，而 lint-staged 是在
  `web/` 根目录执行 `eslint` 的。
- **`common-config/vite` 只收「有两个以上应用会用」的片段。** 只有一个应用用的 Vite 配置
  留在它自己的 `vite.config.ts` 里 —— 提前抽到共享包会让读者以为它是全局约定。
