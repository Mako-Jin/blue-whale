package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.job.dto.JobDependencySetRequest;
import com.bluewhale.job.po.JobDependencyPo;
import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.mapper.JobDependencyMapper;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.scheduler.JobScheduler;
import com.bluewhale.job.service.JobDependencyService;
import com.bluewhale.job.vo.JobDependencyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 任务依赖关系服务实现。
 *
 * <p>依赖配置为「全量替换」语义：每次保存先物理清理该下游任务的旧依赖边再写入新边，
 * 保存前完成存在性、自依赖与环检测（DFS 深度上限 64），保存后重新调度该任务，
 * 使其按是否仍有上游依赖决定是否注册 cron。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class JobDependencyServiceImpl implements JobDependencyService {

    /** 环检测 DFS 深度上限：防止异常数据导致无限递归 */
    private static final int MAX_CYCLE_DEPTH = 64;

    private final JobDependencyMapper jobDependencyMapper;

    private final JobInfoMapper jobInfoMapper;

    private final JobScheduler jobScheduler;

    @Override
    public JobDependencyVO getDependencies(Long jobId) {
        requireJob(jobId);
        JobDependencyVO vo = new JobDependencyVO();
        vo.setJobId(jobId);
        vo.setUpstreamJobIds(listUpstreamIds(jobId));
        vo.setDownstreamJobIds(listDownstreamIds(jobId));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobDependencyVO saveDependencies(Long jobId, JobDependencySetRequest request) {
        requireJob(jobId);
        List<Long> upstreamIds = normalize(request == null ? null : request.getUpstreamJobIds());
        validateUpstreams(jobId, upstreamIds);
        validateNoCycle(jobId, upstreamIds);
        jobDependencyMapper.purgeByJobId(jobId);
        upstreamIds.forEach(upstreamId -> insertDependency(jobId, upstreamId));
        jobScheduler.reschedule(jobId);
        return getDependencies(jobId);
    }

    /**
     * 查询任务，不存在则抛出 {@code NOT_FOUND}
     *
     * @param jobId 任务ID
     * @return 任务实体
     */
    private JobInfoPo requireJob(Long jobId) {
        JobInfoPo job = jobInfoMapper.selectById(jobId);
        if (job == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "任务不存在: " + jobId);
        }
        return job;
    }

    /**
     * 归一化上游任务ID列表：过滤空值并按首次出现顺序去重
     *
     * @param upstreamJobIds 原始上游任务ID列表（可为空）
     * @return 去重后的上游任务ID列表
     */
    private static List<Long> normalize(List<Long> upstreamJobIds) {
        if (upstreamJobIds == null) {
            return List.of();
        }
        Set<Long> distinctIds = new LinkedHashSet<>();
        upstreamJobIds.stream().filter(Objects::nonNull).forEach(distinctIds::add);
        return List.copyOf(distinctIds);
    }

    /**
     * 校验上游任务：禁止自依赖且每个上游任务必须存在
     *
     * @param jobId 下游任务ID
     * @param upstreamIds 上游任务ID列表
     * @throws BizException 自依赖抛 {@code PARAM_ERROR}，上游不存在抛 {@code NOT_FOUND}
     */
    private void validateUpstreams(Long jobId, List<Long> upstreamIds) {
        for (Long upstreamId : upstreamIds) {
            if (upstreamId.equals(jobId)) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "任务不能依赖自身");
            }
            if (jobInfoMapper.selectById(upstreamId) == null) {
                throw new BizException(ErrorCode.NOT_FOUND.getCode(), "上游任务不存在: " + upstreamId);
            }
        }
    }

    /**
     * 校验新依赖边不会形成环：从每个新上游出发，沿上游方向 DFS 查找是否能回到当前任务
     *
     * @param jobId 下游任务ID
     * @param upstreamIds 本次待保存的上游任务ID列表
     * @throws BizException 存在环时抛出 {@code PARAM_ERROR}
     */
    private void validateNoCycle(Long jobId, List<Long> upstreamIds) {
        for (Long upstreamId : upstreamIds) {
            if (reachesTarget(upstreamId, jobId, jobId, upstreamIds, new HashSet<>(), 0)) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "任务依赖关系存在环");
            }
        }
    }

    /**
     * DFS 判断从 {@code current} 沿上游方向是否能到达 {@code target}
     *
     * @param current 当前遍历节点
     * @param target 目标节点（当前下游任务）
     * @param overrideJobId 使用候选边替代数据库边的任务ID
     * @param overrideUpstreamIds 候选边中 {@code overrideJobId} 的上游列表
     * @param visited 已访问节点集合
     * @param depth 当前深度
     * @return 能到达返回 {@code true}；超深或已访问返回 {@code false}
     */
    private boolean reachesTarget(Long current, Long target, Long overrideJobId, List<Long> overrideUpstreamIds,
                                  Set<Long> visited, int depth) {
        if (current.equals(target)) {
            return true;
        }
        if (depth >= MAX_CYCLE_DEPTH || !visited.add(current)) {
            return false;
        }
        List<Long> upstreams = current.equals(overrideJobId) ? overrideUpstreamIds : listUpstreamIds(current);
        for (Long upstreamId : upstreams) {
            if (reachesTarget(upstreamId, target, overrideJobId, overrideUpstreamIds, visited, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 写入一条依赖边
     *
     * @param jobId 下游任务ID
     * @param upstreamId 上游任务ID
     */
    private void insertDependency(Long jobId, Long upstreamId) {
        JobDependencyPo dependency = new JobDependencyPo();
        dependency.setJobId(jobId);
        dependency.setUpstreamJobId(upstreamId);
        jobDependencyMapper.insert(dependency);
    }

    /**
     * 查询指定任务的上游任务ID列表
     *
     * @param jobId 任务ID
     * @return 上游任务ID列表（去重）
     */
    private List<Long> listUpstreamIds(Long jobId) {
        return jobDependencyMapper.selectList(new LambdaQueryWrapper<JobDependencyPo>()
                        .eq(JobDependencyPo::getJobId, jobId)).stream()
                .map(JobDependencyPo::getUpstreamJobId)
                .distinct()
                .toList();
    }

    /**
     * 查询指定任务的下游任务ID列表
     *
     * @param jobId 任务ID
     * @return 下游任务ID列表（去重）
     */
    private List<Long> listDownstreamIds(Long jobId) {
        return jobDependencyMapper.selectList(new LambdaQueryWrapper<JobDependencyPo>()
                        .eq(JobDependencyPo::getUpstreamJobId, jobId)).stream()
                .map(JobDependencyPo::getJobId)
                .distinct()
                .toList();
    }
}
