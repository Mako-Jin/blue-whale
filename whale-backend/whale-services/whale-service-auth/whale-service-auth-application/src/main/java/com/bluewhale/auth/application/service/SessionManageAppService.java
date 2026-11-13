package com.bluewhale.auth.application.service;

import com.bluewhale.auth.application.model.query.LoginHistoryPageQuery;
import com.bluewhale.auth.application.model.query.SessionPageQuery;
import com.bluewhale.auth.application.model.vo.SessionVOs.DeviceVO;
import com.bluewhale.auth.application.model.vo.SessionVOs.LoginHistoryVO;
import com.bluewhale.auth.application.model.vo.SessionVOs.SessionVO;
import com.bluewhale.common.core.result.PageResult;

import java.util.List;

/**
 * 会话管理应用服务（权限中心扩展 M3）：管理端会话查询、强制下线、设备与登录历史。
 *
 * @author BlueWhale
 */
public interface SessionManageAppService {

    /**
     * 分页查询用户会话（refreshToken 记录账本）
     *
     * @param query 分页查询
     * @return 会话分页结果
     */
    PageResult<SessionVO> pageSessions(SessionPageQuery query);

    /**
     * 强制下线指定会话（按 jti）：账本标记 revoked + 写入用户撤销通道即时生效
     *
     * @param jti 会话令牌唯一标识
     */
    void revokeSession(String jti);

    /**
     * 强制下线用户全部会话
     *
     * @param userId 用户 ID
     */
    void revokeAllSessions(Long userId);

    /**
     * 查询用户设备列表（按最近使用时间降序）
     *
     * @param userId 用户 ID
     * @return 设备列表
     */
    List<DeviceVO> listDevices(Long userId);

    /**
     * 标记 / 取消信任设备
     *
     * @param deviceId 设备主键
     * @param trusted 是否信任
     */
    void changeDeviceTrust(Long deviceId, boolean trusted);

    /**
     * 分页查询登录历史（按事件时间降序）
     *
     * @param query 分页查询
     * @return 登录历史分页结果
     */
    PageResult<LoginHistoryVO> pageLoginHistory(LoginHistoryPageQuery query);
}
