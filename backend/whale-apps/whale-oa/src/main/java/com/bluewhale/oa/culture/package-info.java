/**
 * 企业文化上下文（M5 落地）。
 *
 * <p>负责企业文化内容、生日/周年/大事记、投票问卷与建议箱（匿名控制）。
 * 消息投递归 Message，即时通讯归 IM。</p>
 *
 * <p>分层：{@code interfaces → application → domain → infrastructure}，
 * 依赖方向由 ArchUnit 守护。</p>
 *
 * @author BlueWhale
 */
package com.bluewhale.oa.culture;
