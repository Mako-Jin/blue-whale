package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.job.dto.JobAlertQueryRequest;
import com.bluewhale.job.mapper.JobAlertMapper;
import com.bluewhale.job.po.JobAlertPo;
import com.bluewhale.job.vo.JobAlertVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobAlertServiceImpl} 单元测试：恢复标识映射与通道模糊筛选。
 *
 * @author BlueWhale
 */
class JobAlertServiceImplTest {

    private final JobAlertMapper jobAlertMapper = mock(JobAlertMapper.class);

    private final JobAlertServiceImpl service = new JobAlertServiceImpl(jobAlertMapper);

    /**
     * 注册实体元信息缓存：Lambda 条件构造器需要按实体解析列名。
     */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, JobAlertPo.class);
    }

    private static JobAlertPo alert(String level, String channel) {
        JobAlertPo alert = new JobAlertPo();
        alert.setId(1L);
        alert.setJobId(10L);
        alert.setJobName("任务A");
        alert.setLevel(level);
        alert.setChannel(channel);
        alert.setStatus(1);
        return alert;
    }

    @Test
    @DisplayName("page：INFO 级告警标记为恢复通知，ERROR 不标记")
    void shouldMarkRecoveredForInfoLevel() {
        Page<JobAlertPo> page = new Page<>(1L, 10L);
        page.setRecords(List.of(alert("INFO", "WEBHOOK"), alert("ERROR", "WEBHOOK")));
        page.setTotal(2L);
        when(jobAlertMapper.selectPage(ArgumentMatchers.<IPage<JobAlertPo>>any(),
                ArgumentMatchers.<Wrapper<JobAlertPo>>any())).thenReturn(page);

        PageResult<JobAlertVO> result = service.page(new JobAlertQueryRequest());

        assertEquals(2L, result.getTotal());
        assertTrue(result.getRecords().get(0).getRecovered());
        assertFalse(result.getRecords().get(1).getRecovered());
    }

    @Test
    @DisplayName("page：channel 使用模糊匹配，可命中逗号拼接的通道名")
    @SuppressWarnings("unchecked")
    void shouldFilterChannelFuzzily() {
        Page<JobAlertPo> page = new Page<>(1L, 10L);
        page.setRecords(List.of());
        page.setTotal(0L);
        when(jobAlertMapper.selectPage(ArgumentMatchers.<IPage<JobAlertPo>>any(),
                ArgumentMatchers.<Wrapper<JobAlertPo>>any())).thenReturn(page);
        JobAlertQueryRequest request = new JobAlertQueryRequest();
        request.setChannel("DINGTALK");

        service.page(request);

        ArgumentCaptor<Wrapper<JobAlertPo>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(jobAlertMapper).selectPage(ArgumentMatchers.<IPage<JobAlertPo>>any(), captor.capture());
        LambdaQueryWrapper<JobAlertPo> wrapper = (LambdaQueryWrapper<JobAlertPo>) captor.getValue();
        assertTrue(wrapper.getTargetSql().toLowerCase().contains("channel"), wrapper.getTargetSql());
    }

    @Test
    @DisplayName("page：channel 为空时不追加过滤条件")
    @SuppressWarnings("unchecked")
    void shouldIgnoreBlankChannel() {
        Page<JobAlertPo> page = new Page<>(1L, 10L);
        page.setRecords(List.of());
        page.setTotal(0L);
        when(jobAlertMapper.selectPage(ArgumentMatchers.<IPage<JobAlertPo>>any(),
                ArgumentMatchers.<Wrapper<JobAlertPo>>any())).thenReturn(page);
        JobAlertQueryRequest request = new JobAlertQueryRequest();
        request.setChannel(" ");

        service.page(request);

        ArgumentCaptor<Wrapper<JobAlertPo>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(jobAlertMapper).selectPage(ArgumentMatchers.<IPage<JobAlertPo>>any(), captor.capture());
        LambdaQueryWrapper<JobAlertPo> wrapper = (LambdaQueryWrapper<JobAlertPo>) captor.getValue();
        assertFalse(wrapper.getTargetSql().toLowerCase().contains("channel"), wrapper.getTargetSql());
    }
}
