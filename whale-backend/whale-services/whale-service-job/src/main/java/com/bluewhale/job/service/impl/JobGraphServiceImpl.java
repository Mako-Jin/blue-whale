package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.job.mapper.JobDependencyMapper;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.po.JobDependencyPo;
import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.service.JobGraphService;
import com.bluewhale.job.vo.JobGraphEdgeVO;
import com.bluewhale.job.vo.JobGraphNodeVO;
import com.bluewhale.job.vo.JobGraphValidateVO;
import com.bluewhale.job.vo.JobGraphVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 任务依赖拓扑图服务实现。
 *
 * <p>图接口一次返回节点与边，边方向固定为上游 → 下游；分组过滤时只有两端都在节点集合内的边才保留。
 * 环检测使用三色 DFS 找出第一条回边并截取闭环，作为保存前校验与数据体检入口。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobGraphServiceImpl implements JobGraphService {

    private final JobInfoMapper jobInfoMapper;

    private final JobDependencyMapper jobDependencyMapper;

    @Override
    public JobGraphVO graph(String jobGroup) {
        List<JobInfoPo> jobs = jobInfoMapper.selectList(new LambdaQueryWrapper<JobInfoPo>()
                .eq(StringUtils.hasText(jobGroup), JobInfoPo::getJobGroup, jobGroup)
                .orderByAsc(JobInfoPo::getId));
        Set<Long> nodeIds = jobs.stream().map(JobInfoPo::getId).collect(Collectors.toSet());
        List<JobGraphEdgeVO> edges = new ArrayList<>();
        if (!nodeIds.isEmpty()) {
            jobDependencyMapper.selectList(new LambdaQueryWrapper<JobDependencyPo>()
                            .orderByAsc(JobDependencyPo::getUpstreamJobId)
                            .orderByAsc(JobDependencyPo::getJobId)).stream()
                    .filter(dependency -> nodeIds.contains(dependency.getUpstreamJobId())
                            && nodeIds.contains(dependency.getJobId()))
                    .forEach(dependency -> edges.add(
                            new JobGraphEdgeVO(dependency.getUpstreamJobId(), dependency.getJobId())));
        }
        JobGraphVO vo = new JobGraphVO();
        vo.setNodes(jobs.stream().map(JobGraphServiceImpl::toNode).toList());
        vo.setEdges(edges);
        return vo;
    }

    @Override
    public JobGraphValidateVO validate() {
        List<JobInfoPo> jobs = jobInfoMapper.selectList(new LambdaQueryWrapper<JobInfoPo>()
                .select(JobInfoPo::getId));
        Set<Long> nodes = new TreeSet<>();
        jobs.forEach(job -> nodes.add(job.getId()));
        Map<Long, List<Long>> adjacency = new HashMap<>();
        jobDependencyMapper.selectList(new LambdaQueryWrapper<JobDependencyPo>()
                        .orderByAsc(JobDependencyPo::getUpstreamJobId)
                        .orderByAsc(JobDependencyPo::getJobId)).stream()
                .filter(dependency -> nodes.contains(dependency.getUpstreamJobId())
                        && nodes.contains(dependency.getJobId()))
                .forEach(dependency -> adjacency
                        .computeIfAbsent(dependency.getUpstreamJobId(), key -> new ArrayList<>())
                        .add(dependency.getJobId()));
        List<Long> cycle = findCycle(nodes, adjacency);
        JobGraphValidateVO vo = new JobGraphValidateVO();
        vo.setAcyclic(cycle.isEmpty());
        vo.setCycleJobIds(cycle);
        if (!cycle.isEmpty()) {
            log.warn("任务依赖整图检测发现环: {}", cycle);
        }
        return vo;
    }

    /**
     * 三色 DFS 查找第一条闭环：0 未访问 / 1 访问中 / 2 已完成
     *
     * @param nodes 节点集合（升序，保证结果稳定）
     * @param adjacency 上游 → 下游邻接表
     * @return 闭环路径（首尾相同）；无环返回空列表
     */
    private static List<Long> findCycle(Set<Long> nodes, Map<Long, List<Long>> adjacency) {
        Map<Long, Integer> states = new HashMap<>();
        Deque<Long> path = new ArrayDeque<>();
        for (Long node : nodes) {
            if (states.getOrDefault(node, 0) == 0) {
                List<Long> cycle = dfs(node, adjacency, states, path);
                if (!cycle.isEmpty()) {
                    return cycle;
                }
            }
        }
        return List.of();
    }

    /**
     * DFS 单步：遇「访问中」节点即为回边，截取当前栈上的环
     *
     * @param node 当前节点
     * @param adjacency 上游 → 下游邻接表
     * @param states 节点状态
     * @param path 当前 DFS 栈
     * @return 闭环路径；本轮无环返回空列表
     */
    private static List<Long> dfs(Long node, Map<Long, List<Long>> adjacency, Map<Long, Integer> states,
                                  Deque<Long> path) {
        states.put(node, 1);
        path.addLast(node);
        for (Long downstream : adjacency.getOrDefault(node, List.of())) {
            int state = states.getOrDefault(downstream, 0);
            if (state == 1) {
                return extractCycle(path, downstream);
            }
            if (state == 0) {
                List<Long> cycle = dfs(downstream, adjacency, states, path);
                if (!cycle.isEmpty()) {
                    return cycle;
                }
            }
        }
        path.removeLast();
        states.put(node, 2);
        return List.of();
    }

    /**
     * 从当前 DFS 栈中截取从 {@code start} 到栈顶的闭环
     *
     * @param path 当前 DFS 栈
     * @param start 回边指向的节点
     * @return 闭环路径（首尾均为 {@code start}）
     */
    private static List<Long> extractCycle(Deque<Long> path, Long start) {
        List<Long> cycle = new ArrayList<>();
        boolean collecting = false;
        for (Long current : path) {
            if (current.equals(start)) {
                collecting = true;
            }
            if (collecting) {
                cycle.add(current);
            }
        }
        cycle.add(start);
        return cycle;
    }

    /**
     * 任务实体转图节点
     *
     * @param job 任务实体
     * @return 图节点
     */
    private static JobGraphNodeVO toNode(JobInfoPo job) {
        JobGraphNodeVO node = new JobGraphNodeVO();
        node.setId(job.getId());
        node.setJobName(job.getJobName());
        node.setJobGroup(job.getJobGroup());
        node.setStatus(job.getStatus());
        node.setExecutorApp(job.getExecutorApp());
        node.setJobCode(job.getJobCode());
        node.setCronExpression(job.getCronExpression());
        node.setShardTotal(job.getShardTotal());
        node.setTimeoutSeconds(job.getTimeoutSeconds());
        node.setFailThreshold(job.getFailThreshold());
        node.setFailCount(job.getFailCount());
        return node;
    }
}
