package com.bluewhale.party.branch.application.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 支部树节点。
 *
 * <p>在 {@link BranchView} 之上加 {@code children} 与两个计数：树接口一次取全量支部在内存里装配，
 * 避免前端按层拉接口拼树（层数越多请求越多，且拼出来的树与后端权限过滤结果容易不一致）。</p>
 *
 * @author BlueWhale
 */
@Data
public class BranchTreeView {

    /** 支部 ID */
    private Long id;

    /** 支部编码 */
    private String branchCode;

    /** 支部名称 */
    private String branchName;

    /** 上级支部 ID */
    private Long parentId;

    /** 支部层级 */
    private Integer branchLevel;

    /** 关联 ERP 组织编码 */
    private String orgCode;

    /** 支部书记姓名 */
    private String secretaryName;

    /** 状态码 */
    private String status;

    /** 状态标签 */
    private String statusLabel;

    /** 排序号 */
    private Integer sortNo;

    /** 下级支部 */
    private List<BranchTreeView> children = new ArrayList<>();
}
