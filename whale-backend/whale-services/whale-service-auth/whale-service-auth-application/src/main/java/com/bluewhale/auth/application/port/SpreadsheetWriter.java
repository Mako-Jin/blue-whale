package com.bluewhale.auth.application.port;

import com.bluewhale.auth.application.model.importexport.UserExportRow;

import java.io.OutputStream;
import java.util.List;

/**
 * 电子表格写出端口（权限中心扩展 M4）：导入模板与导出文件共用同一套表头。
 *
 * @author BlueWhale
 */
public interface SpreadsheetWriter {

    /**
     * 生成用户导入模板（表头 + 必填项说明 + 示例行）
     *
     * @return 模板 xlsx 字节内容
     */
    byte[] writeTemplate();

    /**
     * 流式写出用户导出文件：按页拉取数据逐批写入，避免整表驻留内存
     *
     * @param output  输出流
     * @param fetcher 分页取数器（页码从 0 起；返回空列表表示结束）
     * @return 实际写出的总行数
     */
    long writeUsers(OutputStream output, UserRowFetcher fetcher);

    /**
     * 分页取数器：导出流式分页回填
     *
     * @author BlueWhale
     */
    @FunctionalInterface
    interface UserRowFetcher {

        /**
         * 拉取一页导出行
         *
         * @param pageIndex 页码（从 0 起）
         * @param pageSize  每页条数
         * @return 当页导出行列表（空列表表示数据已取完）
         */
        List<UserExportRow> fetch(long pageIndex, int pageSize);
    }
}
