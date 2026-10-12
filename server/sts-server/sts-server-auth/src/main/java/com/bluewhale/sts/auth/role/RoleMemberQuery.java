package com.bluewhale.sts.auth.role;

import com.bluewhale.sts.common.basic.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 角色成员的分页查询条件。
 *
 * <p><b>成员列表分页，而资源授权列表不分页 —— 这个差别是有意的。</b>
 * 一个角色能授出的资源数被权限码的总量封顶（当前 17 个，长期也就百量级），
 * 一次取完是合理的；而成员是**多对多**的，"全员"这类角色的成员可能上万，
 * 一次取完就是一次全表扫描加全量序列化，接口会以"正常返回 200"的方式打垮自己。
 * 分页在这里不是形式，是必须。
 *
 * <p><b>只有 {@code memberType} 一个过滤条件，没有关键字搜索。</b>
 * 成员名在**另一张表**里，而且随成员类型在两张表之间切换：要按名字过滤就得先跨表
 * 解析出名字、再过滤 —— 那样 {@code total} 就不是数据库给的那个数了，得先全量捞出
 * 关系行才敢报总数，等于把分页的收益（不取全量）还回去。成员列表的主要用途是
 * "看这个角色给了谁、按类型筛"，按名字找一个人应当从**用户**列表那一侧进入
 * （那里名字是本表的列，可以走索引）。等真的出现"在角色里按名字搜成员"的诉求，
 * 正解是在关系表上冗余一份成员名，而不是在这里做一次跨表后过滤。
 */
@Schema(description = "角色成员查询条件")
public class RoleMemberQuery extends PageQuery {

    @Schema(description = "成员类型：USER / GROUP，不传表示全部")
    private String memberType;

    public String getMemberType() {
        return memberType;
    }

    public void setMemberType(String memberType) {
        this.memberType = memberType;
    }

}
