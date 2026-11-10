package com.bluewhale.mail.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mail.entity.MailRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收信规则 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface MailRuleMapper extends BaseMapper<MailRule> {
}
