package com.bluewhale.sts.auth.usergroup;

import com.bluewhale.sts.common.basic.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户组成员的分页查询条件。
 *
 * <p>与 {@code RoleMemberQuery} 同构，取舍也相同：**分页、但不做按名搜索**。
 * 一个组里的人没有上限（"全员"这类组可能上万），一次取完就是一次全表扫描
 * 加全量序列化，接口会以"正常返回 200"的方式打垮自己；而按名字过滤需要先跨表
 * 解析出名字再过滤，那样 {@code total} 就不再是数据库给的那个数，等于把分页的收益还回去。
 * 按名字找一个人应当从**用户**列表那一侧进入。
 */
@Schema(description = "用户组成员查询条件")
public class UserGroupMemberQuery extends PageQuery {

    @Schema(description = "成员类型：USER，不传表示全部")
    private String memberType;

    public String getMemberType() {
        return memberType;
    }

    public void setMemberType(String memberType) {
        this.memberType = memberType;
    }

}
