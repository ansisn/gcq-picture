package com.gcq.picture.manager.auth;


import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.gcq.picture.manager.auth.model.SpaceUserAuthConfig;
import com.gcq.picture.manager.auth.model.SpaceUserRole;
import com.gcq.picture.model.entity.Space;
import com.gcq.picture.model.entity.SpaceUser;
import com.gcq.picture.model.entity.User;
import com.gcq.picture.model.enums.SpaceRoleEnum;
import com.gcq.picture.model.enums.SpaceTypeEnum;
import com.gcq.picture.service.SpaceUserService;
import com.gcq.picture.service.UserService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Component
public class SpaceUserManager {

    public  static final SpaceUserAuthConfig spaceUserAuthConfig;

    @Resource
    private UserService userService;

    @Resource
    private SpaceUserService spaceUserService;

    static {
        String json = ResourceUtil.readUtf8Str("biz/spaceUserAuthConfig.json");
         spaceUserAuthConfig = JSONUtil.toBean(json, SpaceUserAuthConfig.class);
    }

     public List<String> getPermissionsByRole(String key) {
         if (StrUtil.isBlank(key)) {
             return new ArrayList<>();
         }
         SpaceUserRole spaceUserRole = spaceUserAuthConfig
                 .getRoles()
                 .stream()
                 .filter(r -> key.equals(r.getKey()))
                 .findFirst()
                 .orElse(null);
         if (spaceUserRole == null) {
             return new ArrayList<>();
         }

         return spaceUserRole.getPermissions();
     }

    public List<String> getPermissionList(Space space, User loginUser) {
        if (loginUser == null) {
            return new ArrayList<>();
        }
        // 管理员权限
        List<String> ADMIN_PERMISSIONS = getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        // 公共图库
        if (space == null) {
            if (userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            }
            return new ArrayList<>();
        }
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        if (spaceTypeEnum == null) {
            return new ArrayList<>();
        }
        // 根据空间获取对应的权限
        switch (spaceTypeEnum) {
            case PRIVATE:
                // 私有空间，仅本人或管理员有所有权限
                if (space.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    return new ArrayList<>();
                }
            case TEAM:
                // 团队空间，查询 SpaceUser 并获取角色和权限
                SpaceUser spaceUser = spaceUserService.lambdaQuery()
                        .eq(SpaceUser::getSpaceId, space.getId())
                        .eq(SpaceUser::getUserId, loginUser.getId())
                        .one();
                if (spaceUser == null) {
                    return new ArrayList<>();
                } else {
                    return getPermissionsByRole(spaceUser.getSpaceRole());
                }
        }
        return new ArrayList<>();
    }

}
