package com.bluewhale.form.support;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 表单数据批量导入工具（CSV 解析 / 模板生成 / 单元格转换），纯 JDK 实现。
 *
 * <p>解析遵循 RFC4180：逗号分隔、双引号包裹、{@code ""} 转义为 {@code "}、引号内允许逗号与换行；
 * 行分隔兼容 {@code \n} / {@code \r\n} / 单独 {@code \r}；完全空行（所有单元格均为空白）直接忽略。
 * 模板直接复用 {@link FormDataExportSupport#csv(List, List)}，与导出文件保持同一编码约定
 * （UTF-8 + BOM、CRLF 行分隔）。</p>
 *
 * @author BlueWhale
 */
public final class FormDataImportSupport {

    /** 单次导入最大行数（不含被忽略的空行） */
    private static final int MAX_ROWS = 5000;

    /** 单行最大列数 */
    private static final int MAX_COLUMNS = 256;

    /** 导入文件最大字节数（5MB） */
    private static final int MAX_LENGTH = 5 * 1024 * 1024;

    /** UTF-8 BOM */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** CSV 单元格分隔符 */
    private static final char CSV_SEPARATOR = ',';

    /** 布尔真值文本（不区分大小写） */
    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "是", "y", "yes");

    /** 布尔假值文本（不区分大小写） */
    private static final Set<String> FALSE_VALUES = Set.of("false", "0", "否", "n", "no");

    /** 支持批量导入的存储类型（附件/子表为 JSON 结构，需走专用接口） */
    private static final Set<String> IMPORTABLE_TYPES = Set.of(
            FormDataType.TEXT, FormDataType.TEXTAREA, FormDataType.SELECT,
            FormDataType.DATE, FormDataType.DATETIME, FormDataType.NUMBER, FormDataType.BOOL);

    /** 工具类不允许实例化 */
    private FormDataImportSupport() {
    }

    /**
     * 解析 CSV 文件为二维单元格列表（含表头行）
     *
     * <p>短行按最宽行补空串，长行不裁剪（列数与表头的匹配由调用方判断）。</p>
     *
     * @param content CSV 文件内容（可含 UTF-8 BOM）
     * @return 行列表，每行单元格均为非 {@code null} 字符串
     * @throws IllegalArgumentException 文件过大、行列超限或引号未闭合时抛出
     */
    public static List<List<String>> parseCsv(byte[] content) {
        if (content == null || content.length == 0) {
            return new ArrayList<>();
        }
        if (content.length > MAX_LENGTH) {
            throw new IllegalArgumentException("导入文件过大");
        }
        String text = new String(stripBom(content), StandardCharsets.UTF_8);
        CsvReader reader = new CsvReader();
        for (int index = 0; index < text.length(); index++) {
            index = reader.read(text, index);
        }
        return reader.result();
    }

    /**
     * 生成导入模板（UTF-8 + BOM 的表头 CSV，与导出文件编码一致）
     *
     * @param headers 表头
     * @return CSV 字节数组
     */
    public static byte[] template(List<String> headers) {
        return FormDataExportSupport.csv(headers, List.of());
    }

    /**
     * 按字段类型转换单元格文本
     *
     * <p>空白单元格返回 {@code null}，交由后续必填校验处理；{@code date}/{@code datetime}
     * 仅做去空白，格式合法性由服务层统一校验。</p>
     *
     * @param field 字段定义
     * @param cell 单元格文本（可空）
     * @return 转换后的值；空白单元格返回 {@code null}
     * @throws IllegalArgumentException 值非法或字段类型不支持批量导入时抛出
     */
    public static Object convert(FormField field, String cell) {
        if (cell == null || cell.isBlank()) {
            return null;
        }
        String type = field.getType();
        if (FormDataType.NUMBER.equals(type)) {
            return number(field, cell);
        }
        if (FormDataType.BOOL.equals(type)) {
            return bool(field, cell);
        }
        if (FormDataType.DATE.equals(type) || FormDataType.DATETIME.equals(type)) {
            return cell.trim();
        }
        if (FormDataType.TEXT.equals(type) || FormDataType.TEXTAREA.equals(type)
                || FormDataType.SELECT.equals(type)) {
            return cell;
        }
        throw new IllegalArgumentException("字段 " + label(field) + " 不支持批量导入");
    }

    /**
     * 判断存储类型是否支持批量导入
     *
     * @param type 存储类型
     * @return 支持导入返回 {@code true}
     */
    public static boolean importable(String type) {
        return type != null && IMPORTABLE_TYPES.contains(type);
    }

    /**
     * 去除 UTF-8 BOM
     *
     * @param content 原始字节
     * @return 去除 BOM 后的字节（无 BOM 时原样返回）
     */
    private static byte[] stripBom(byte[] content) {
        if (content.length < UTF8_BOM.length
                || content[0] != UTF8_BOM[0] || content[1] != UTF8_BOM[1] || content[2] != UTF8_BOM[2]) {
            return content;
        }
        byte[] result = new byte[content.length - UTF8_BOM.length];
        System.arraycopy(content, UTF8_BOM.length, result, 0, result.length);
        return result;
    }

    /**
     * 判断一行是否全为空白单元格
     *
     * @param row 行数据
     * @return 全部空白返回 {@code true}
     */
    private static boolean isBlankRow(List<String> row) {
        for (String value : row) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 转换数值单元格
     *
     * @param field 字段定义
     * @param cell 单元格文本
     * @return 数值
     * @throws IllegalArgumentException 非法数字时抛出
     */
    private static BigDecimal number(FormField field, String cell) {
        try {
            return new BigDecimal(cell.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("字段 " + label(field) + " 的值不是合法数字");
        }
    }

    /**
     * 转换布尔单元格
     *
     * @param field 字段定义
     * @param cell 单元格文本
     * @return 布尔值
     * @throws IllegalArgumentException 非真值/假值文本时抛出
     */
    private static Boolean bool(FormField field, String cell) {
        String text = cell.trim().toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(text)) {
            return Boolean.TRUE;
        }
        if (FALSE_VALUES.contains(text)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("字段 " + label(field) + " 的值不是合法布尔值");
    }

    /**
     * 取字段显示名（缺省回落字段 key）
     *
     * @param field 字段定义
     * @return 用于错误提示的字段名
     */
    private static String label(FormField field) {
        String label = field.getLabel();
        if (label != null && !label.isBlank()) {
            return label;
        }
        return field.getKey() == null ? "" : field.getKey();
    }

    /**
     * CSV 状态机读取器（逐字符推进，维护当前行、当前单元格与引号状态）。
     *
     * @author BlueWhale
     */
    private static final class CsvReader {

        /** 已解析行 */
        private final List<List<String>> rows = new ArrayList<>();

        /** 当前行 */
        private final List<String> row = new ArrayList<>();

        /** 当前单元格缓冲 */
        private final StringBuilder cell = new StringBuilder();

        /** 是否处于引号内 */
        private boolean quoted;

        /**
         * 读取下标处字符并返回下一次循环应开始的下标
         *
         * @param text 全文
         * @param index 当前下标
         * @return 下一次循环应使用的下标
         */
        private int read(String text, int index) {
            if (quoted) {
                return readQuoted(text, index);
            }
            return readPlain(text, index);
        }

        /**
         * 读取引号内的字符（{@code ""} 转义为单个引号，单独引号结束引号段）
         *
         * @param text 全文
         * @param index 当前下标
         * @return 下一次循环应使用的下标
         */
        private int readQuoted(String text, int index) {
            char ch = text.charAt(index);
            if (ch != '"') {
                cell.append(ch);
                return index;
            }
            if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                cell.append('"');
                return index + 1;
            }
            quoted = false;
            return index;
        }

        /**
         * 读取引号外的字符（分隔符、行结束符、引号段起始与普通字符）
         *
         * @param text 全文
         * @param index 当前下标
         * @return 下一次循环应使用的下标
         */
        private int readPlain(String text, int index) {
            char ch = text.charAt(index);
            if (ch == '"') {
                if (cell.length() == 0) {
                    quoted = true;
                } else {
                    cell.append(ch);
                }
                return index;
            }
            if (ch == CSV_SEPARATOR) {
                addCell();
                return index;
            }
            if (ch == '\n') {
                endRow();
                return index;
            }
            if (ch == '\r') {
                return endLine(text, index);
            }
            cell.append(ch);
            return index;
        }

        /**
         * 结束当前行并跳过 {@code \r\n} 中的 {@code \n}
         *
         * @param text 全文
         * @param index 当前下标（指向 {@code \r}）
         * @return 下一次循环应使用的下标
         */
        private int endLine(String text, int index) {
            endRow();
            if (index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                return index + 1;
            }
            return index;
        }

        /**
         * 追加当前单元格并校验列数上限
         *
         * @throws IllegalArgumentException 列数超过上限时抛出
         */
        private void addCell() {
            row.add(cell.toString());
            cell.setLength(0);
            if (row.size() > MAX_COLUMNS) {
                throw new IllegalArgumentException("导入文件列数超过上限 " + MAX_COLUMNS);
            }
        }

        /**
         * 结束当前行：空行忽略，否则收集并校验行数上限
         *
         * @throws IllegalArgumentException 行数超过上限时抛出
         */
        private void endRow() {
            addCell();
            if (isBlankRow(row)) {
                row.clear();
                return;
            }
            rows.add(new ArrayList<>(row));
            row.clear();
            if (rows.size() > MAX_ROWS) {
                throw new IllegalArgumentException("导入文件行数超过上限 " + MAX_ROWS);
            }
        }

        /**
         * 收尾：校验引号闭合、落最后一行并按最宽行补齐短行
         *
         * @return 解析结果
         * @throws IllegalArgumentException 引号未闭合时抛出
         */
        private List<List<String>> result() {
            if (quoted) {
                throw new IllegalArgumentException("CSV 存在未闭合的引号");
            }
            if (cell.length() > 0 || !row.isEmpty()) {
                endRow();
            }
            int width = 0;
            for (List<String> line : rows) {
                width = Math.max(width, line.size());
            }
            for (List<String> line : rows) {
                while (line.size() < width) {
                    line.add("");
                }
            }
            return rows;
        }
    }
}
