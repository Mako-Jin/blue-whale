package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.job.mapper.JobDependencyMapper;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.po.JobDependencyPo;
import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.vo.JobGraphValidateVO;
import com.bluewhale.job.vo.JobGraphVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link JobGraphServiceImpl} 单元测试：图组装、分组过滤与整图环检测。
 *
 * @author BlueWhale
 */
class JobGraphServiceImplTest {

    private final JobInfoMapper jobInfoMapper = mock(JobInfoMapper.class);

    private final JobDependencyMapper jobDependencyMapper = mock(JobDependencyMapper.class);

    private final JobGraphServiceImpl service = new JobGraphServiceImpl(jobInfoMapper, jobDependencyMapper);

    /**
     * 注册实体元信息缓存：Lambda 条件构造器需要按实体解析列名。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, JobInfoPo.class);
        TableInfoHelper.initTableInfo(assistant, JobDependencyPo.class);
    }

    @Test
    @DisplayName("图：边只保留两端都在节点集合内的依赖")
    void shouldKeepEdgesWithinNodes() {
        when(jobInfoMapper.selectList(any())).thenReturn(List.of(job(1L), job(2L)));
        when(jobDependencyMapper.selectList(any())).thenReturn(List.of(
                dependency(1L, 2L), dependency(2L, 3L)));

        JobGraphVO graph = service.graph(null);

        assertEquals(2, graph.getNodes().size());
        assertEquals(1, graph.getEdges().size());
        assertEquals(1L, graph.getEdges().get(0).getFrom());
        assertEquals(2L, graph.getEdges().get(0).getTo());
    }

    @Test
    @DisplayName("环检测：A→B→C→A 返回闭环路径")
    void shouldDetectCycle() {
        when(jobInfoMapper.selectList(any())).thenReturn(List.of(job(1L), job(2L), job(3L)));
        when(jobDependencyMapper.selectList(any())).thenReturn(List.of(
                dependency(1L, 2L), dependency(2L, 3L), dependency(3L, 1L)));

        JobGraphValidateVO result = service.validate();

        assertFalse(result.isAcyclic());
        assertEquals(List.of(1L, 2L, 3L, 1L), result.getCycleJobIds());
    }

    @Test
    @DisplayName("环检测：无环返回空闭环")
    void shouldReturnAcyclicWhenNoCycle() {
        when(jobInfoMapper.selectList(any())).thenReturn(List.of(job(1L), job(2L), job(3L)));
        when(jobDependencyMapper.selectList(any())).thenReturn(List.of(
                dependency(1L, 2L), dependency(2L, 3L)));

        JobGraphValidateVO result = service.validate();

        assertTrue(result.isAcyclic());
        assertTrue(result.getCycleJobIds().isEmpty());
    }

    /**
     * 构造任务实体
     *
     * @param id 任务ID
     * @return 任务实体
     */
    private static JobInfoPo job(Long id) {
        JobInfoPo job = new JobInfoPo();
        job.setId(id);
        job.setJobName("job-" + id);
        job.setJobGroup("DEFAULT");
        job.setStatus(1);
        return job;
    }

    /**
     * 构造依赖边
     *
     * @param upstreamId 上游任务ID
     * @param jobId 下游任务ID
     * @return 依赖实体
     */
    private static JobDependencyPo dependency(Long upstreamId, Long jobId) {
        JobDependencyPo dependency = new JobDependencyPo();
        dependency.setUpstreamJobId(upstreamId);
        dependency.setJobId(jobId);
        return dependency;
    }
}
