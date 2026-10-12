package com.bluewhale.sts.auth.authz;

import lombok.Getter;
import lombok.Setter;

/**
 * 「一个生效角色 + 它的数据范围」—— 判定链取数的最小视图。
 *
 * <p><b>为什么角色编码与数据范围在同一行返回，而不是各查一次：</b>
 * 两者来自**同一组角色**，也就共用同一套生效判定（角色未删未停用、成员关系未删、
 * 两层时态都落在区间内）。分成两条 SQL 只会把那串六七行的条件写两遍，
 * 而两遍一旦漂移，症状是"角色查得到、数据范围查不到"这类自相矛盾的状态 ——
 * 它同时骗过两边各自的测试，是最难排查的一种。
 *
 * <p>不返回角色实体整行：判定只关心"编码"与"范围"两列。
 * 多带出来的列会被后来的改动当成"有人在用"，而它们其实没有消费方。
 */
@Getter
@Setter
public class RoleScopeView {

    /** 角色编码，如 {@code SUPER_ADMIN} */
    private String roleCode;

    /** 数据范围档位，取值见 {@link com.bluewhale.sts.auth.common.enums.DataScope}；可为空 */
    private String dataScope;

}
