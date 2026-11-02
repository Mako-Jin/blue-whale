package com.bluewhale.doc.support.collab;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DocCollabPayloads} base64 解码与体积校验断言。
 *
 * @author BlueWhale
 */
class DocCollabPayloadsTest {

    @Test
    void decodeShouldRejectNullOrBlank() {
        assertThatThrownBy(() -> DocCollabPayloads.decode(null, 10)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> DocCollabPayloads.decode("  ", 10)).isInstanceOf(BizException.class);
    }

    @Test
    void decodeShouldRejectInvalidBase64() {
        assertThatThrownBy(() -> DocCollabPayloads.decode("not-base64!", 10))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(40009);
    }

    @Test
    void decodeShouldRejectOversizedPayload() {
        String encoded = Base64.getEncoder().encodeToString(new byte[20]);

        assertThatThrownBy(() -> DocCollabPayloads.decode(encoded, 10))
                .isInstanceOf(BizException.class);
    }

    @Test
    void decodeShouldReturnBytesWithinLimit() {
        String encoded = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});

        assertThat(DocCollabPayloads.decode(encoded, 10)).containsExactly(1, 2, 3);
    }
}
