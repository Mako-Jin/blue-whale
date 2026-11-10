package com.bluewhale.mail.vo;

import lombok.Data;

/**
 * 平台代开邮箱结果。
 *
 * <p><b>不包含初始口令</b>。口令解密只发生在服务端建立 IMAP 连接的那一刻；
 * 通过接口返回给调用方，等于把口令交给另一个服务的日志与内存，收益为零、风险为正。
 * 员工首次登录 Webmail 的口令交付是独立议题（见运维手册「已知缺口」）。</p>
 *
 * @author BlueWhale
 */
@Data
public class MailProvisionVO {

    /** 已创建：本次新建了邮箱并落库 */
    public static final String STATE_CREATED = "CREATED";

    /** 已存在：该用户此前已开通过，直接返回现有地址 */
    public static final String STATE_EXISTS = "EXISTS";

    /** 未启用：代开能力关闭或关键配置缺失 */
    public static final String STATE_DISABLED = "DISABLED";

    /** 冲突：地址已被占用（平台内或 Mailcow 内），需人工处理 */
    public static final String STATE_CONFLICT = "CONFLICT";

    /** 失败：调用 Mailcow 或落库出错 */
    public static final String STATE_FAILED = "FAILED";

    /** 结果状态，取值见本类常量 */
    private String state;

    /** 邮箱地址（CREATED / EXISTS 时有值） */
    private String email;

    /** 说明（DISABLED / CONFLICT / FAILED 时的原因，可直接写日志） */
    private String message;

    /**
     * 构造结果
     *
     * @param state 状态
     * @param email 邮箱地址
     * @param message 说明
     * @return 结果对象
     */
    public static MailProvisionVO of(String state, String email, String message) {
        MailProvisionVO vo = new MailProvisionVO();
        vo.setState(state);
        vo.setEmail(email);
        vo.setMessage(message);
        return vo;
    }
}
