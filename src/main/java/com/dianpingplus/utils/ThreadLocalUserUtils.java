package com.dianpingplus.utils;

import com.dianpingplus.dto.UserDTO;

public class ThreadLocalUserUtils {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void putUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void remove(){
        tl.remove();
    }

}
