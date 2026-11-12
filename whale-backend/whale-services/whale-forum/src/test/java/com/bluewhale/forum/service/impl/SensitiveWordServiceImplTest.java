package com.bluewhale.forum.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.entity.ForumSensitiveWord;
import com.bluewhale.forum.mapper.ForumSensitiveHitMapper;
import com.bluewhale.forum.mapper.ForumSensitiveWordMapper;
import com.bluewhale.forum.service.SensitiveWordService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 敏感词扫描单测：三档动作取最严、REPLACE 等长打码、大小写不敏感。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SensitiveWordServiceImplTest {

    @Mock
    private ForumSensitiveWordMapper wordMapper;

    @Mock
    private ForumSensitiveHitMapper hitMapper;

    private SensitiveWordServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        service = new SensitiveWordServiceImpl(wordMapper, hitMapper, new ForumProperties());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("无命中返回 NONE，文本不变")
    void noHitReturnsNone() {
        mockWords(word("代开发票", "BLOCK"));

        SensitiveWordService.ScanResult result = service.scan("<p>今天食堂的鱼很好吃</p>");

        assertThat(result.action()).isEqualTo(SensitiveWordService.ScanResult.NONE);
        assertThat(result.hit()).isFalse();
        assertThat(result.text()).isEqualTo("<p>今天食堂的鱼很好吃</p>");
    }

    @Test
    @DisplayName("BLOCK 命中：动作 BLOCK，文本原样返回（由调用方拒绝）")
    void blockKeepsText() {
        mockWords(word("代开发票", "BLOCK"));

        SensitiveWordService.ScanResult result = service.scan("需要代开发票请联系");

        assertThat(result.action()).isEqualTo(SensitiveWordService.ScanResult.BLOCK);
        assertThat(result.hitWord()).isEqualTo("代开发票");
        assertThat(result.position()).isEqualTo(2);
        assertThat(result.text()).isEqualTo("需要代开发票请联系");
    }

    @Test
    @DisplayName("REPLACE 命中：原文等长打码，其余字符不动")
    void replaceMasksWithSameLength() {
        mockWords(word("加微信", "REPLACE"));

        SensitiveWordService.ScanResult result = service.scan("详情加微信聊");

        assertThat(result.action()).isEqualTo(SensitiveWordService.ScanResult.REPLACE);
        assertThat(result.text()).isEqualTo("详情***聊");
        assertThat(result.text()).hasSameSizeAs("详情加微信聊");
    }

    @Test
    @DisplayName("同一段文本命中多个词：取最严动作（BLOCK 优先于 REPLACE）")
    void strictestActionWins() {
        mockWords(word("加微信", "REPLACE"), word("博彩", "BLOCK"));

        SensitiveWordService.ScanResult result = service.scan("加微信聊博彩");

        assertThat(result.action()).isEqualTo(SensitiveWordService.ScanResult.BLOCK);
        // BLOCK 时不打码：内容会被整体拒绝，打码反而掩盖证据
        assertThat(result.text()).isEqualTo("加微信聊博彩");
    }

    @Test
    @DisplayName("PENDING 优先于 REPLACE：转待审的不能被静默放行")
    void pendingBeatsReplace() {
        mockWords(word("加微信", "REPLACE"), word("刷单", "PENDING"));

        SensitiveWordService.ScanResult result = service.scan("加微信刷单");

        assertThat(result.action()).isEqualTo(SensitiveWordService.ScanResult.PENDING);
    }

    @Test
    @DisplayName("大小写不敏感：词库小写也能命中大写正文")
    void matchingIsCaseInsensitive() {
        mockWords(word("ad", "BLOCK"));

        SensitiveWordService.ScanResult result = service.scan("这是 AD 推广");

        assertThat(result.action()).isEqualTo(SensitiveWordService.ScanResult.BLOCK);
    }

    /**
     * 打桩词库。
     *
     * @param words 词条
     */
    private void mockWords(ForumSensitiveWord... words) {
        when(wordMapper.selectList(any())).thenReturn(List.of(words));
    }

    /**
     * 构造词条。
     *
     * @param text 词
     * @param action 动作
     * @return 词条
     */
    private ForumSensitiveWord word(String text, String action) {
        ForumSensitiveWord row = new ForumSensitiveWord();
        row.setWord(text);
        row.setAction(action);
        row.setStatus(1);
        return row;
    }
}
