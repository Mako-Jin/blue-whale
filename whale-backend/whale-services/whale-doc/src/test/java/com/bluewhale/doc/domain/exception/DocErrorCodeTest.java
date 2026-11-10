package com.bluewhale.doc.domain.exception;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DocErrorCode} 文案与异常构造断言。
 *
 * @author BlueWhale
 */
class DocErrorCodeTest {

    @Test
    void messageOfShouldReturnMappedMessage() {
        assertThat(DocErrorCode.messageOf(DocErrorCode.SPACE_ACCESS_DENIED)).isEqualTo("无权访问该文档空间");
        assertThat(DocErrorCode.messageOf(DocErrorCode.PAGE_NOT_FOUND)).isEqualTo("页面不存在或无权访问");
        assertThat(DocErrorCode.messageOf(DocErrorCode.CONTENT_TOO_LARGE)).isEqualTo("页面正文超过体积上限");
        assertThat(DocErrorCode.messageOf(DocErrorCode.REVISION_CONFLICT))
                .isEqualTo("页面已被他人更新，请刷新后重试");
        assertThat(DocErrorCode.messageOf(DocErrorCode.EXTERNAL_SERVICE_UNAVAILABLE))
                .isEqualTo("外部服务暂不可用，请稍后重试");
    }

    @Test
    void messageOfShouldFallbackForUnknownCode() {
        assertThat(DocErrorCode.messageOf(99999)).isEqualTo("文档操作失败");
    }

    @Test
    void exceptionShouldCarryCodeAndMessage() {
        BizException ex = DocErrorCode.exception(DocErrorCode.IMPORT_FILE_INVALID);

        assertThat(ex.getCode()).isEqualTo(DocErrorCode.IMPORT_FILE_INVALID);
        assertThat(ex.getMessage()).isEqualTo("导入文件为空、格式不支持或体积超限");
    }

    @Test
    void exceptionShouldUseGenericMessageForUnknownCode() {
        BizException ex = DocErrorCode.exception(-1);

        assertThat(ex.getCode()).isEqualTo(-1);
        assertThat(ex.getMessage()).isEqualTo("文档操作失败");
    }
}
