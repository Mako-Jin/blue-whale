package com.bluewhale.archive.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 档案门类树节点视图。
 *
 * <p>ID 以字符串承载，避免雪花ID在浏览器端超过 IEEE 754 安全整数范围。</p>
 *
 * @author BlueWhale
 */
@Data
public class ArchiveCategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 门类ID */
    private String id;

    /** 门类编码 */
    private String code;

    /** 门类名称 */
    private String name;

    /** 上级门类ID（根节点为 "0"） */
    private String parentId;

    /** 层级（根节点为 1） */
    private Integer level;

    /** 默认保管期限（年；空表示长期） */
    private Integer retentionYears;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    private Integer securityLevel;

    /** 排序号 */
    private Integer sort;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 子节点 */
    private List<ArchiveCategoryVO> children = new ArrayList<>();
}
