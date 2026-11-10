package com.bluewhale.oa.directory.domain.model;

import lombok.Data;

import java.util.List;

/**
 * 组织架构树节点。
 *
 * @author BlueWhale
 */
@Data
public class DeptNode {

    /** 部门ID */
    private Long id;

    /** 父部门ID */
    private Long parentId;

    /** 部门名称 */
    private String name;

    /** 子部门 */
    private List<DeptNode> children;
}
