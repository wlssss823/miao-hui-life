package com.dianpingplus.interceptor;

import com.dianpingplus.dto.UserDTO;
import com.dianpingplus.utils.ThreadLocalUserUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 校验登陆状态
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler chosen handler to execute, for type and/or instance evaluation
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        UserDTO user = ThreadLocalUserUtils.getUser();
        if(user == null){
            response.setStatus(401);
            return false;
        }

        return true;
    }

}
