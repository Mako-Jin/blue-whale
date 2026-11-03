/**
 * 视频应用层：用例编排、事务边界与跨服务调用编排。
 *
 * <p>应用服务只依赖领域层端口，不直接访问 MyBatis Mapper 或 HTTP 客户端实现。</p>
 *
 * @author BlueWhale
 */
package com.bluewhale.video.application;
