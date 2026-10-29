package com.bluewhale.doc.support.collab;

import com.bluewhale.doc.domain.exception.DocErrorCode;

import java.util.Base64;

/**
 * 协作载荷校验工具（M2）。
 *
 * @author BlueWhale
 */
public final class DocCollabPayloads {

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private DocCollabPayloads() {
    }

    /**
     * 解码 base64 载荷并校验体积。
     *
     * @param base64   载荷文本
     * @param maxBytes 解码后最大字节数
     * @return 解码后的字节
     * @throws com.bluewhale.common.core.exception.BizException 载荷为空、非法或超限
     */
    public static byte[] decode(String base64, long maxBytes) {
        if (base64 == null || base64.isBlank()) {
            throw DocErrorCode.exception(DocErrorCode.COLLAB_FRAME_INVALID);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length > maxBytes) {
                throw DocErrorCode.exception(DocErrorCode.COLLAB_FRAME_INVALID);
            }
            return bytes;
        } catch (IllegalArgumentException ex) {
            throw DocErrorCode.exception(DocErrorCode.COLLAB_FRAME_INVALID);
        }
    }
}
