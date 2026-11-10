/// <reference types="vite/client" />

/**
 * ops 环境变量声明
 *
 * 监控跳转地址仅从构建时环境变量注入；未配置时 Monitor 页不渲染跳转入口，
 * 不再内置内网地址兜底（审计修复）。
 */
interface ImportMetaEnv {
  /** Prometheus 控制台地址（可选，运维内网使用） */
  readonly VITE_PROMETHEUS_URL?: string;
  /** Grafana 控制台地址（可选，运维内网使用） */
  readonly VITE_GRAFANA_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
