package com.example.bi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.bi.common.ErrorCode;
import com.example.bi.entity.User;
import com.example.bi.enums.UserRoleEnum;
import com.example.bi.exception.BusinessException;
import com.example.bi.service.UserService;
import com.example.bi.mapper.UserMapper;
import com.example.bi.vo.LoginUserVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

import static com.example.bi.constant.UserConstant.USER_LOGIN_STATE;

/**
* @author xy
* @description 针对表【user(用户)】的数据库操作Service实现
*/
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService{

    /**
     * 盐值，混淆密码
     */
    private static final String SALT = "xy";
    public long userRegister(String userAccount, String userPassword, String checkPassword){
        //进行校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        //密码与确认密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码输入不一致");
        }
        synchronized (userAccount.intern()){
            //账号不能重复
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            //要查询userAccount字段等于userAccount变量的记录
            queryWrapper.eq("userAccount",userAccount);
            //执行数据库查询操作，使用selectCount()方法返回满足查询条件的记录数量
            long count = this.baseMapper.selectCount(queryWrapper);
            if(count > 0){
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
            }
            //插入数据
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(userPassword);
            //save()方法是MyBatis-Plus提供的一个方法，它会自动执行插入操作，并返回一个布尔值表示插入是否成功
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
            }
            return user.getId();
        }

    }

    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request){
        // 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", userPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            System.out.println(userPassword);
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        //将user对象的属性值复制到loginUserVO对象中
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        //从当前会话（Session）中获取名为USER_LOGIN_STATE的属性值：用户登录状态
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        //获取到的userObj强制转换为User类型,从而获取当前登录用户的对象
        User currentUser = (User) userObj;
        //如果登录状态失效或未登录，则抛出异常
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        long userId = currentUser.getId();
        //数据库根据ID查询
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }
    @Override
    public boolean userLogout(HttpServletRequest request){
        if (request.getSession().getAttribute(USER_LOGIN_STATE) == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    @Override
    public boolean isAdmin(HttpServletRequest request) {
        // 仅管理员可查询
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        return isAdmin(user);
    }
}




