package com.bluewhale.job.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.job.dto.JobInfoCreateRequest;
import com.bluewhale.job.dto.JobInfoQueryRequest;
import com.bluewhale.job.dto.JobInfoUpdateRequest;
import com.bluewhale.job.vo.JobInfoVO;

/**
 * 定时任务信息服务
 *
 * @author BlueWhale
 */
public interface JobInfoService {

    /**
     * 分页查询定时任务
     *
     * @param request 查询条件
     * @return 任务分页结果
     */
    PageResult<JobInfoVO> page(JobInfoQueryRequest request);

    /**
     * 任务详情
     *
     * @param id 任务ID
     * @return 任务视图对象
     */
    JobInfoVO getDetail(Long id);

    /**
     * 新增任务
     *
     * @param request 新增请求
     * @return 新建任务视图对象
     */
    JobInfoVO create(JobInfoCreateRequest request);

    /**
     * 更新任务
     *
     * @param id 任务ID
     * @param request 更新请求
     * @return 更新后的任务视图对象
     */
    JobInfoVO update(Long id, JobInfoUpdateRequest request);

    /**
     * 删除任务（逻辑删除）
     *
     * @param id 任务ID
     */
    void delete(Long id);

    /**
     * 启停任务
     *
     * @param id 任务ID
     * @param status 目标状态：1 运行中 / 0 已停止
     */
    void changeStatus(Long id, Integer status);

    /**
     * 立即执行一次
     *
     * @param id 任务ID
     */
    void runOnce(Long id);
}
