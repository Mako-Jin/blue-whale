export {
  bindHost,
  getHostGlobalState,
  getHostProps,
  isMicroApp,
  onHostGlobalStateChange,
  setHostGlobalState,
  unbindHost,
} from './bridge';
export type { GlobalStateListener } from './bridge';
export type { HostAuthBridge, HostGlobalState, HostProps } from './types';

export { createPermissionStore, EMPTY_PERMISSION_STATE, visibleViews } from './permission-store';
export type {
  IdentitySnapshot,
  PermissionState,
  PermissionStore,
  StandaloneIdentitySource,
} from './permission-store';
