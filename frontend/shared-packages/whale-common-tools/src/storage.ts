// 存储工具 - localStorage / sessionStorage 封装

type StorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem' | 'clear'>;

/** 非浏览器 / 存储被禁用时的内存兜底，保证工具函数不抛错。 */
function createMemoryStorage(): StorageLike {
  const data = new Map<string, string>();
  return {
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => {
      data.set(key, value);
    },
    removeItem: (key) => {
      data.delete(key);
    },
    clear: () => {
      data.clear();
    },
  };
}

const memoryStorage = createMemoryStorage();

/** 安全获取宿主存储；隐私模式或非浏览器环境下回落内存实现。 */
function resolveStorage(kind: 'localStorage' | 'sessionStorage'): StorageLike {
  try {
    const host = globalThis as unknown as Record<string, StorageLike | undefined>;
    const storage = host[kind];
    if (storage && typeof storage.getItem === 'function') {
      return storage;
    }
  } catch {
    // 访问 localStorage 本身可能因隐私策略抛错
  }
  return memoryStorage;
}

function createStorage(kind: 'localStorage' | 'sessionStorage') {
  return {
    get<T = unknown>(key: string): T | null {
      const value = resolveStorage(kind).getItem(key);
      if (value === null) return null;
      try {
        return JSON.parse(value) as T;
      } catch {
        return value as unknown as T;
      }
    },

    set(key: string, value: unknown): void {
      const str = typeof value === 'string' ? value : JSON.stringify(value);
      resolveStorage(kind).setItem(key, str);
    },

    remove(key: string): void {
      resolveStorage(kind).removeItem(key);
    },

    clear(): void {
      resolveStorage(kind).clear();
    },
  };
}

export const localStore = createStorage('localStorage');
export const sessionStore = createStorage('sessionStorage');
