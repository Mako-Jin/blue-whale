package com.bluewhale.job.service;

import com.bluewhale.job.dto.JobBatchDeleteRequest;
import com.bluewhale.job.dto.JobBatchStatusRequest;
import com.bluewhale.job.dto.JobImportRequest;
import com.bluewhale.job.vo.JobBatchResultVO;
import com.bluewhale.job.vo.JobExportVO;
import com.bluewhale.job.vo.JobImportResultVO;

/**
 * 任务批量操作与导入导出服务。
 *
 * <p>批量操作逐条复用单条接口的服务方法，不整批回滚；
 * 导入按匹配键识别已存在任务，并在任务全部落库后统一还原依赖。</p>
 *
 * @author BlueWhale
 */
public interface JobBatchService {

    /**
     * 批量启停任务
     *
     * @param request 任务ID列表与目标状态
     * @return 成功条数与失败明细
     */
    JobBatchResultVO batchStatus(JobBatchStatusRequest request);

    /**
     * 批量删除任务（逻辑删除）
     *
     * @param request 任务ID列表
     * @return 成功条数与失败明细
     */
    JobBatchResultVO batchDelete(JobBatchDeleteRequest request);

    /**
     * 导出任务定义与依赖边
     *
     * @param jobGroup 任务分组，为空导出全量
     * @return 导出结果
     */
    JobExportVO export(String jobGroup);

    /**
     * 导入任务定义与依赖边
     *
     * @param request 导入请求
     * @return 导入结果（各类计数与失败明细）
     */
    JobImportResultVO importJobs(JobImportRequest request);
}
