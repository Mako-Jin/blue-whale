package com.bluewhale.pdm.process.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 工艺路线下发内容校验和。
 *
 * <p>按 DTO 字段顺序序列化后取 SHA-256；同一内容重复计算稳定，
 * 用于 MES 侧幂等比对。</p>
 *
 * @author BlueWhale
 */
public final class PdmPrcChecksum {

    /** JSON 序列化器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 工具类禁止实例化
     */
    private PdmPrcChecksum() {
    }

    /**
     * 计算内容校验和。
     *
     * @param payload 下发内容
     * @return 十六进制 SHA-256
     */
    public static String of(Object payload) {
        try {
            byte[] json = OBJECT_MAPPER.writeValueAsBytes(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "计算下发校验和失败");
        }
    }
}
