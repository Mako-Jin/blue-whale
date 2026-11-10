package com.bluewhale.crm.analytics.application.service;

import com.bluewhale.crm.analytics.domain.model.AnalyticsExport;
import com.bluewhale.crm.analytics.domain.model.ExportType;
import com.bluewhale.crm.common.CrmActor;
import lombok.Getter;

/**
 * 分析导出应用服务。
 *
 * @author BlueWhale
 */
public interface AnalyticsExportAppService {

    /** 导出文件下载载荷 */
    @Getter
    class ExportDownload {

        /** 文件字节 */
        private final byte[] content;

        /** 下载文件名 */
        private final String fileName;

        /** 内容类型 */
        private final String contentType;

        /**
         * 构造下载载荷。
         *
         * @param content 文件字节
         * @param fileName 文件名
         */
        public ExportDownload(byte[] content, String fileName) {
            this.content = content;
            this.fileName = fileName;
            this.contentType = "text/csv;charset=UTF-8";
        }
    }

    /**
     * 创建并同步执行分析导出。
     *
     * @param type 导出类型
     * @param filtersJson 筛选条件 JSON
     * @param actor 操作人
     * @return 导出任务
     */
    AnalyticsExport create(ExportType type, String filtersJson, CrmActor actor);

    /**
     * 查询导出内容。
     *
     * @param id 导出任务主键
     * @param actor 操作人
     * @return 导出任务（新导出为文件引用，历史导出含 CSV）
     */
    AnalyticsExport content(Long id, CrmActor actor);

    /**
     * 代理下载导出文件（V13 起新导出走 whale-file；历史导出回退 CSV 内容）。
     *
     * @param id 导出任务主键
     * @param actor 操作人
     * @return 下载载荷
     */
    ExportDownload download(Long id, CrmActor actor);
}
