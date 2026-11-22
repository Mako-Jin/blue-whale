// ESLint 9 flat config - BlueWhale 前端统一规则
import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import eslintConfigPrettier from 'eslint-config-prettier';

export default tseslint.config(
  // 全局忽略
  {
    ignores: [
      '**/dist/**',
      '**/node_modules/**',
      '**/.turbo/**',
      '**/coverage/**',
      '**/*.d.ts',
      '**/vite-env.d.ts',
      // Playwright 产物（HTML 报告自带打包后的 UI 脚本，不是本仓源码）。
      // 2026-11-13 之前根 .gitignore 误写 frontend/ 前缀，产物被入库并扫出 4000+ no-undef。
      '**/playwright-report/**',
      '**/test-results/**',
      '**/blob-report/**',
    ],
  },

  // 基础规则
  js.configs.recommended,
  ...tseslint.configs.recommended,

  // TS/TSX 源文件
  {
    files: ['**/*.{ts,tsx}'],
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      '@typescript-eslint/no-unused-vars': [
        'warn',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      '@typescript-eslint/no-explicit-any': 'warn',
      'no-console': ['warn', { allow: ['warn', 'error'] }],
    },
  },

  // Node 脚本（冒烟脚本、Playwright 用例等）：补充 Node 全局与内置 Web API。
  // 这类脚本同时跑在两种宿主里：Node 侧驱动 Playwright，page.evaluate 回调里用浏览器全局，
  // 所以两边全局都得声明，否则 no-undef 会把每个脚本都判红（本轮曾累计 4127 条）。
  {
    files: ['**/*.{mjs,cjs,js}'],
    languageOptions: {
      globals: {
        // Node
        console: 'readonly',
        process: 'readonly',
        Buffer: 'readonly',
        __dirname: 'readonly',
        __filename: 'readonly',
        module: 'readonly',
        require: 'readonly',
        exports: 'readonly',
        global: 'readonly',
        // 定时器 / 微任务
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
        setInterval: 'readonly',
        clearInterval: 'readonly',
        setImmediate: 'readonly',
        clearImmediate: 'readonly',
        queueMicrotask: 'readonly',
        // Node 18+ / 浏览器共有的 Web API
        URL: 'readonly',
        URLSearchParams: 'readonly',
        fetch: 'readonly',
        Request: 'readonly',
        Response: 'readonly',
        Headers: 'readonly',
        Blob: 'readonly',
        File: 'readonly',
        FormData: 'readonly',
        TextEncoder: 'readonly',
        TextDecoder: 'readonly',
        ReadableStream: 'readonly',
        AbortController: 'readonly',
        structuredClone: 'readonly',
        performance: 'readonly',
        // 浏览器（page.evaluate / 页面内注入脚本）
        window: 'readonly',
        document: 'readonly',
        navigator: 'readonly',
        location: 'readonly',
        localStorage: 'readonly',
        sessionStorage: 'readonly',
        WebSocket: 'readonly',
        Event: 'readonly',
        CustomEvent: 'readonly',
        HTMLElement: 'readonly',
        Element: 'readonly',
        Node: 'readonly',
        MouseEvent: 'readonly',
        KeyboardEvent: 'readonly',
        Image: 'readonly',
        XMLHttpRequest: 'readonly',
        requestAnimationFrame: 'readonly',
        cancelAnimationFrame: 'readonly',
        getComputedStyle: 'readonly',
        atob: 'readonly',
        btoa: 'readonly',
        DOMMatrix: 'readonly',
        createImageBitmap: 'readonly',
        OffscreenCanvas: 'readonly',
        alert: 'readonly',
      },
    },
  },

  // 关闭与 Prettier 冲突的规则（必须放最后）
  eslintConfigPrettier,
);
