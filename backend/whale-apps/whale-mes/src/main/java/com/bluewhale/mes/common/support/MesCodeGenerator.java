package com.bluewhale.mes.common.support;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * MES 业务单号生成器。
 *
 * <p>格式：{@code <前缀>-yyyyMMddHHmmss-<6 位随机>}，长度不超过 64，满足租户内唯一索引要求。
 * 单号仅用于展示与幂等定位，不承载业务含义。</p>
 *
 * @author BlueWhale
 */
public final class MesCodeGenerator {

    /** 时间片段格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 工具类禁止实例化
     */
    private MesCodeGenerator() {
    }

    /**
     * 生成业务单号
     *
     * @param prefix 业务前缀，如 TASK / DSP / RPT
     * @return 业务单号
     */
    public static String nextNo(String prefix) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return prefix + "-" + LocalDateTime.now().format(TIME_FORMAT) + "-" + random;
    }
}
