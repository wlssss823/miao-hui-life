package com.dianpingplus.controller;


import com.dianpingplus.dto.LoginFormDTO;
import com.dianpingplus.dto.Result;
import com.dianpingplus.dto.UserDTO;
import com.dianpingplus.entity.UserInfo;
import com.dianpingplus.service.IUserInfoService;
import com.dianpingplus.service.IUserService;
import com.dianpingplus.utils.RedisConstants;
import com.dianpingplus.utils.ThreadLocalUserUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 */
@Tag(name = "用户", description = "登录、注册、签到")
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     */
    @Operation(summary = "发送验证码")
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @Operation(summary = "登录/注册")
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.login(loginForm,session);
    }

    /**
     * 登出功能：删除 Redis 中的 token 会话
     * @return 无
     */
    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result logout(){
        // 从请求头获取 token
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String token = request.getHeader("authorization");
            // 删除 Redis 中的 token 会话数据
            if (token != null) {
                stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
            }
        }
        // 清除 ThreadLocal
        ThreadLocalUserUtils.remove();
        return Result.ok();
    }

    @GetMapping("/me")
    public Result me(){
        UserDTO user = ThreadLocalUserUtils.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    @GetMapping("/{id}")
    public Result queryUserByid(@PathVariable("id") Long id){
        return userService.queryUserById(id);
    }

    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }
}
