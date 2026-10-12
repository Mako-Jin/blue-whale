export { MICRO_APPS, findMicroApp, resolveEntry } from './apps';
export { createMicroAppProps, mountMicroApp } from './loader';
export { startSessionSync } from './session-sync';
export {
  INITIAL_GLOBAL_STATE,
  getGlobalState,
  offGlobalStateChange,
  onGlobalStateChange,
  setGlobalState,
} from './state';
export type { GlobalState, MicroAppMeta, MicroAppProps } from './types';
