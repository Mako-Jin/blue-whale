package com.bluewhale.archive.support;

import com.bluewhale.archive.remote.RemoteFormField;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表单附件递归提取器。
 *
 * <p>按字段定义遍历表单值：{@code attachment} 字段取文件ID（支持单值、数组、含 {@code id} 的对象），
 * {@code subtable} 字段逐行递归其子字段；结果按首次出现顺序去重，过滤非法值。</p>
 *
 * @author BlueWhale
 */
public final class AttachmentExtractor {

    /** 对象形态附件值中可能的ID键 */
    private static final String[] FILE_ID_KEYS = {"fileId", "id", "value"};

    /** 工具类不允许实例化 */
    private AttachmentExtractor() {
    }

    /**
     * 提取表单值中的全部附件文件ID。
     *
     * @param values 表单结构化字段值
     * @param fields 字段定义
     * @return 文件ID列表（去重、保持出现顺序）
     */
    public static List<Long> extract(Map<String, Object> values, List<RemoteFormField> fields) {
        Set<Long> ids = new LinkedHashSet<>();
        if (values == null || fields == null) {
            return new ArrayList<>(ids);
        }
        collect(values, fields, ids);
        return new ArrayList<>(ids);
    }

    /**
     * 递归收集某一层级字段的附件ID。
     *
     * @param values 当前层字段值
     * @param fields 当前层字段定义
     * @param ids 收集结果
     */
    private static void collect(Map<String, Object> values, List<RemoteFormField> fields, Set<Long> ids) {
        for (RemoteFormField field : fields) {
            if (field == null || field.getKey() == null) {
                continue;
            }
            Object value = values.get(field.getKey());
            if (value == null) {
                continue;
            }
            if (ArchiveConstants.FORM_TYPE_ATTACHMENT.equals(field.getType())) {
                collectFileIds(value, ids);
            } else if (ArchiveConstants.FORM_TYPE_SUBTABLE.equals(field.getType())) {
                collectRows(value, field.getChildren(), ids);
            }
        }
    }

    /**
     * 递归收集子表行中的附件ID。
     *
     * @param value 子表值（行集合）
     * @param children 子字段定义
     * @param ids 收集结果
     */
    private static void collectRows(Object value, List<RemoteFormField> children, Set<Long> ids) {
        if (!(value instanceof Collection<?> rows)) {
            return;
        }
        for (Object row : rows) {
            if (row instanceof Map<?, ?> rowMap) {
                collect(toKeyMap(rowMap), children, ids);
            }
        }
    }

    /**
     * 收集单个附件字段值中的文件ID。
     *
     * @param value 附件字段值（单值 / 集合 / 对象）
     * @param ids 收集结果
     */
    private static void collectFileIds(Object value, Set<Long> ids) {
        if (value instanceof Collection<?> items) {
            items.forEach(item -> collectFileIds(item, ids));
            return;
        }
        if (value instanceof Object[] items) {
            for (Object item : items) {
                collectFileIds(item, ids);
            }
            return;
        }
        Long id = toFileId(value);
        if (id != null) {
            ids.add(id);
        }
    }

    /**
     * 把附件值规整为文件ID：支持数字、数字字符串与含ID键的对象。
     *
     * @param value 附件值
     * @return 文件ID；无法识别时返回 {@code null}
     */
    private static Long toFileId(Object value) {
        if (value instanceof Number number) {
            long id = number.longValue();
            return id > 0 ? id : null;
        }
        if (value instanceof String text) {
            try {
                long id = Long.parseLong(text.trim());
                return id > 0 ? id : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : FILE_ID_KEYS) {
                Long id = toFileId(map.get(key));
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }

    /**
     * 将通配符键 Map 规整为字符串键 Map。
     *
     * @param source 原始 Map
     * @return 字符串键 Map
     */
    private static Map<String, Object> toKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
