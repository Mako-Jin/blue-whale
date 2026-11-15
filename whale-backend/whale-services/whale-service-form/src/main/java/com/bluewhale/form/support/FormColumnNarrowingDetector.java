package com.bluewhale.form.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态列类型「收窄」识别器（发布预检）。
 *
 * <p>ALTER 即时执行且无回滚：把 {@code VARCHAR(255)} 改成 {@code VARCHAR(100)}、把
 * {@code DECIMAL(18,4)} 精度调小、把 {@code DATETIME} 改成 {@code DATE} 都会让存量数据被
 * 静默截断。识别出收窄并让调用方要求二次确认，是发布路径上成本最低的一道护栏。</p>
 *
 * <p>入参类型文本均先经 {@link FormTableDdlGenerator#normalizeType(String)} 归一化
 * （小写、去空格、去反引号）。识别器只处理本模块实际会生成的类型（VARCHAR/TEXT/DECIMAL/
 * DATE/DATETIME/TINYINT），其它类型不判定，交由数据库自身校验。</p>
 *
 * @author BlueWhale
 */
public final class FormColumnNarrowingDetector {

    /** {@code varchar(n)} */
    private static final Pattern VARCHAR = Pattern.compile("^varchar\\((\\d+)\\)$");

    /** {@code decimal(p,s)} */
    private static final Pattern DECIMAL = Pattern.compile("^decimal\\((\\d+),(\\d+)\\)$");

    /** 文本最大长度类型标识 */
    private static final String TYPE_TEXT = "text";

    /** 日期类型标识 */
    private static final String TYPE_DATE = "date";

    /** 日期时间类型标识 */
    private static final String TYPE_DATETIME = "datetime";

    /** 工具类不允许实例化 */
    private FormColumnNarrowingDetector() {
    }

    /**
     * 判定列类型变更是否构成收窄
     *
     * @param currentType 物理表现有类型（如 {@code varchar(255)}）
     * @param wantedType 目标类型（如 {@code varchar(100)}）
     * @return 收窄原因描述；不构成收窄时返回 {@code null}
     */
    public static String detect(String currentType, String wantedType) {
        String current = FormTableDdlGenerator.normalizeType(currentType);
        String wanted = FormTableDdlGenerator.normalizeType(wantedType);
        if (current.isEmpty() || wanted.isEmpty() || current.equals(wanted)) {
            return null;
        }
        String textReason = detectTextNarrowing(current, wanted);
        if (textReason != null) {
            return textReason;
        }
        String decimalReason = detectDecimalNarrowing(current, wanted);
        if (decimalReason != null) {
            return decimalReason;
        }
        if (TYPE_DATETIME.equals(current) && TYPE_DATE.equals(wanted)) {
            return "DATETIME → DATE 会丢失时间部分";
        }
        return null;
    }

    /**
     * 判定文本类收窄：{@code TEXT → VARCHAR(n)}、{@code VARCHAR(m) → VARCHAR(n)} 且 n &lt; m
     *
     * @param current 现有类型
     * @param wanted 目标类型
     * @return 收窄原因；不构成收窄返回 {@code null}
     */
    private static String detectTextNarrowing(String current, String wanted) {
        Matcher wantedMatcher = VARCHAR.matcher(wanted);
        if (!wantedMatcher.matches()) {
            return null;
        }
        int wantedLength = Integer.parseInt(wantedMatcher.group(1));
        if (TYPE_TEXT.equals(current)) {
            return "TEXT → VARCHAR(" + wantedLength + ") 可能截断长文本";
        }
        Matcher currentMatcher = VARCHAR.matcher(current);
        if (currentMatcher.matches()) {
            int currentLength = Integer.parseInt(currentMatcher.group(1));
            if (wantedLength < currentLength) {
                return "VARCHAR(" + currentLength + ") → VARCHAR(" + wantedLength + ") 长度收窄";
            }
        }
        return null;
    }

    /**
     * 判定数值类收窄：{@code DECIMAL(p,s)} 的精度或小数位变小
     *
     * @param current 现有类型
     * @param wanted 目标类型
     * @return 收窄原因；不构成收窄返回 {@code null}
     */
    private static String detectDecimalNarrowing(String current, String wanted) {
        Matcher currentMatcher = DECIMAL.matcher(current);
        Matcher wantedMatcher = DECIMAL.matcher(wanted);
        if (!currentMatcher.matches() || !wantedMatcher.matches()) {
            return null;
        }
        int currentPrecision = Integer.parseInt(currentMatcher.group(1));
        int currentScale = Integer.parseInt(currentMatcher.group(2));
        int wantedPrecision = Integer.parseInt(wantedMatcher.group(1));
        int wantedScale = Integer.parseInt(wantedMatcher.group(2));
        if (wantedPrecision < currentPrecision || wantedScale < currentScale) {
            return "DECIMAL(" + currentPrecision + "," + currentScale + ") → DECIMAL("
                    + wantedPrecision + "," + wantedScale + ") 精度或小数位收窄";
        }
        return null;
    }
}
