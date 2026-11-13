package com.bluewhale.auth.application.model.importexport;

/**
 * 导入行级错误（权限中心扩展 M4）：预检阶段定位到「行 + 列」的校验错误。
 *
 * @param rowNo   Excel 行号（1 起）
 * @param column  列名（与模板表头一致，如「用户名」「手机号」）
 * @param message 错误说明
 * @author BlueWhale
 */
public record ImportRowError(int rowNo, String column, String message) {
}
