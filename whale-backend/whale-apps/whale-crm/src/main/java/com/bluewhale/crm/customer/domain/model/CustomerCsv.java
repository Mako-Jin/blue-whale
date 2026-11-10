package com.bluewhale.crm.customer.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.common.CrmMasking;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 客户 CSV 解析与序列化（M1-9，纯 Java 实现，便于单测）。
 *
 * <p>解析遵循 RFC4180-lite：支持双引号包裹、引号内逗号与换行、双引号转义
 * （{@code ""}）以及 CRLF / LF 行尾，并自动剔除 UTF-8 BOM。表头支持中英文别名，
 * 「客户名称」为必填列。导出按固定列顺序输出，手机 / 邮箱 / 税号按调用方授权脱敏。</p>
 *
 * @author BlueWhale
 */
public final class CustomerCsv {

    /** 导出列顺序 */
    public static final List<String> EXPORT_HEADERS = List.of(
            "客户编码", "客户名称", "简称", "行业", "来源", "等级", "省", "市", "区",
            "税号", "官网", "标签", "负责人", "手机", "邮箱", "创建时间");

    /** 表头别名 -> 字段名 */
    private static final Map<String, String> ALIASES = aliases();

    /** 手机号格式 */
    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";

    /** 邮箱格式 */
    private static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    /** 引号 */
    private static final char QUOTE = '"';

    /** BOM 字符 */
    private static final char BOM = '\uFEFF';

    /** 字段名 -> 数据行赋值器 */
    private static final Map<String, BiConsumer<Row, String>> FILLERS = new HashMap<>();

    static {
        FILLERS.put("name", Row::setName);
        FILLERS.put("shortName", Row::setShortName);
        FILLERS.put("industry", Row::setIndustry);
        FILLERS.put("source", Row::setSource);
        FILLERS.put("level", Row::setLevel);
        FILLERS.put("website", Row::setWebsite);
        FILLERS.put("taxNo", Row::setTaxNo);
        FILLERS.put("province", Row::setProvince);
        FILLERS.put("city", Row::setCity);
        FILLERS.put("district", Row::setDistrict);
        FILLERS.put("address", Row::setAddress);
        FILLERS.put("ownerId", (row, value) -> row.setOwnerId(parseLong(value)));
        FILLERS.put("tags", Row::setTags);
        FILLERS.put("phone", Row::setPhone);
        FILLERS.put("email", Row::setEmail);
        FILLERS.put("customerCode", Row::setCustomerCode);
        FILLERS.put("createTime", Row::setCreateTime);
    }

    /**
     * 私有构造器：工具类，禁止实例化。
     */
    private CustomerCsv() {
    }

