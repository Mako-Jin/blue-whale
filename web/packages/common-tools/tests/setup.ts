import { afterEach } from 'vitest';
import { resetLogger } from '../src/logger';

// 全局配置是模块级单例，逐个用例之间必须复位，否则会互相串味
afterEach(() => {
  resetLogger();
});
