/** 统一把异常转为可展示文案 */
export function toMessage(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败';
}
