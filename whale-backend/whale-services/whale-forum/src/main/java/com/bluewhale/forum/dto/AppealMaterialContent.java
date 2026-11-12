package com.bluewhale.forum.dto;

/**
 * 申诉材料内容（供控制器直接装配响应，不把 Web 类型带进服务层）。
 *
 * @param bytes 字节
 * @param contentType MIME 类型
 * @author BlueWhale
 */
public record AppealMaterialContent(byte[] bytes, String contentType) {
}
