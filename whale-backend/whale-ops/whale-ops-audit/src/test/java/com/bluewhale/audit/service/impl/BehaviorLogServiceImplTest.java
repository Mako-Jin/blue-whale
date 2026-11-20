package com.bluewhale.audit.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.audit.dto.BehaviorLogQueryRequest;
import com.bluewhale.audit.dto.LogBatchRequest;
import com.bluewhale.audit.dto.LogItem;
import com.bluewhale.audit.vo.BehaviorLogVO;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.log.entity.BehaviorLogRecord;
import com.bluewhale.common.log.mapper.BehaviorLogMapper;
import com.bluewhale.common.log.sink.LogPersistSupport;
import com.bluewhale.common.log.sink.LogSink;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 前端用户行为埋点服务单测（REQ-SEC-006 / checklist 8.6）。
 *
 * <p>覆盖：分页映射与 {@code size} 收敛 [1,200]、查询条件拼装、{@code create_time} 倒序、
 * 批量上报 channel {@code behavior} 投递与 userId 回填、落库提交异常不冒泡。</p>
 *
 * @author BlueWhale
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class BehaviorLogServiceImplTest {

    /** 单页上限 */
    private static final long MAX_PAGE_SIZE = 200L;
    /** 网关注入的用户ID */
    private static final long GATEWAY_USER_ID = 1001L;

    private final BehaviorLogMapper behaviorLogMapper = mock(BehaviorLogMapper.class);
    private final AtomicReference<String> channelRef = new AtomicReference<>();
    private final LogSink capturingSink = (task, channel) -> {
        channelRef.set(channel);
        task.run();
    };
    private final LogPersistSupport persistSupport = new LogPersistSupport(capturingSink);
    private final BehaviorLogServiceImpl service = new BehaviorLogServiceImpl(behaviorLogMapper, persistSupport);

    /**
     * 初始化 MyBatis-Plus 实体元数据，使 Lambda 列缓存可用（{@code getSqlSegment} 依赖该缓存）
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BehaviorLogRecord.class);
    }

    @Test
    @DisplayName("分页：映射记录并收敛 size 上限到 200")
    void shouldMapRecordsAndClampPageSize() {
        Page<BehaviorLogRecord> dbPage = new Page<>(1L, MAX_PAGE_SIZE);
        dbPage.setRecords(List.of(sampleRecord()));
        dbPage.setTotal(1L);
        doReturn(dbPage).when(behaviorLogMapper).selectPage(any(Page.class), any());

        BehaviorLogQueryRequest request = new BehaviorLogQueryRequest();
        request.setSize(9999L);

        PageResult<BehaviorLogVO> result = service.page(request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getEventName()).isEqualTo("用户管理页访问");

        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(behaviorLogMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(MAX_PAGE_SIZE);
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
    }

    @Test
    @DisplayName("分页：size 下界收敛到 1")
    void shouldClampPageSizeToLowerBound() {
        Page<BehaviorLogRecord> dbPage = new Page<>(1L, 1L);
        dbPage.setRecords(List.of());
        dbPage.setTotal(0L);
        doReturn(dbPage).when(behaviorLogMapper).selectPage(any(Page.class), any());

        BehaviorLogQueryRequest request = new BehaviorLogQueryRequest();
        request.setSize(0L);

        service.page(request);

        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(behaviorLogMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(1L);
    }

    @Test
    @DisplayName("分页：查询条件拼装完整并按 create_time 倒序")
    void shouldBuildConditionsAndOrderByCreateTimeDesc() {
        Page<BehaviorLogRecord> dbPage = new Page<>(1L, 10L);
        dbPage.setRecords(List.of());
        dbPage.setTotal(0L);
        doReturn(dbPage).when(behaviorLogMapper).selectPage(any(Page.class), any());

        BehaviorLogQueryRequest request = new BehaviorLogQueryRequest();
        request.setTraceId("trace-1");
        request.setUserId(GATEWAY_USER_ID);
        request.setClientId("client-1");
        request.setEventType("CLICK");
        request.setEventName("用户管理页访问");
        request.setStartTime(LocalDateTime.now().minusHours(1));
        request.setEndTime(LocalDateTime.now());

        service.page(request);

        ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(behaviorLogMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String normalized = wrapperCaptor.getValue().getSqlSegment()
                .replaceAll("[_\\s]", "").toLowerCase(Locale.ROOT);
        assertThat(normalized).contains("traceid").contains("userid").contains("clientid")
                .contains("eventtype").contains("eventname").contains("createtime")
                .contains("orderby").contains("desc");
    }

    @Test
    @DisplayName("批量上报：按 channel behavior 投递并为每条记录回填 userId")
    void shouldSubmitBatchWithBehaviorChannelAndUserId() {
        LogBatchRequest request = batchOf(3);

        service.saveBatch(request, GATEWAY_USER_ID);

        ArgumentCaptor<BehaviorLogRecord> captor = ArgumentCaptor.forClass(BehaviorLogRecord.class);
        verify(behaviorLogMapper, times(3)).insert(captor.capture());
        assertThat(captor.getAllValues()).isNotEmpty()
                .allSatisfy(record -> assertThat(record.getUserId()).isEqualTo(GATEWAY_USER_ID));
        assertThat(channelRef.get()).isEqualTo("behavior");
    }

    @Test
    @DisplayName("批量上报：无有效条目时不投递")
    void shouldNotSubmitWhenNoItems() {
        service.saveBatch(new LogBatchRequest(), GATEWAY_USER_ID);

        verify(behaviorLogMapper, never()).insert(any(BehaviorLogRecord.class));
        assertThat(channelRef.get()).isNull();
    }

    @Test
    @DisplayName("落库提交异常：不冒泡影响业务")
    void shouldNotBubbleWhenSubmitFails() {
        LogPersistSupport failingSupport =
                new LogPersistSupport((task, channel) -> {
                    throw new IllegalStateException("sink down");
                });
        BehaviorLogServiceImpl failingService =
                new BehaviorLogServiceImpl(behaviorLogMapper, failingSupport);

        assertThatCode(() -> failingService.saveBatch(batchOf(1), GATEWAY_USER_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("逐条落库：单条异常不中断后续条目")
    void shouldContinueWhenSingleInsertFails() {
        doThrow(new IllegalStateException("db down"))
                .doReturn(1)
                .when(behaviorLogMapper).insert(any(BehaviorLogRecord.class));

        assertThatCode(() -> service.saveBatch(batchOf(2), GATEWAY_USER_ID)).doesNotThrowAnyException();

        verify(behaviorLogMapper, times(2)).insert(any(BehaviorLogRecord.class));
    }

    /**
     * 构造批量上报请求
     *
     * @param size 条目数
     * @return 批量上报请求
     */
    private static LogBatchRequest batchOf(int size) {
        LogBatchRequest request = new LogBatchRequest();
        request.setTraceId("trace-1");
        request.setClientId("client-1");
        request.setPageUrl("/home");
        request.setItems(IntStream.range(0, size).mapToObj(index -> {
            LogItem item = new LogItem();
            item.setEventType("CLICK");
            item.setEventName("用户管理页访问");
            return item;
        }).toList());
        return request;
    }

    /**
     * 构造样例行为埋点记录
     *
     * @return 行为埋点记录
     */
    private static BehaviorLogRecord sampleRecord() {
        BehaviorLogRecord record = new BehaviorLogRecord();
        record.setTraceId("trace-1");
        record.setUserId(GATEWAY_USER_ID);
        record.setClientId("client-1");
        record.setEventType("PAGE_VIEW");
        record.setEventName("用户管理页访问");
        record.setPageUrl("/home");
        record.setTarget("#user-table");
        record.setPayload("{\"rows\":10}");
        record.setCreateTime(LocalDateTime.now());
        return record;
    }
}
