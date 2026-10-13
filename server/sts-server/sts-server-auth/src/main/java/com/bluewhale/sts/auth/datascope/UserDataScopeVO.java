package com.bluewhale.sts.auth.datascope;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 某个用户的**数据范围求值结果** —— "这个人能看到多大范围的数据"。
 *
 * <p><b>这个 VO 存在的意义是把"已配置"与"已生效"分开。</b>
 * 一个角色的 {@code data_scope} 写在表里是一回事，某个查询真的按它过滤了是另一回事。
 * 两者混为一谈时，"我配了数据范围但不管用"这种问题没有答案 ——
 * 所以这里用 {@link #precise} 把"这一档能不能被精确求值"显式说出来，
 * 而不是让调用方去猜。
 *
 * <p><b>关于"可见组织 id 集合"这一项，本类此前明确不返回，现在返回了 ——
 * 触发条件到达了，值得记下变化的原因：</b>
 * 原先的理由是"本期没有任何一处查询会消费它，返回一个恒为空的集合比不返回更糟 ——
 * 调用方会以为'这个人的可见组织就是空的'，而真实原因是'这个字段还没实现'"。
 * 组织树与成员归属落地之后，这个字段**不再是恒为空**：{@code DEPT} 系列有了确切的
 * 求值口径（主岗所在组织 / 其启用子树），而它的消费方就是**反查接口本身** ——
 * 那个接口的全部意义是回答"这个人的范围具体是哪些组织"，
 * 从审计面板点进来看到"本部门及下级（研发部 → 前端组、后端组）"才是可用的答案。
 *
 * <p>但要如实说清它**还没有**成为过滤条件的输入：本仓仍然没有业务数据表，
 * 而审计域的可见性规则（{@code ADR-0025}）刻意保持"非 ALL 即仅本人"——
 * 把 DEPT 接进审计过滤会是一次**静默的放宽**（升级后一个 DEPT 角色突然能看到
 * 整个部门的审计记录）。那是独立的一刀，见 {@code ADR-0030} 的"未覆盖"。
 * 因此：{@link #precise} 现在为 true 意味着"**这一档已经能被翻译成一组组织**"，
 * 不意味着"已经有查询按它过滤了"。
 */
@Getter
@Setter
@Schema(name = "UserDataScopeVO", description = "用户的数据范围求值结果：合并后的档位、来源角色、"
        + "该档位是否能被精确求值，以及（DEPT 系列）可见的组织集合")
public class UserDataScopeVO {

    @Schema(description = "生效角色持有的数据范围档位（去重），按由宽到窄排列；为空表示没有任何角色带数据范围")
    private List<String> scopes;

    @Schema(description = "合并后的最宽档位：ALL / DEPT_AND_CHILD / DEPT / CUSTOM / SELF；无角色时为 null")
    private String scope;

    @Schema(description = "档位的中文标签")
    private String scopeLabel;

    @Schema(description = "这些档位来自哪些角色（去重），用于回答\"这个范围是谁给的\"")
    private List<String> roleCodes;

    @Schema(description = "是否为「全部数据」—— 已接入的查询据此放行整个租户")
    private boolean allData;

    @Schema(description = "这一档能否被**精确求值**。ALL 与 SELF 恒为 true；"
            + "DEPT / DEPT_AND_CHILD 在**该用户有一个有效主岗**时为 true；"
            + "CUSTOM 恒为 false（filter_json 解析未实现）")
    private boolean precise;

    @Schema(description = "主岗所在组织的主键。仅当最宽档位是 DEPT 系列、且主岗可解析时才有值")
    private Long primaryOrgId;

    @Schema(description = "主岗所在组织的名称")
    private String primaryOrgName;

    @Schema(description = "主岗所在组织的全称（形如 蓝鲸集团/研发部/前端组）")
    private String primaryOrgFullName;

    @Schema(description = "DEPT 系列可见的组织主键集合：DEPT 只有主岗那一个，"
            + "DEPT_AND_CHILD 还含**已启用的**全部下级。其他档位为 null —— null 表示"
            + "\"这一档不是按组织划定的\"，与空集合（\"一个组织也没有\"）是两件事")
    private List<Long> visibleOrgIds;

    @Schema(description = "当 precise 为 false 时的说明：档位未实现求值、或该用户没有有效主岗，"
            + "已接入的查询**保守地按「仅本人」处理**")
    private String note;

}
