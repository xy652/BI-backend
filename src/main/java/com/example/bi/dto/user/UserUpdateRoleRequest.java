package com.example.bi.dto.user;

import java.io.Serializable;

/**
 * 用户更新个人信息请求
 */
public class UserUpdateRoleRequest implements Serializable {

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;


    private static final long serialVersionUID = 1L;
}
