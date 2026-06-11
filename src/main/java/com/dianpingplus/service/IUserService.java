package com.dianpingplus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dianpingplus.dto.LoginFormDTO;
import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.User;

import jakarta.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送验证码
     * @param phone 手机号
     * @param session  session
     * @return 验证码发送结果
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 登录或注册
     * @param loginForm 登录信息
     * @param session  session
     * @return 登录结果
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 查询用户
     * @param id 用户id
     * @return 用户信息
     */
    Result queryUserById(Long id);

    /**
     * 签到
     * @return 签到结果
     */
    Result sign();

    /**
     * 统计连续签到次数
     * @return 最近连续签到次数
     */
    Result signCount();
}
