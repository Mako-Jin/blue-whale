package com.bluewhale.pdm.document.support;

/**
 * 图文档导出结果
 *
 * @param content   CSV 字节（含 UTF-8 BOM 与表头）
 * @param truncated 是否因超过导出行数上限被截断
 * @author BlueWhale
 */
public record PdmDocExport(byte[] content, boolean truncated) {
}
