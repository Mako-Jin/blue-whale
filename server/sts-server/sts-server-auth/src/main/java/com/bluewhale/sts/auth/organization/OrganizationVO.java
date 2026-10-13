package com.bluewhale.sts.auth.organization;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 组织视图。
 *
 * <p><b>三列派生字段都返回出来</b>（{@code orgLevel} / {@code codePath} / {@code fullName}），
 * 尽管它们不接受入参。理由是它们**解释了数据范围**：一个管理员看到
 * {@code DEPT_AND_CHILD} 生效范围不对时，第一个要看的正是"这个节点在树的哪一层、
 * 路径是什么"——把它们藏起来，排查就只能靠数据库。
 * 与 {@code UserGroupVO} 刻意不返回 {@code filterJson} 不矛盾：那一个返回了会让人
 * 以为它有用，这三个返回出来本身就说明"层级是算出来的"。
 *
 * <p>{@code hasChild} 由服务端维护（建 / 移 / 删都会重算），它让树接口之外的列表
 * 也能直接决定"要不要画展开箭头"，不必为每一行再查一次下级。
 */
@Getter
@Setter
@Schema(description = "组织")
public class OrganizationVO {

    @Schema(description = "组织主键")
    private Long id;

    @Schema(description = "所属租户主键")
    private Long tenantId;

    @Schema(description = "组织编码，租户内唯一")
    private String orgCode;

    @Schema(description = "组织名称")
    private String orgName;

    @Schema(description = "组织全称，按层级派生（如 蓝鲸集团/研发部/前端组）。改名或移动时整棵子树一起重写")
    private String fullName;

    @Schema(description = "父组织主键，顶级为 null")
    private Long parentId;

    @Schema(description = "层级，根为 1。由服务端派生，与 parentId 始终一致")
    private Integer orgLevel;

    @Schema(description = "物化路径，形如 /1/5/，为**各级祖先的主键**加斜杠（不含自身）。"
            + "DEPT_AND_CHILD 档位正是按它做前缀匹配求子树")
    private String codePath;

    @Schema(description = "是否有下级：0 否 1 是")
    private Integer hasChild;

    @Schema(description = "组织类型，只作展示、不参与判定")
    private String orgType;

    @Schema(description = "负责人用户主键")
    private Long managerId;

    @Schema(description = "负责人姓名。**冗余列**，写入时与 managerId 一起确定，便于列表展示")
    private String managerName;

    @Schema(description = "排序号")
    private Integer sortNo;

    @Schema(description = "状态：ENABLED / DISABLED。**停用的组织不再能作为 DEPT 档位的范围目标**")
    private String status;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "联系邮箱")
    private String contactEmail;

    @Schema(description = "创建时间（毫秒时间戳）")
    private Long createTime;

    @Schema(description = "最后更新时间（毫秒时间戳）")
    private Long updateTime;

}
