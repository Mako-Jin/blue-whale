package com.bluewhale.mall.catalog.domain.model;

import com.bluewhale.mall.common.MallErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品类目。
 *
 * <p>树最深三级：一级品类 → 二级品类 → 三级品类。层级不用递归 SQL 现算，
 * 由应用层按上级链回填后写死——层级要参与「最多三级」校验，放数据库里算
 * 等于把校验规则埋进 SQL，换方言就得重写。</p>
 *
 * @author BlueWhale
 */
@Data
public class Category {

    /** 名称最大长度 */
    public static final int NAME_MAX_LENGTH = 64;

    /** 根类目的上级 ID */
    public static final long ROOT_PARENT_ID = 0L;

    /** 最大层级 */
    public static final int MAX_LEVEL = 3;

    /** 主键 */
    private Long id;

    /** 上级类目 ID，0 为一级 */
    private Long parentId;

    /** 类目名称 */
    private String categoryName;

    /** 层级（1 起） */
    private Integer categoryLevel;

    /** 排序号 */
    private Integer sortNo;

    /** 状态 */
    private CategoryStatus status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验类目自身的一致性。
     *
     * <p>只管「这一条记录自己讲不讲得通」；「上级是否存在、是否成环、同级是否重名」
     * 需要查库，放在应用服务里做。</p>
     */
    public void validate() {
        if (categoryName == null || categoryName.isBlank()) {
            throw MallErrorCode.exception(MallErrorCode.CATEGORY_INVALID, "类目名称不能为空");
        }
        if (categoryName.trim().length() > NAME_MAX_LENGTH) {
            throw MallErrorCode.exception(MallErrorCode.CATEGORY_INVALID,
                    "类目名称不得超过 " + NAME_MAX_LENGTH + " 字符");
        }
        if (parentId == null) {
            parentId = ROOT_PARENT_ID;
        }
        if (id != null && id.equals(parentId)) {
            throw MallErrorCode.exception(MallErrorCode.CATEGORY_INVALID, "上级类目不能是自身");
        }
        if (categoryLevel == null || categoryLevel < 1 || categoryLevel > MAX_LEVEL) {
            throw MallErrorCode.exception(MallErrorCode.CATEGORY_INVALID,
                    "类目层级不合法，最多支持 " + MAX_LEVEL + " 级");
        }
        if (remark != null && remark.trim().length() > 255) {
            throw MallErrorCode.exception(MallErrorCode.CATEGORY_INVALID, "备注不得超过 255 字符");
        }
        categoryName = categoryName.trim();
    }

    /**
     * 是否为一级类目。
     *
     * @return 一级类目返回 true
     */
    public boolean isRoot() {
        return parentId == null || parentId == ROOT_PARENT_ID;
    }
}
