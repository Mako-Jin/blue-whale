package com.bluewhale.archive.support;

import com.bluewhale.archive.entity.ArchiveCategory;
import com.bluewhale.archive.mapper.ArchiveCategoryMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 门类路径解析器：沿 {@code parentId} 上溯拼接祖先编码。
 *
 * <p>路径形如 {@code DOC/FIN}，用于条目展示与冗余存储；上溯深度受
 * {@link ArchiveConstants#MAX_TREE_DEPTH} 限制，脏数据成环时截断而非无限递归。</p>
 *
 * @author BlueWhale
 */
public final class CategoryPathResolver {

    /** 门类路径分隔符 */
    public static final String SEPARATOR = "/";

    /** 工具类不允许实例化 */
    private CategoryPathResolver() {
    }

    /**
     * 解析门类路径。
     *
     * @param categoryMapper 门类 Mapper，用于逐级查询父节点
     * @param category 目标门类，可为 {@code null}
     * @return 祖先编码以 {@code /} 连接的路径；门类为 {@code null} 时返回空串
     */
    public static String resolve(ArchiveCategoryMapper categoryMapper, ArchiveCategory category) {
        if (category == null) {
            return "";
        }
        List<String> codes = new ArrayList<>();
        ArchiveCategory current = category;
        int depth = 0;
        while (current != null && depth < ArchiveConstants.MAX_TREE_DEPTH) {
            codes.add(0, current.getCode());
            if (current.getParentId() == null || current.getParentId() == ArchiveConstants.ROOT_PARENT_ID) {
                break;
            }
            current = categoryMapper.selectById(current.getParentId());
            depth++;
        }
        return String.join(SEPARATOR, codes);
    }
}
