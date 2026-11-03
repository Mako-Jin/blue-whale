/// <reference types="vite/client" />

/**
 * 基座环境变量声明（P0-5 修复：qiankun 子应用 entry/登录态配置化）
 *
 * 生产部署必须在构建时注入 VITE_SUBAPP_ENTRY_*（反向代理地址或子应用域名），
 * 未注入时回退 `//localhost:<port>`，仅可用于本地开发。模板见 .env.example。
 */
interface ImportMetaEnv {
  /** 子应用 entry 覆盖（键对应 apps.ts 中各子应用） */
  readonly VITE_SUBAPP_ENTRY_PORTAL?: string;
  readonly VITE_SUBAPP_ENTRY_ERP?: string;
  readonly VITE_SUBAPP_ENTRY_OA?: string;
  readonly VITE_SUBAPP_ENTRY_MES?: string;
  readonly VITE_SUBAPP_ENTRY_PDM?: string;
  readonly VITE_SUBAPP_ENTRY_IM?: string;
  readonly VITE_SUBAPP_ENTRY_PM?: string;
  readonly VITE_SUBAPP_ENTRY_CONTRACT?: string;
  readonly VITE_SUBAPP_ENTRY_DOC?: string;
  readonly VITE_SUBAPP_ENTRY_MUSIC?: string;
  readonly VITE_SUBAPP_ENTRY_VIDEO?: string;
  /** 旧子应用 host 兼容用的回退 token，生产应注入真实值 */
  readonly VITE_PLATFORM_TOKEN?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
