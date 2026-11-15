package com.bluewhale.file.domain.content;

/**
 * 文件内容子域：按文件头魔数识别真实内容类型，供应用层做上传内容校验。
 *
 * <p>只做纯字节匹配，不依赖任何外部服务与第三方库；识别结果仅用于安全判定，
 * 不参与对象键生成与落库字段。</p>
 *
 * @author BlueWhale
 */
