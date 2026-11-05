package com.bluewhale.pdm.change.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

/**
 * 变更影响对象类型。
 *
 * @author BlueWhale
 */
public final class PdmChgObjectType {

    /** 文档 */
    public static final String DOCUMENT = "DOCUMENT";

    /** BOM */
    public static final String BOM = "BOM";

    /** 零件 */
    public static final String PART = "PART";

    /**
     * 工具类禁止实例化
     */
    private PdmChgObjectType() {
    }

    /**
     * 校验类型合法性。
     *
     * @param objectType 对象类型
     * @return 规范化类型
     */
    public static String requireValid(String objectType) {
        String normalized = objectType == null ? "" : objectType.trim().toUpperCase();
        if (!DOCUMENT.equals(normalized) && !BOM.equals(normalized) && !PART.equals(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "影响对象类型仅支持 DOCUMENT/BOM/PART");
        }
        return normalized;
    }
}
