package com.bluewhale.auth.infrastructure.persistence.po;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 登录次数按天聚合行（只用于查询投影，不对应任何表）。
 *
 * @author BlueWhale
 */
@Data
public class LoginDayCountPo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件日期（{@code DATE(login_time)} 投影） */
    private LocalDate day;

    /** 登录结果：SUCCESS / FAILURE / LOGOUT */
    private String loginResult;

    /** 次数 */
    private Long total;
}
