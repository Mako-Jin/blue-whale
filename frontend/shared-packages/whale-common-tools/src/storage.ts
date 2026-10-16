// 存储工具 - localStorage / sessionStorage 封装

function createStorage(storage: Storage) {
  return {
    get<T = unknown>(key: string): T | null {
      const value = storage.getItem(key);
      if (value === null) return null;
      try {
        return JSON.parse(value) as T;
      } catch {
        return value as unknown as T;
      }
    },

    set(key: string, value: unknown): void {
      const str = typeof value === 'string' ? value : JSON.stringify(value);
      storage.setItem(key, str);
    },

    remove(key: string): void {
      storage.removeItem(key);
    },

    clear(): void {
      storage.clear();
    },
  };
}

export const localStore = createStorage(localStorage);
export const sessionStore = createStorage(sessionStorage);
