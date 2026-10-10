import '@testing-library/jest-dom/vitest';
import { vi } from 'vitest';

/**
 * jsdom 不带 canvas 实现，`getContext()` 每被调用一次就往控制台打一条
 * "Not implemented" 错误。这里显式返回 `null`，语义上就是**没有 WebGL 的运行环境** ——
 * 正好是本应用要覆盖的降级分支（无 GPU 的 CI / 无头环境）。
 */
vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
