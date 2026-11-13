package com.bluewhale.auth.application.model.result;

import java.time.LocalDateTime;

/**
 * 用户三方绑定视图（权限中心扩展 M2，账号设置页数据源）。
 *
 * @param provider 渠道编码（小写）
 * @param providerName 渠道显示名
 * @param nickname 三方昵称快照（可空）
 * @param avatar 三方头像快照（可空）
 * @param boundTime 绑定时间
 * @author BlueWhale
 */
public record SocialBindingResult(String provider, String providerName,
                                  String nickname, String avatar, LocalDateTime boundTime) {
}
