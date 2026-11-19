package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.job.dto.JobLogStatsQueryRequest;
import com.bluewhale.job.mapper.JobInfoMapper;
import com.bluewhale.job.mapper.JobLogMapper;
import com.bluewhale.job.po.JobInfoPo;
import com.bluewhale.job.po.JobLogPo;
import com.bluewhale.job.scheduler.handler.ExecutorLogClient;
import com.bluewhale.job.vo.JobLogDailyStatVO;
import com.bluewhale.job.vo.JobLogStatsVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 执行日志统计单元测试：按天分桶、P95、TopN 与参数校验。
 *
 * @author BlueWhale
 */
class JobLogServiceImplStatsTest {

    private final JobLogMapper jobLogMapper = mock(JobLogMapper.class);

    private final JobInfoMapper jobInfoMapper = mock(JobInfoMapper.class);

    private final ExecutorLogClient executorLogClient = mock(ExecutorLogClient.class);

    private final JobLogServiceImpl service =
            new JobLogServiceImpl(jobLogMapper, jobInfoMapper, executorLogClient);

    /**
     * 注册实体元信息缓存：Lambda 条件构造器需要按实体解析列名。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, JobInfoPo.class);
        TableInfoHelper.initTableInfo(assistant, JobLogPo.class);
    }

    @Test
    @DisplayName("统计：跨天分桶、均值与 P95 按离散口径计算")
    void shouldAggregateDailyStats() {
        when(jobLogMapper.selectList(any())).thenReturn(List.of(
                log(1L, "job-1", 1, LocalDateTime.of(2026, 11, 17, 10, 0, 0), 10),
                log(1L, "job-1", 1, LocalDateTime.of(2026, 11, 17, 11, 0, 0), 20),
                log(2L, "job-2", -1, LocalDateTime.of(2026, 11, 17, 12, 0, 0), 30),
                log(1L, "job-1", 1, LocalDateTime.of(2026, 11, 18, 10, 0, 0), 100)));

        JobLogStatsVO stats = service.stats(request(null, null));

        assertEquals(4, stats.getTotalCount());
        assertFalse(stats.isSampled());
        assertEquals(2, stats.getDaily().size());
        JobLogDailyStatVO firstDay = stats.getDaily().get(0);
        assertEquals(LocalDate.of(2026, 11, 17), firstDay.getDate());
        assertEquals(2, firstDay.getSuccessCount());
        assertEquals(1, firstDay.getFailureCount());
        assertEquals(20, firstDay.getAvgDurationMillis());
        assertEquals(30, firstDay.getP95DurationMillis());
        JobLogDailyStatVO secondDay = stats.getDaily().get(1);
        assertEquals(100, secondDay.getAvgDurationMillis());
        assertEquals(100, secondDay.getP95DurationMillis());
    }

    @Test
    @DisplayName("统计：失败 Top 与耗时 Top 按任务聚合排序")
    void shouldBuildTopLists() {
        when(jobLogMapper.selectList(any())).thenReturn(List.of(
                log(1L, "job-1", 1, LocalDateTime.of(2026, 11, 17, 10, 0, 0), 10),
                log(1L, "job-1", 1, LocalDateTime.of(2026, 11, 17, 11, 0, 0), 20),
                log(1L, "job-1", 1, LocalDateTime.of(2026, 11, 18, 10, 0, 0), 100),
                log(2L, "job-2", -1, LocalDateTime.of(2026, 11, 17, 12, 0, 0), 30)));

        JobLogStatsVO stats = service.stats(request(null, null));

        assertEquals(1, stats.getFailureTop().size());
        assertEquals(2L, stats.getFailureTop().get(0).getJobId());
        assertEquals(1, stats.getFailureTop().get(0).getFailureCount());
        assertEquals(2, stats.getDurationTop().size());
        assertEquals(1L, stats.getDurationTop().get(0).getJobId());
        assertEquals(43, stats.getDurationTop().get(0).getAvgDurationMillis());
        assertEquals(3, stats.getDurationTop().get(0).getExecutionCount());
    }

    @Test
    @DisplayName("统计：失败原因按异常类型聚合取 TopN，无法识别归 OTHER")
    void shouldBuildErrorTypeTop() {
        when(jobLogMapper.selectList(any())).thenReturn(
                List.of(log(1L, "job-1", -1, LocalDateTime.of(2026, 11, 17, 10, 0, 0), 10)),
                List.of(error("BizException: 参数错误"), error("BizException: 余额不足"),
                        error("HttpServerErrorException$BadGateway: 502"), error("连接超时")));

        JobLogStatsVO stats = service.stats(request(null, null));

        assertEquals(3, stats.getErrorTypeTop().size());
        assertEquals("BizException", stats.getErrorTypeTop().get(0).getErrorType());
        assertEquals(2, stats.getErrorTypeTop().get(0).getCount());
        assertEquals("BizException: 参数错误", stats.getErrorTypeTop().get(0).getSampleMessage());
        assertEquals("HttpServerErrorException$BadGateway", stats.getErrorTypeTop().get(1).getErrorType());
        assertEquals("OTHER", stats.getErrorTypeTop().get(2).getErrorType());
    }

    @Test
    @DisplayName("统计：指定任务时不返回 TopN")
    void shouldSkipTopListsWhenJobIdGiven() {
        when(jobLogMapper.selectList(any())).thenReturn(List.of(
                log(1L, "job-1", 1, LocalDateTime.of(2026, 11, 17, 10, 0, 0), 10)));

        JobLogStatsQueryRequest request = request(null, null);
        request.setJobId(1L);
        JobLogStatsVO stats = service.stats(request);

        assertTrue(stats.getFailureTop().isEmpty());
        assertTrue(stats.getDurationTop().isEmpty());
        assertTrue(stats.getErrorTypeTop().isEmpty());
    }

    @Test
    @DisplayName("统计：分组下无任务时返回空结构")
    void shouldReturnEmptyWhenGroupHasNoJob() {
        when(jobInfoMapper.selectList(any())).thenReturn(List.of());

        JobLogStatsVO stats = service.stats(request("EMPTY", null));

        assertEquals(0, stats.getTotalCount());
        assertTrue(stats.getDaily().isEmpty());
    }

    @Test
    @DisplayName("统计：起止时间非法抛参数错误")
    void shouldRejectInvalidRange() {
        JobLogStatsQueryRequest request = request(null, null);
        LocalDateTime now = LocalDateTime.of(2026, 11, 18, 12, 0, 0);
        request.setFrom(now);
        request.setTo(now);

        assertThrows(BizException.class, () -> service.stats(request));
    }

    /**
     * 构造统计请求
     *
     * @param jobGroup 任务分组
     * @param jobId 任务ID
     * @return 统计请求
     */
    private static JobLogStatsQueryRequest request(String jobGroup, Long jobId) {
        JobLogStatsQueryRequest request = new JobLogStatsQueryRequest();
        request.setJobGroup(jobGroup);
        request.setJobId(jobId);
        request.setFrom(LocalDateTime.of(2026, 11, 17, 0, 0, 0));
        request.setTo(LocalDateTime.of(2026, 11, 18, 23, 59, 59));
        return request;
    }

    /**
     * 构造带错误信息的失败日志
     *
     * @param errorMsg 错误信息
     * @return 执行日志实体
     */
    private static JobLogPo error(String errorMsg) {
        JobLogPo log = new JobLogPo();
        log.setStatus(-1);
        log.setErrorMsg(errorMsg);
        return log;
    }

    /**
     * 构造执行日志
     *
     * @param jobId 任务ID
     * @param jobName 任务名称
     * @param status 执行状态
     * @param startTime 开始时间
     * @param durationMillis 耗时毫秒数
     * @return 执行日志实体
     */
    private static JobLogPo log(Long jobId, String jobName, int status, LocalDateTime startTime,
                                long durationMillis) {
        JobLogPo log = new JobLogPo();
        log.setJobId(jobId);
        log.setJobName(jobName);
        log.setStatus(status);
        log.setStartTime(startTime);
        log.setEndTime(startTime.plusNanos(durationMillis * 1_000_000L));
        return log;
    }
}
