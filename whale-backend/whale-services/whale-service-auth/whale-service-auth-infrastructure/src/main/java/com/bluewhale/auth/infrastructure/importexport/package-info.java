/**
 * 用户导入导出适配器（权限中心扩展 M4）：实现应用层
 * {@link com.bluewhale.auth.application.port.ImportBatchStore} /
 * {@link com.bluewhale.auth.application.port.ExportFileUploader} 端口，
 * 以 Redis 暂存预检批次、以 whale-file 承载超限导出文件上传。
 *
 * @author BlueWhale
 */
package com.bluewhale.auth.infrastructure.importexport;