    /**
     * 解析 CSV 内容。
     *
     * @param content CSV 文本
     * @return 数据行（不含表头）
     */
    public static List<Row> parse(String content) {
        if (content == null || content.isBlank()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_IMPORT_EMPTY);
        }
        String text = content.charAt(0) == BOM ? content.substring(1) : content;
        List<List<String>> records = splitRecords(text);
        if (records.isEmpty()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_IMPORT_EMPTY);
        }
        List<String> header = records.get(0);
        Map<Integer, String> mapping = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String field = ALIASES.get(normalizeHeader(header.get(i)));
            if (field != null) {
                mapping.put(i, field);
            }
        }
        if (!mapping.containsValue("name")) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_IMPORT_INVALID, "缺少必填列「客户名称」");
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            if (isBlankRecord(record)) {
                continue;
            }
            Row row = new Row();
            row.setLineNumber(i + 1);
            for (Map.Entry<Integer, String> entry : mapping.entrySet()) {
                if (entry.getKey() < record.size()) {
                    fill(row, entry.getValue(), trim(record.get(entry.getKey())));
                }
            }
            rows.add(row);
        }
        if (rows.isEmpty()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_IMPORT_EMPTY);
        }
        return rows;
    }

    /**
     * 校验单行字段（必填、等级、手机与邮箱格式）。
     *
     * @param row 数据行
     * @return 错误原因列表；为空表示通过
     */
    public static List<String> validate(Row row) {
        List<String> errors = new ArrayList<>();
        if (row == null || row.getName() == null || row.getName().isBlank()) {
            errors.add("客户名称不能为空");
            return errors;
        }
        if (row.getLevel() != null && !row.getLevel().isBlank()) {
            String level = row.getLevel().trim().toUpperCase(Locale.ROOT);
            if (!"A".equals(level) && !"B".equals(level) && !"C".equals(level) && !"D".equals(level)) {
                errors.add("客户等级非法：" + row.getLevel());
            }
        }
        String phone = CustomerNormalizer.normalizePhone(row.getPhone());
        if (phone != null && !phone.matches(PHONE_PATTERN)) {
            errors.add("手机号格式非法：" + row.getPhone());
        }
        if (row.getEmail() != null && !row.getEmail().isBlank()
                && !row.getEmail().trim().matches(EMAIL_PATTERN)) {
            errors.add("邮箱格式非法：" + row.getEmail());
        }
        return errors;
    }

    /**
     * 序列化为 CSV 文本（含表头）。
     *
     * @param rows 数据行
     * @param allowPlain 是否允许输出敏感明文
     * @return CSV 文本
     */
    public static String export(List<Row> rows, boolean allowPlain) {
        StringBuilder builder = new StringBuilder();
        appendRecord(builder, EXPORT_HEADERS);
        for (Row row : rows) {
            appendRecord(builder, List.of(
                    value(row.getCustomerCode()),
                    value(row.getName()),
                    value(row.getShortName()),
                    value(row.getIndustry()),
                    value(row.getSource()),
                    value(row.getLevel()),
                    value(row.getProvince()),
                    value(row.getCity()),
                    value(row.getDistrict()),
                    value(CrmMasking.taxNo(row.getTaxNo(), allowPlain)),
                    value(row.getWebsite()),
                    value(row.getTags()),
                    value(row.getOwnerId() == null ? "" : row.getOwnerId().toString()),
                    value(CrmMasking.phone(row.getPhone(), allowPlain)),
                    value(CrmMasking.email(row.getEmail(), allowPlain)),
                    value(row.getCreateTime())));
        }
        return builder.toString();
    }

    /**
     * 按 CSV 规则切分记录。
     *
     * @param text CSV 文本
     * @return 记录列表
     */
    private static List<List<String>> splitRecords(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == QUOTE) {
                i = consumeQuoted(text, i + 1, field) - 1;
            } else if (current == ',') {
                record.add(field.toString());
                field.setLength(0);
            } else if (current == '\r' || current == '\n') {
                if (current == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                record.add(field.toString());
                field.setLength(0);
                records.add(record);
                record = new ArrayList<>();
            } else {
                field.append(current);
            }
        }
        if (field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }

    /**
     * 读取一段双引号包裹的字段内容。
     *
     * @param text CSV 文本
     * @param index 引号后的起始下标
     * @param field 字段缓冲
     * @return 引号闭合后的下一个下标
     */
    private static int consumeQuoted(String text, int index, StringBuilder field) {
        int i = index;
        while (i < text.length()) {
            char current = text.charAt(i);
            if (current != QUOTE) {
                field.append(current);
                i++;
                continue;
            }
            if (i + 1 < text.length() && text.charAt(i + 1) == QUOTE) {
                field.append(QUOTE);
                i += 2;
                continue;
            }
            return i + 1;
        }
        throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_IMPORT_INVALID, "CSV 引号未闭合");
    }

    /**
     * 写入一行 CSV。
     *
     * @param builder 构建器
     * @param values 字段值
     */
    private static void appendRecord(StringBuilder builder, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escape(values.get(i)));
        }
        builder.append('\n');
    }

    /**
     * CSV 字段转义。
     *
     * @param value 原始值
     * @return 转义后的值
     */
    private static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') >= 0 || text.indexOf(QUOTE) >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return QUOTE + text.replace("\"", "\"\"") + QUOTE;
        }
        return text;
    }

    /**
     * 空值转空串。
     *
     * @param value 原始值
     * @return 展示值
     */
    private static String value(String value) {
        return value == null ? "" : value;
    }

    /**
     * 判断记录是否全部为空。
     *
     * @param record 记录
     * @return 全空返回 true
     */
    private static boolean isBlankRecord(List<String> record) {
        for (String value : record) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 表头归一化。
     *
     * @param header 原始表头
     * @return 归一化表头
     */
    private static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        String text = header.trim();
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return text.toLowerCase(Locale.ROOT);
    }

    /**
     * 字段赋值。
     *
     * @param row 数据行
     * @param field 字段名
     * @param value 字段值
     */
    private static void fill(Row row, String field, String value) {
        BiConsumer<Row, String> filler = FILLERS.get(field);
        if (filler != null) {
            filler.accept(row, value);
        }
    }

    /**
     * 解析负责人 ID。
     *
     * @param value 文本
     * @return 负责人 ID；非法返回 null
     */
    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 去空白。
     *
     * @param value 文本
     * @return 去空白文本；空串返回 null
     */
    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 构建表头别名映射。
     *
     * @return 别名映射
     */
    private static Map<String, String> aliases() {
        Map<String, String> aliases = new HashMap<>();
        register(aliases, "name", "客户名称", "name");
        register(aliases, "shortName", "简称", "shortname");
        register(aliases, "industry", "行业", "industry");
        register(aliases, "source", "来源", "source");
        register(aliases, "level", "等级", "level");
        register(aliases, "website", "官网", "website");
        register(aliases, "taxNo", "税号", "taxno");
        register(aliases, "province", "省", "province");
        register(aliases, "city", "市", "city");
        register(aliases, "district", "区", "district");
        register(aliases, "address", "地址", "address");
        register(aliases, "ownerId", "负责人id", "ownerid");
        register(aliases, "tags", "标签", "tags");
        register(aliases, "phone", "手机", "phone");
        register(aliases, "email", "邮箱", "email");
        register(aliases, "customerCode", "客户编码", "customercode");
        register(aliases, "createTime", "创建时间", "createtime");
        return aliases;
    }

    /**
     * 注册别名。
     *
     * @param aliases 映射
     * @param field 字段名
     * @param codes 别名
     */
    private static void register(Map<String, String> aliases, String field, String... codes) {
        for (String code : codes) {
            aliases.put(code, field);
        }
    }

    /**
     * CSV 数据行。
     */
    @Data
    public static class Row {

        /** 行号（含表头，从 1 开始） */
        private Integer lineNumber;

        /** 客户编码（导出用） */
        private String customerCode;

        /** 客户名称 */
        private String name;

        /** 简称 */
        private String shortName;

        /** 行业 */
        private String industry;

        /** 来源 */
        private String source;

        /** 等级 */
        private String level;

        /** 官网 */
        private String website;

        /** 税号 */
        private String taxNo;

        /** 省 */
        private String province;

        /** 市 */
        private String city;

        /** 区县 */
        private String district;

        /** 地址 */
        private String address;

        /** 负责人 */
        private Long ownerId;

        /** 标签 */
        private String tags;

        /** 手机 */
        private String phone;

        /** 邮箱 */
        private String email;

        /** 创建时间（导出展示文本） */
        private String createTime;
    }
}
