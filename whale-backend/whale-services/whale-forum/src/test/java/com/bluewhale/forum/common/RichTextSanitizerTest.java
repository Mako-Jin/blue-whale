package com.bluewhale.forum.common;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 富文本消毒单测：危险构造必须被拒绝（拒绝式实现，不做猜测式清洗）。
 *
 * @author BlueWhale
 */
class RichTextSanitizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "<p>正常内容</p>",
            "<p><strong>加粗</strong>与<em>斜体</em></p>",
            "<ul><li>列表</li></ul>",
            "<pre><code>System.out.println(1);</code></pre>",
            "<a href=\"https://example.com\">链接</a>"
    })
    @DisplayName("常规富文本原样通过")
    void allowsNormalRichText(String input) {
        assertThat(RichTextSanitizer.sanitize(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<SCRIPT SRC=//evil.js></SCRIPT>",
            "<iframe src=\"//evil\"></iframe>",
            "<img src=x onerror=alert(1)>",
            "<a href=\"javascript:alert(1)\">点我</a>",
            "<div style=\"width:expression(alert(1))\">x</div>",
            "<object data=\"evil.swf\"></object>"
    })
    @DisplayName("危险标签/属性/协议一律 30030 拒绝")
    void rejectsDangerousMarkup(String payload) {
        assertThatThrownBy(() -> RichTextSanitizer.sanitize(payload))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.CONTENT_REJECTED));
    }

    @Test
    @DisplayName("null 透传、首尾空白去除")
    void trimsAndPassesNull() {
        assertThat(RichTextSanitizer.sanitize(null)).isNull();
        assertThat(RichTextSanitizer.sanitize("  <p>a</p>  ")).isEqualTo("<p>a</p>");
    }

    @Test
    @DisplayName("摘要去标签并截断")
    void summarizesPlainText() {
        String summary = RichTextSanitizer.summarize("<p>你好&nbsp;世界 <strong>论坛</strong></p>", 10);
        assertThat(summary).isEqualTo("你好 世界 论坛");
        assertThat(RichTextSanitizer.summarize("<p>一二三四五六七八九十</p>", 5)).hasSize(5);
        assertThat(RichTextSanitizer.summarize(null, 10)).isEmpty();
    }
}
