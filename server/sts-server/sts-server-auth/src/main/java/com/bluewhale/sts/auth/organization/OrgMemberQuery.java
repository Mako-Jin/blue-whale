package com.bluewhale.sts.auth.organization;

import com.bluewhale.sts.common.basic.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 组织成员的分页查询条件。
 *
 * <p>与 {@code UserGroupMemberQuery} 同构，取舍也相同：**分页、但不做按名搜索**。
 * 一个部门的人没有上限，一次取完就是一次全表扫描加全量序列化，
 * 接口会以"正常返回 200"的方式打垮自己；而按名字过滤要先跨表解析出姓名再过滤，
 * 那样 {@code total} 就不再是数据库给的那个数，等于把分页的收益还回去。
 * 按名字找一个人应当从**用户**列表那一侧进入。
 *
 * <p>{@code primaryOnly} 是这里唯一多出来的条件，因为主岗不是"可有可无的额外信息"：
 * 它是 DEPT 档位唯一的取值来源。排查"这个人的数据范围为什么是这些部门"时，
 * 第一个要看的往往就是"他的主岗在哪"。
 */
@Schema(description = "组织成员查询条件")
public class OrgMemberQuery extends PageQuery {

    @Schema(description = "只取主岗：true 时仅返回 isPrimaryPosition = 1 的归属。不传表示全部")
    private Boolean primaryOnly;

    public Boolean getPrimaryOnly() {
        return primaryOnly;
    }

    public void setPrimaryOnly(Boolean primaryOnly) {
        this.primaryOnly = primaryOnly;
    }

}
