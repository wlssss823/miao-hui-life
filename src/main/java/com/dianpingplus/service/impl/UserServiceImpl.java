package com.dianpingplus.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianpingplus.dto.LoginFormDTO;
import com.dianpingplus.dto.Result;
import com.dianpingplus.dto.UserDTO;
import com.dianpingplus.entity.User;
import com.dianpingplus.mapper.UserMapper;
import com.dianpingplus.service.IUserService;
import com.dianpingplus.utils.RegexUtils;
import com.dianpingplus.utils.ThreadLocalUserUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.dianpingplus.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    static final String USER_NICKNAME_PREFIX = "user_";

    @Resource
    StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号校验错误");
        }
        //生成
        String firstCode = String.valueOf(RandomUtil.randomInt(1,10));
        String pastCode = RandomUtil.randomNumbers(5);

        String verifyCode = firstCode + pastCode;

        //存入redis
        stringRedisTemplate.opsForValue().set(phone, verifyCode, 5, TimeUnit.MINUTES);
        //发送
        log.debug("发送验证码:{}",verifyCode);

        return Result.ok(verifyCode);
    }

    /**
     * 登录或注册
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号校验错误");
        }

        String verifyCodeFromUser = loginForm.getCode();
        String verifyCodeFromSys = stringRedisTemplate.opsForValue().get(phone);
        if(verifyCodeFromSys == null || !verifyCodeFromSys.equals(verifyCodeFromUser)){
            return Result.fail("验证码错误");
        }
        //查询用户
        User user = query().eq("phone", phone).one();
        //不存在就创建
        if(user == null){
            user = createWithPhone(phone);
        }

        //保存到redis中
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        //生成token
        String token = UUID.randomUUID().toString();
        //存入Redis
        //转为HashMap
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).
                setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    /**
     * 创建用户
     * @param phone
     * @return
     */
    private User createWithPhone(String phone) {
        User newUser = new User();
        newUser.setPhone(phone);
        newUser.setNickName(USER_NICKNAME_PREFIX + RandomUtil.randomString(10));
        save(newUser);
        return newUser;
    }

    @Override
    public Result queryUserById(Long id) {
        User user = getById(id);
        if(user == null){
            return Result.fail("用户不存在");
        }
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        return Result.ok(userDTO);
    }

    @Override
    public Result sign() {
        Long userId = ThreadLocalUserUtils.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        //处理key
        String date = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + date;
        //处理日
        int day = now.getDayOfMonth();
        //00000000000 -> 0100000000...
        stringRedisTemplate.opsForValue().setBit(key, day - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //获取签到表
        Long userId = ThreadLocalUserUtils.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + date;
        int dayOfMonth = now.getDayOfMonth();

        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if(result == null || result.isEmpty())return Result.ok(0);

        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        //统计
        int count = 0;
        while ((num & 1) != 0) {
            count++;
            num >>>= 1;
        }

        return Result.ok(count);
    }
}
