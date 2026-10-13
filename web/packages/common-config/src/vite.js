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
 * preamble 那个 Promise 挂在哪个全局键上。
 *
 * 单独抽成常量：写它的地方（preamble 脚本）与读它的地方（入口脚本）必须用**同一个键**，
 * 两处各写一遍字面量是最容易改一处忘一处的地方。
 */
const PREAMBLE_READY = '__bwDevPreambleReady';

/**
 * `vite-plugin-qiankun` 注入的入口脚本 —— 形如 `<script>import(… + '/src/main.tsx').finally(…)`。
 *
 * 用 `(?!<\/script>)` 逐字符排除闭合标签，而不是 `[\s\S]*?`：
 * 后者允许跨过 `</script>`，于是会从**前面**那段 preamble 脚本一路匹配到这里、
 * 把两段一起包住 —— 那样包装出来的是一个空转的 Promise，而 `nginx` 式的"看着合法"最坑人。
 */
const ENTRY_SCRIPT =
  /<script>((?:(?!<\/script>)[\s\S])*?\/src\/main\.tsx(?:(?!<\/script>)[\s\S])*)<\/script>/;

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
 *
 * ### 改写的**代价**：它把顺序保证丢了，所以必须同时补回来
 *
 * 模块脚本按文档顺序**求值**是规范保证的，而 `(async () => {…})()` 不是 ——
 * 它一开始就 `await`，把控制权交出去。于是入口脚本那段
 * `import('…/src/main.tsx')` 会与 `import('/@react-refresh')` **抢跑**：
 * 谁先完成取决于响应快慢，而 `/@react-refresh` 在**冷却的开发服务器**上要先做依赖预构建，
 * 明显更慢。抢输的症状是组件模块执行时 `$RefreshReg$` 还没装好，
 * `@vitejs/plugin-react` 抛 `can't detect preamble. Something is wrong.` ——
 * **整个应用白屏**（刷新一次就好，因为那时服务器已经热了）。
 *
 * 这条路径**只在独立运行时**（直接开 `:3300` 这类地址）出现：跑在基座里时
 * `import-html-entry` 取 HTML 与执行脚本之间隔了几跳，`@react-refresh` 反而先到。
 * 也正因为它只在"刚起服务 + 第一次打开"时出现，曾经被误判成代码回归
 * （见 `docs/decisions/0035`）。
 *
 * 补法就是**把顺序显式写出来**：preamble 把自己那次的 Promise 挂到全局，
 * 入口脚本先等它、再 import 应用。两段都是普通脚本、按文档顺序执行，
 * 所以入口读到的一定是 preamble 已经**发起**的那次导入（`?? Promise.resolve()`
 * 是兜底：万一找不到那段脚本，行为退回改写前，不会更糟）。
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
        let converted = false;

        const withPreamble = html.replace(
          /<script([^>]*)\btype="module"([^>]*)>([\s\S]*?)<\/script>/g,
          (whole, before, after, body) => {
            if (!body.includes('@react-refresh')) {
              return whole;
            }

            const code = toDynamicImports(body);
            if (!code) {
              return whole;
            }

            converted = true;
            // 这次导入的 Promise 挂出去，供入口脚本 await
            return `<script>window.${PREAMBLE_READY} = (async () => {${code}})()</script>`;
          },
        );

        // 没改写 preamble（或它已经是原生模块脚本、顺序本来就由规范保证）⇒ 入口不动
        if (!converted) {
          return withPreamble;
        }

        return withPreamble.replace(ENTRY_SCRIPT, (whole, body) => {
          // 包一层函数体只是为了不改动原脚本的结构：它是一条表达式语句
          return `<script>(window.${PREAMBLE_READY} ?? Promise.resolve()).then(() => {${body}})</script>`;
        });
      },
    },
  };
}
