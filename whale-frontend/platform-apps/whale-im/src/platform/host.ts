// 宿主（whale-platform）注入的上下文
//
// qiankun 开发态子应用与基座不同源，localStorage 不共享；基座通过 props 注入
// getToken / getPermissions，保证开发态也能复用同一登录态。

export interface HostProps {
  container?: HTMLElement;
  /** 占位 token（与其他子应用保持一致的注册字段） */
  token?: string;
  /** 读取基座 accessToken */
  getToken?: () => string | null;
  /** 读取基座当前用户权限码 */
  getPermissions?: () => string[];
  /** 读取基座当前用户ID（字符串） */
  getUserId?: () => string | null;
}

let hostProps: HostProps | null = null;

/** 在 render 之前保存宿主上下文（unmount 后清空） */
export function setHostProps(props?: HostProps | null): void {
  hostProps = props ?? null;
}

/** 读取宿主上下文；独立启动时为 null */
export function getHostProps(): HostProps | null {
  return hostProps;
}
