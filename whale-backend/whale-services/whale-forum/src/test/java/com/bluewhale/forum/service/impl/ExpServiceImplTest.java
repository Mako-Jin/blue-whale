package com.bluewhale.forum.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.ExpSummaryVO;
import com.bluewhale.forum.entity.ForumExpLedger;
import com.bluewhale.forum.mapper.ForumExpLedgerMapper;
import com.bluewhale.forum.service.ExpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 论坛经验账本单测：幂等、参数兜底、故障不阻断主流程。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ExpServiceImplTest {

    @Mock
    private ForumExpLedgerMapper ledgerMapper;

    @Spy
    private ForumProperties properties = new ForumProperties();

    @InjectMocks
    private ExpServiceImpl expService;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("正常入账：按事件写入经验与帖子锚点")
    void awardWritesLedger() {
        when(ledgerMapper.insertIgnore(any(ForumExpLedger.class))).thenReturn(1);

        int rows = expService.award(100L, ExpService.EVENT_THREAD_PUBLISH, 5, 11L);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<ForumExpLedger> captor = ArgumentCaptor.forClass(ForumExpLedger.class);
        verify(ledgerMapper).insertIgnore(captor.capture());
        ForumExpLedger ledger = captor.getValue();
        assertThat(ledger.getTenantId()).isEqualTo(1L);
        assertThat(ledger.getUserId()).isEqualTo(100L);
        assertThat(ledger.getEvent()).isEqualTo(ExpService.EVENT_THREAD_PUBLISH);
        assertThat(ledger.getAmount()).isEqualTo(5);
        assertThat(ledger.getRefThreadId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("重复入账：唯一键忽略插入，返回 0")
    void duplicateAwardIsIdempotent() {
        when(ledgerMapper.insertIgnore(any(ForumExpLedger.class))).thenReturn(0);

        assertThat(expService.award(100L, ExpService.EVENT_LIKE_GIVEN, 2, 11L)).isZero();
    }

    @Test
    @DisplayName("非法参数（空用户/非正分值）直接跳过，不落库")
    void invalidArgumentsAreSkipped() {
        assertThat(expService.award(null, ExpService.EVENT_THREAD_PUBLISH, 5, 11L)).isZero();
        assertThat(expService.award(100L, ExpService.EVENT_THREAD_PUBLISH, 0, 11L)).isZero();
        verify(ledgerMapper, never()).insertIgnore(any(ForumExpLedger.class));
    }

    @Test
    @DisplayName("账本故障只告警，不影响发帖主流程")
    void ledgerFailureDoesNotBreakCaller() {
        when(ledgerMapper.insertIgnore(any(ForumExpLedger.class)))
                .thenThrow(new RuntimeException("db down"));

        assertThat(expService.award(100L, ExpService.EVENT_THREAD_PUBLISH, 5, 11L)).isZero();
    }

    @Test
    @DisplayName("我的经验：总额 + 最近流水按时间倒序")
    void myExpReturnsTotalAndRecent() {
        when(ledgerMapper.sumExp(1L, 100L)).thenReturn(207L);
        ForumExpLedger row = new ForumExpLedger();
        row.setUserId(100L);
        row.setEvent(ExpService.EVENT_ESSENCE_AWARD);
        row.setAmount(200);
        row.setRefThreadId(11L);
        row.setCreateTime(LocalDateTime.now());
        when(ledgerMapper.selectList(any())).thenReturn(List.of(row));

        ExpSummaryVO summary = expService.myExp(actor());

        assertThat(summary.getTotal()).isEqualTo(207L);
        assertThat(summary.getRecent()).hasSize(1);
        assertThat(summary.getRecent().get(0).getEvent()).isEqualTo(ExpService.EVENT_ESSENCE_AWARD);
        assertThat(summary.getRecent().get(0).getRefThreadId()).isEqualTo("11");
    }

    /**
     * 构造操作者。
     *
     * @return 操作者
     */
    private ForumActor actor() {
        return new ForumActor(100L, Set.of(ForumPermissions.BROWSE), null);
    }
}
