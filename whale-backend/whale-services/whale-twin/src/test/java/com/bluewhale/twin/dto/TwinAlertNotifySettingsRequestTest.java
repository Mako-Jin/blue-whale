package com.bluewhale.twin.dto;

import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通知设置请求校验单测：静默时段正则必须能编译，且覆盖空值与常见写法。
 *
 * <p>回归背景：正则写错（多一个右括号）时 {@code @Pattern} 在运行期初始化
 * {@code PatternValidator} 会抛 {@code HV000032}，整个 PUT 变成 500「系统异常」——
 * 编译期完全看不出来，只能靠这种断言兜住。</p>
 *
 * @author BlueWhale
 */
class TwinAlertNotifySettingsRequestTest {

    /** 需要校验正则的字段名 */
    private static final List<String> QUIET_FIELDS = List.of("quietStart", "quietEnd");

    @Test
    void quietTimePatternsAreCompilable() throws Exception {
        for (String name : QUIET_FIELDS) {
            Field field = TwinAlertNotifySettingsRequest.class.getDeclaredField(name);
            Pattern annotation = field.getAnnotation(Pattern.class);
            // 编译失败会抛 PatternSyntaxException
            java.util.regex.Pattern.compile(annotation.regexp());
        }
    }

    @Test
    void quietTimePatternAcceptsEmptyAndTwoForms() throws Exception {
        java.util.regex.Pattern pattern = compile("quietStart");

        assertTrue(pattern.matcher("").matches());
        assertTrue(pattern.matcher("22:00").matches());
        assertTrue(pattern.matcher("8:05").matches());
        assertTrue(pattern.matcher("00:00").matches());
        assertTrue(pattern.matcher("23:59").matches());
    }

    @Test
    void quietTimePatternRejectsIllegalValues() throws Exception {
        java.util.regex.Pattern pattern = compile("quietStart");

        assertFalse(pattern.matcher("24:00").matches());
        assertFalse(pattern.matcher("22:60").matches());
        assertFalse(pattern.matcher("2200").matches());
        assertFalse(pattern.matcher("22:0").matches());
    }

    @Test
    void brokenRegexWouldBeCaughtByCompile() {
        // 示意：多一个右括号就是这个坑（此前线上正是如此）
        assertThrows(PatternSyntaxException.class,
                () -> java.util.regex.Pattern.compile("^([01]?\\d|2[0-3]):[0-5]\\d)?$"));
    }

    /**
     * 取某个字段上的正则并编译。
     *
     * @param name 字段名
     * @return 编译后的正则
     * @throws Exception 反射失败
     */
    private java.util.regex.Pattern compile(String name) throws Exception {
        Field field = TwinAlertNotifySettingsRequest.class.getDeclaredField(name);
        return java.util.regex.Pattern.compile(field.getAnnotation(Pattern.class).regexp());
    }
}
