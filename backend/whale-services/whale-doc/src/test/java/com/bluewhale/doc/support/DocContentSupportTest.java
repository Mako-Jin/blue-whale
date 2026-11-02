package com.bluewhale.doc.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.doc.config.DocProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DocContentSupport} 正文体积、纯文本投影与哈希断言。
 *
 * @author BlueWhale
 */
class DocContentSupportTest {

    private final DocProperties properties = new DocProperties();

    private final DocContentSupport support = new DocContentSupport(new ObjectMapper(), properties);

    @Test
    void requireWithinLimitShouldIgnoreNull() {
        support.requireWithinLimit(null);
    }

    @Test
    void requireWithinLimitShouldAcceptContentWithinBudget() {
        properties.setContentMaxBytes(16);
        support.requireWithinLimit("short");
    }

    @Test
    void requireWithinLimitShouldRejectOversizedContent() {
        properties.setContentMaxBytes(4);

        assertThatThrownBy(() -> support.requireWithinLimit("12345"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(40001);
    }

    @Test
    void extractTextShouldReturnEmptyForBlankOrInvalidInput() {
        assertThat(support.extractText(null)).isEmpty();
        assertThat(support.extractText("  ")).isEmpty();
        assertThat(support.extractText("{oops")).isEmpty();
    }

    @Test
    void extractTextShouldJoinTextNodesAndBlocks() {
        String json = "{\"type\":\"doc\",\"content\":["
                + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"hello \"},"
                + "{\"type\":\"text\",\"text\":\"world\"},{\"type\":\"hardBreak\"},"
                + "{\"type\":\"text\",\"text\":\"next\"}]},"
                + "{\"type\":\"heading\",\"content\":[{\"type\":\"text\",\"text\":\"title\"}]}]}";

        assertThat(support.extractText(json)).isEqualTo("hello world\nnext\n\ntitle");
    }

    @Test
    void extractTextShouldIgnoreScalarAndNullNodes() {
        String json = "{\"type\":\"doc\",\"content\":[1,null,\"raw\","
                + "{\"type\":\"paragraph\",\"content\":\"raw\"},"
                + "{\"type\":\"table\",\"content\":[{\"type\":\"text\",\"text\":\"cell\"}]}]}";

        assertThat(support.extractText(json)).isEqualTo("cell");
    }

    @Test
    void extractTextShouldNormalizeWhitespace() {
        String json = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"a\\tb\\n\\n\\nc\"}]}]}";

        assertThat(support.extractText(json)).isEqualTo("a b\n\nc");
    }

    @Test
    void extractTextShouldTruncateAtByteLimitWithoutBreakingCharacter() {
        properties.setContentTextMaxBytes(4);
        String json = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"ab中\"}]}]}";

        assertThat(support.extractText(json)).isEqualTo("ab");
    }

    @Test
    void extractTextShouldReturnEmptyWhenByteBudgetDisabled() {
        properties.setContentTextMaxBytes(0);
        String json = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"content\"}]}]}";

        assertThat(support.extractText(json)).isEmpty();
    }

    @Test
    void extractTextShouldKeepOriginalWhenWithinByteLimit() {
        properties.setContentTextMaxBytes(1024);
        String json = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":["
                + "{\"type\":\"text\",\"text\":\"normal\"}]}]}";

        assertThat(support.extractText(json)).isEqualTo("normal");
    }

    @Test
    void sha256ShouldReturnNullForNull() {
        assertThat(support.sha256(null)).isNull();
    }

    @Test
    void sha256ShouldMatchKnownDigest() {
        assertThat(support.sha256(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(support.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
