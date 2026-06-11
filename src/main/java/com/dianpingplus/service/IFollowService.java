package com.dianpingplus.service;

import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注
     * @param id
     * @param op
     * @return
     */
    Result follow(Long id, Boolean op);

    /**
     * 判断是否已关注
     * @param id
     * @return
     */
    Result isFollow(Long id);

    /**
     * 查看当前用户和登录用户重复关注
     * @param id
     * @return
     */
    Result followCommons(Long id);
}
