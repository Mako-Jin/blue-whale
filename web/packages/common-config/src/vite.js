/**
 * Vite 侧的共享配置片段。
 *
 * 放在这里的原因与本包其他文件一致：**跨应用重复的工程问题只修一次**。
 * 下面的插件会被每一个 qiankun 子应用用到，散在各 `vite.config.ts` 里必然漂移。
 */

/**
 * 把内联的 `<script type="module">` 改写成动态 `import()` 形式。
 *
 * 只转换能识别的静态 import；遇到不认识的写法原样返回，宁可报错也不猜。
 */
function toDynamicImports(code) {
  const named = /import\s*\{([^}]*)\}\s*from\s*(['"])([^'"]+)\2;?/g;
  const fallback = /import\s+([A-Za-z_$][\w$]*)\s+from\s*(['"])([^'"]+)\2;?/g;

  const converted = code
    .replace(named, 'const {$1} = await import($2$3$2);')
    .replace(fallback, 'const $1 = (await import($2$3$2)).default;');

  // 还剩 import 说明是没覆盖的语法形态，交给调用方放弃转换
  if (/^\s*import\s/m.test(converted)) {
    return null;
  }
  return converted;
}

/**
 * 让 React Fast Refresh 的 preamble 能在 qiankun 里执行。
 *
 * ### 问题
 *
 * `@vitejs/plugin-react` 在开发期会往 `index.html` 的 `<head>` 里塞一段内联模块脚本：
 *
 * ```html
 * <script type="module">import { injectIntoGlobalHook } from "/@react-refresh"; …</script>
 * ```
 *
 * qiankun 执行子应用脚本走的是 `import-html-entry`：它把每个 `<script>` 的**文本**取出来
 * 用 `eval` 执行。这段代码里是静态 `import`，而 `eval` 出来的是**普通脚本**、
 * 不是模块 —— 于是抛 `SyntaxError: Cannot use import statement outside a module`，
 * Fast Refresh 的全局钩子（`$RefreshReg$` / `$RefreshSig$`）也就永远装不上，
 * 表现是子应用里改组件不生效、或 HMR 时组件状态被整个丢掉。
 *
 * 只含 `src` 属性的脚本不受影响：`vite-plugin-qiankun` 已经把
 * `<body>` 里的 module 脚本改成动态 `import()` 了，唯独 `<head>` 里这段内联脚本它没管。
 *
 * ### 做法
 *
 * 把静态 `import` 换成 `await import()`，整体包进 async IIFE，并去掉 `type="module"`。
 * 语义完全不变（仍然是模块导入），但变成普通脚本，`eval` 就能执行了。
 *
 * 只处理**内容里含 `@react-refresh`** 的内联模块脚本，避免误伤其他内联脚本。
 */
export function qiankunDevPreamble() {
  return {
    name: 'bluewhale:qiankun-dev-preamble',
    // 只在开发期需要：生产构建不会注入这段 preamble
    apply: 'serve',
    transformIndexHtml: {
      // 必须排在最后 —— 要等 react 插件把 preamble 注入进来之后才能改写它
      order: 'post',
      handler(html) {
        return html.replace(
          /<script([^>]*)\btype="module"([^>]*)>([\s\S]*?)<\/script>/g,
          (whole, before, after, body) => {
            if (!body.includes('@react-refresh')) {
              return whole;
            }

            const converted = toDynamicImports(body);
            if (!converted) {
              return whole;
            }

            return `<script>(async () => {${converted}})()</script>`;
          },
        );
      },
    },
  };
}
