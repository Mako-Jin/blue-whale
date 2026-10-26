package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.Set;

/**
 * 列存储类型（{@code data_type}）白名单与 MySQL 列类型映射。
 *
 * <p><b>存储类型与控件类型解耦</b>：本类只负责「数据怎么存」（驱动物理表 DDL 生成），
 * 前端「怎么渲染」由 {@link FormWidgetType} 负责。同一 {@code select}（VARCHAR）列可按不同
 * 表单视图渲染为下拉/单选/多选。</p>
 *
 * @author BlueWhale
 */
public final class FormDataType {

    /** 存储类型：短文本 */
    public static final String TEXT = "text";

    /** 存储类型：长文本 */
    public static final String TEXTAREA = "textarea";

    /** 存储类型：数值 */
    public static final String NUMBER = "number";

    /** 存储类型：日期 */
    public static final String DATE = "date";

    /** 存储类型：日期时间 */
    public static final String DATETIME = "datetime";

    /** 存储类型：可选项（存选项值） */
    public static final String SELECT = "select";

    /** 存储类型：布尔 */
    public static final String BOOL = "bool";

    /** 存储类型：附件（文件 ID 数组，以 JSON 文本落库） */
    public static final String ATTACHMENT = "attachment";

    /** 存储类型：子表（行对象数组，以 JSON 文本落库） */
    public static final String SUBTABLE = "subtable";

    /** 存储类型白名单 */
    private static final Set<String> ALLOWED =
            Set.of(TEXT, TEXTAREA, NUMBER, DATE, DATETIME, SELECT, BOOL, ATTACHMENT, SUBTABLE);

    /** 支持条件检索的存储类型（附件/子表为 JSON 文本，不支持比较过滤） */
    private static final Set<String> FILTERABLE =
            Set.of(TEXT, TEXTAREA, NUMBER, DATE, DATETIME, SELECT, BOOL);

    /** 文本字段默认长度 */
    public static final int DEFAULT_TEXT_LENGTH = 255;

    /** 文本字段最小长度 */
    public static final int MIN_TEXT_LENGTH = 1;

    /** 文本字段最大长度 */
    public static final int MAX_TEXT_LENGTH = 2000;

    /** 数值字段默认精度（总位数） */
    public static final int DEFAULT_PRECISION = 18;

    /** 数值字段默认小数位 */
    public static final int DEFAULT_SCALE = 4;

    /** 数值字段最小精度 */
    private static final int MIN_PRECISION = 1;

    /** 数值字段最大精度 */
    private static final int MAX_PRECISION = 65;

    /** 数值字段最小小数位 */
    private static final int MIN_SCALE = 0;

    /** 数值字段最大小数位 */
    private static final int MAX_SCALE = 30;

    /** {@code select} 列固定长度 */
    private static final int SELECT_LENGTH = 255;

    /** 工具类不允许实例化 */
    private FormDataType() {
    }

    /**
     * 判断存储类型是否在白名单内
     *
     * @param dataType 存储类型
     * @return 在白名单内返回 {@code true}
     */
    public static boolean isAllowed(String dataType) {
        return dataType != null && ALLOWED.contains(dataType);
    }

    /**
     * 判断存储类型是否支持动态字段条件检索
     *
     * @param dataType 存储类型
     * @return 支持条件检索返回 {@code true}
     */
    public static boolean isFilterable(String dataType) {
        return dataType != null && FILTERABLE.contains(dataType);
    }

    /**
     * 断言存储类型合法
     *
     * @param dataType 存储类型
     * @param columnCode 列编码（用于错误提示）
     * @throws BizException 类型非法时抛出 {@code PARAM_ERROR}
     */
    public static void assertAllowed(String dataType, String columnCode) {
        if (!isAllowed(dataType)) {
            throw paramError("列 " + columnCode + " 的存储类型非法：" + dataType);
        }
    }

    /**
     * 映射存储类型到 MySQL 列类型
     *
     * @param dataType 存储类型
     * @param length 文本长度（{@code text} 生效，可空）
     * @param precision 精度（{@code number} 生效，可空）
     * @param scale 小数位（{@code number} 生效，可空）
     * @return MySQL 列类型文本
     * @throws BizException 存储类型或参数非法时抛出 {@code PARAM_ERROR}
     */
    public static String mysqlType(String dataType, Integer length, Integer precision, Integer scale) {
        assertAllowed(dataType, "");
        switch (dataType) {
            case TEXT:
                return textType(length);
            case TEXTAREA:
                return "TEXT";
            case NUMBER:
                return numberType(precision, scale);
            case DATE:
                return "DATE";
            case DATETIME:
                return "DATETIME";
            case SELECT:
                return "VARCHAR(" + SELECT_LENGTH + ")";
            case BOOL:
                return "TINYINT";
            case ATTACHMENT:
            case SUBTABLE:
                // 附件/子表值均为 JSON 数组，统一以 TEXT 承载
                return "TEXT";
            default:
                throw paramError("存储类型非法：" + dataType);
        }
    }

    /**
     * 映射文本存储类型到 {@code VARCHAR(n)}
     *
     * @param length 长度（可空，缺省 {@link #DEFAULT_TEXT_LENGTH}）
     * @return MySQL 列类型文本
     * @throws BizException 长度越界时抛出 {@code PARAM_ERROR}
     */
    private static String textType(Integer length) {
        int textLength = length == null ? DEFAULT_TEXT_LENGTH : length;
        if (textLength < MIN_TEXT_LENGTH || textLength > MAX_TEXT_LENGTH) {
            throw paramError("text 列长度需在 1..2000：" + textLength);
        }
        return "VARCHAR(" + textLength + ")";
    }

    /**
     * 映射数值存储类型到 {@code DECIMAL(p,s)}
     *
     * @param precision 精度（可空，缺省 {@link #DEFAULT_PRECISION}）
     * @param scale 小数位（可空，缺省 {@link #DEFAULT_SCALE}）
     * @return MySQL 列类型文本
     * @throws BizException 精度/小数位越界时抛出 {@code PARAM_ERROR}
     */
    private static String numberType(Integer precision, Integer scale) {
        int numberPrecision = precision == null ? DEFAULT_PRECISION : precision;
        int numberScale = scale == null ? DEFAULT_SCALE : scale;
        if (numberPrecision < MIN_PRECISION || numberPrecision > MAX_PRECISION
                || numberScale < MIN_SCALE || numberScale > MAX_SCALE
                || numberScale > numberPrecision) {
            throw paramError("number 列精度/小数位非法：" + numberPrecision + "," + numberScale);
        }
        return "DECIMAL(" + numberPrecision + "," + numberScale + ")";
    }

    /**
     * 存储类型缺省控件类型（{@code widget_type} 未配置时的回落映射）
     *
     * @param dataType 存储类型
     * @return 缺省控件类型
     */
    public static String defaultWidget(String dataType) {
        if (BOOL.equals(dataType)) {
            return FormWidgetType.SWITCH;
        }
        return dataType;
    }

    /**
     * 构造参数错误异常
     *
     * @param message 错误消息
     * @return 参数错误业务异常
     */
    private static BizException paramError(String message) {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
    }
}
