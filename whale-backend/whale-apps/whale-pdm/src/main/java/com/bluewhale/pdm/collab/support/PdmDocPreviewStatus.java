package com.bluewhale.pdm.collab.support;

/**
 * 在线预览类型与状态常量
 *
 * @author BlueWhale
 */
public final class PdmDocPreviewStatus {

    /** 2D 预览 */
    public static final String TYPE_2D = "2D";

    /** 3D 预览 */
    public static final String TYPE_3D = "3D";

    /** 等待处理 */
    public static final String PENDING = "PENDING";

    /** 转换中 */
    public static final String RUNNING = "RUNNING";

    /** 成功 */
    public static final String SUCCESS = "SUCCESS";

    /** 失败 */
    public static final String FAILED = "FAILED";

    /** 暂不支持（需外部转换服务或仅下载） */
    public static final String UNSUPPORTED = "UNSUPPORTED";

    /** 原生格式直接预览 */
    public static final String FORMAT_ORIGINAL = "ORIGINAL";

    /**
     * 工具类禁止实例化
     */
    private PdmDocPreviewStatus() {
    }

    /**
     * 校验预览类型
     *
     * @param type 类型
     * @return true 表示合法
     */
    public static boolean isValidType(String type) {
        return TYPE_2D.equals(type) || TYPE_3D.equals(type);
    }
}
