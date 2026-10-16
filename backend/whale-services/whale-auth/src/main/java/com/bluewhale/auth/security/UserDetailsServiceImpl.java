package com.bluewhale.auth.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.entity.SysUser;
import com.bluewhale.auth.mapper.SysUserMapper;
import com.bluewhale.auth.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * UserDetailsService 实现：sys_user + sys_user_role + sys_role 联合加载
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final SysRoleService roleService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        List<String> roles = roleService.listRoleCodesByUserId(user.getId());
        boolean enabled = user.getStatus() != null && user.getStatus() == 1;
        return new LoginUser(user.getId(), user.getUsername(), user.getPassword(), user.getNickname(), roles, enabled);
    }
}
