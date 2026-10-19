package com.bluewhale.form.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

import java.util.Set;

/**
 * 表单控件类型（{@code widget_type}）白名单。
 *
 * <p><b>控件类型与存储类型解耦</b>：同一 {@code select}（VARCHAR）列可按不同表单视图渲染为
 * {@code select}/{@code radio}/{@code checkbox}；{@code switch} 对应 {@code bool} 列。</p>
 *
 * @author BlueWhale
 */
public final class FormWidgetType {

    /** 控件：单行文本 */
    public static final String TEXT = "text";

    /** 控件：多行文本 */
    public static final String TEXTAREA = "textarea";

    /** 控件：数字输入 */
    public static final String NUMBER = "number";

    /** 控件：下拉框 */
    public static final String SELECT = "select";

    /** 控件：单选 */
    public static final String RADIO = "radio";

    /** 控件：多选 */
    public static final String CHECKBOX = "checkbox";

    /** 控件：日期选择 */
    public static final String DATE = "date";

    /** 控件：日期时间选择 */
    public static final String DATETIME = "datetime";

    /** 控件：开关 */
    public static final String SWITCH = "switch";

    /** 控件类型白名单 */
    private static final Set<String> ALLOWED =
            Set.of(TEXT, TEXTAREA, NUMBER, SELECT, RADIO, CHECKBOX, DATE, DATETIME, SWITCH);

    /** 工具类不允许实例化 */
    private FormWidgetType() {
    }

    /**
     * 判断控件类型是否在白名单内
     *
     * @param widgetType 控件类型
     * @return 在白名单内返回 {@code true}
     */
    public static boolean isAllowed(String widgetType) {
        return widgetType != null && ALLOWED.contains(widgetType);
    }

    /**
     * 断言控件类型合法（{@code null} 视为未配置，按存储类型回落，不校验）
     *
     * @param widgetType 控件类型
     * @param fieldLabel 字段显示名（用于错误提示）
     * @throws BizException 控件类型非法时抛出 {@code PARAM_ERROR}
     */
    public static void assertAllowed(String widgetType, String fieldLabel) {
        if (widgetType != null && !ALLOWED.contains(widgetType)) {
            throw paramError("字段 " + fieldLabel + " 的控件类型非法：" + widgetType);
        }
    }

    /**
     * 解析最终控件类型：已配置且合法则原样返回，未配置则按存储类型回落
     *
     * @param widgetType 控件类型（可空）
     * @param dataType 存储类型
     * @return 最终控件类型
     * @throws BizException 控件类型非法时抛出 {@code PARAM_ERROR}
     */
    public static String resolve(String widgetType, String dataType) {
        if (widgetType == null) {
            return FormDataType.defaultWidget(dataType);
        }
        assertAllowed(widgetType, "");
        return widgetType;
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
