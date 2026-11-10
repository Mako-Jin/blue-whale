package com.bluewhale.audit.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.audit.dto.ApiLogQueryRequest;
import com.bluewhale.audit.vo.ApiLogVO;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.log.entity.ApiLogRecord;
import com.bluewhale.common.log.mapper.ApiLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 后端接口日志服务单测（REQ-SEC-006 通道①查询）。
 *
 * <p>覆盖：分页映射与 {@code size} 收敛 [1,200]、查询条件拼装与 {@code create_time} 倒序、空结果。</p>
 *
 * @author BlueWhale
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class ApiLogServiceImplTest {

    /** 单页上限 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 查询样例用户ID */
    private static final long SAMPLE_USER_ID = 1001L;

    /** 查询样例请求URI */
    private static final String SAMPLE_REQUEST_URI = "/auth/login";

    private final ApiLogMapper apiLogMapper = mock(ApiLogMapper.class);
    private final ApiLogServiceImpl service = new ApiLogServiceImpl(apiLogMapper);

    /**
     * 初始化 MyBatis-Plus 实体元数据，使 Lambda 列缓存可用（{@code getSqlSegment} 依赖该缓存）
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ApiLogRecord.class);
    }

    @Test
    @DisplayName("分页：映射记录并收敛 size 上限到 200")
    void shouldMapRecordsAndClampPageSize() {
        Page<ApiLogRecord> dbPage = new Page<>(1L, MAX_PAGE_SIZE);
        dbPage.setRecords(List.of(sampleRecord()));
        dbPage.setTotal(1L);
        doReturn(dbPage).when(apiLogMapper).selectPage(any(Page.class), any());

        ApiLogQueryRequest request = new ApiLogQueryRequest();
        request.setSize(9999L);

        PageResult<ApiLogVO> result = service.page(request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getRequestUri()).isEqualTo(SAMPLE_REQUEST_URI);

        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(apiLogMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(MAX_PAGE_SIZE);
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
    }

    @Test
    @DisplayName("分页：size 下界收敛到 1")
    void shouldClampPageSizeToLowerBound() {
        Page<ApiLogRecord> dbPage = new Page<>(1L, 1L);
        dbPage.setRecords(List.of());
        dbPage.setTotal(0L);
        doReturn(dbPage).when(apiLogMapper).selectPage(any(Page.class), any());

        ApiLogQueryRequest request = new ApiLogQueryRequest();
        request.setSize(0L);

        service.page(request);

        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(apiLogMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(1L);
    }

    @Test
    @DisplayName("分页：查询条件拼装完整并按 create_time 倒序")
    void shouldBuildConditionsAndOrderByCreateTimeDesc() {
        Page<ApiLogRecord> dbPage = new Page<>(1L, 10L);
        dbPage.setRecords(List.of());
        dbPage.setTotal(0L);
        doReturn(dbPage).when(apiLogMapper).selectPage(any(Page.class), any());

        ApiLogQueryRequest request = new ApiLogQueryRequest();
        request.setTraceId("trace-1");
        request.setUserId(SAMPLE_USER_ID);
        request.setModule("whale-auth");
        request.setRequestUri(SAMPLE_REQUEST_URI);
        request.setHttpMethod("POST");
        request.setBizStatus(1);
        request.setStartTime(LocalDateTime.now().minusHours(1));
        request.setEndTime(LocalDateTime.now());

        service.page(request);

        ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(apiLogMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String normalized = wrapperCaptor.getValue().getSqlSegment()
                .replaceAll("[_\\s]", "").toLowerCase(Locale.ROOT);
        assertThat(normalized).contains("traceid").contains("userid").contains("module")
                .contains("requesturi").contains("httpmethod").contains("bizstatus")
                .contains("createtime").contains("orderby").contains("desc");
    }

    @Test
    @DisplayName("分页：无记录时返回空集与总数 0")
    void shouldReturnEmptyWhenNoRecords() {
        Page<ApiLogRecord> dbPage = new Page<>(1L, 10L);
        dbPage.setRecords(List.of());
        dbPage.setTotal(0L);
        doReturn(dbPage).when(apiLogMapper).selectPage(any(Page.class), any());

        PageResult<ApiLogVO> result = service.page(new ApiLogQueryRequest());

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    /**
     * 构造样例接口日志记录
     *
     * @return 接口日志记录
     */
    private static ApiLogRecord sampleRecord() {
        ApiLogRecord record = new ApiLogRecord();
        record.setTraceId("trace-1");
        record.setUserId(SAMPLE_USER_ID);
        record.setUsername("admin");
        record.setModule("whale-auth");
        record.setRequestUri(SAMPLE_REQUEST_URI);
        record.setHttpMethod("POST");
        record.setResponseCode(200);
        record.setBizStatus(1);
        record.setCostMs(12L);
        record.setCreateTime(LocalDateTime.now());
        return record;
    }
}
