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
 * <p><b>为什么不返回"可见组织 id 集合"这类看似更有用的东西：</b>
 * 本期没有任何一处查询会消费它。返回一个恒为空的集合，比不返回更糟 ——
 * 调用方会以为"这个人的可见组织就是空的"，而真实原因是"这个字段还没实现"。
 * 等组织归属落地、真有查询要按它过滤时再加，那时它有确切的语义。
 */
@Getter
@Setter
@Schema(name = "UserDataScopeVO", description = "用户的数据范围求值结果：合并后的档位、来源角色，以及该档位是否能被精确求值")
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

    @Schema(description = "这一档能否被精确求值。ALL 与 SELF 为 true；"
            + "DEPT / DEPT_AND_CHILD 依赖组织归属、CUSTOM 依赖 filter_json 解析，均为 false")
    private boolean precise;

    @Schema(description = "当 precise 为 false 时的说明：档位未实现求值，已接入的查询**保守地按「仅本人」处理**")
    private String note;

}
