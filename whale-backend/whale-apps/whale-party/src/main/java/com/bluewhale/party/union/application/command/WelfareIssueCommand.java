package com.bluewhale.party.union.application.command;

import lombok.Data;

import java.util.List;

/**
 * 节日福利发放命令。
 *
 * <p>名单为空表示「全部在册会员」：管理员不传名单时不必在前端先拉全量会员，
 * 也避免把页面筛选结果误当成发放范围。</p>
 *
 * @author BlueWhale
 */
@Data
public class WelfareIssueCommand {

    /** 指定发放会员 ID 列表；为空表示全部在册会员 */
    private List<Long> memberIds;
}
