package com.bluewhale.forum.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @提及解析器单测。
 *
 * <p>这几条用例都是线上真会踩的：全角 @ 来自中文输入法，邮箱误判来自「@ 就是提及」的直觉，
 * 中文最长匹配来自「@张三的说法」这种没有分隔符的写法。</p>
 *
 * @author BlueWhale
 */
class MentionParserTest {

    @Test
    @DisplayName("基本解析：@昵称 后接空格")
    void parsesSimpleMention() {
        MentionParser.Result result = MentionParser.parse("@张三 你说的对", 20);

        assertThat(result.names()).containsExactly("张三");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    @DisplayName("全角 ＠ 也要认：中文输入法下极常见")
    void parsesFullwidthAt() {
        MentionParser.Result result = MentionParser.parse("＠李四 看下这个", 20);

        assertThat(result.names()).containsExactly("李四");
    }

    @Test
    @DisplayName("邮箱不算提及：@ 前面是字母")
    void ignoresEmailAddress() {
        MentionParser.Result result = MentionParser.parse("联系 zhangsan@example.com 即可", 20);

        assertThat(result.names()).isEmpty();
    }

    @Test
    @DisplayName("同一层重复 @ 只算一次")
    void deduplicates() {
        MentionParser.Result result = MentionParser.parse("@张三 @张三 @李四", 20);

        assertThat(result.names()).containsExactly("张三", "李四");
    }

    @Test
    @DisplayName("超过上限截断并标记，不静默丢弃")
    void truncatesWhenOverLimit() {
        MentionParser.Result result = MentionParser.parse("@a1 @b2 @c3", 2);

        assertThat(result.names()).containsExactly("a1", "b2");
        assertThat(result.truncated()).isTrue();
    }

    @Test
    @DisplayName("昵称尾部 ASCII 标点不并入昵称")
    void trimsTrailingPunctuation() {
        MentionParser.Result result = MentionParser.parse("@wang. 你好", 20);

        assertThat(result.names()).containsExactly("wang");
    }

    @Test
    @DisplayName("中文无分隔符按最长匹配：宁可解析不到也不猜")
    void keepsLongestChineseMatch() {
        MentionParser.Result result = MentionParser.parse("@张三的说法有道理", 20);

        assertThat(result.names()).containsExactly("张三的说法有道理");
    }

    @Test
    @DisplayName("空输入返回空结果，不抛异常")
    void handlesBlankInput() {
        assertThat(MentionParser.parse(null, 20).names()).isEmpty();
        assertThat(MentionParser.parse("", 20).names()).isEmpty();
        assertThat(MentionParser.parse("没有提及", 20).names()).isEmpty();
    }
}
