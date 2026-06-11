package com.dianpingplus.controller;


import com.dianpingplus.dto.Result;
import com.dianpingplus.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @PutMapping("/{id}/{op}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("op")Boolean op){
        return followService.follow(id, op);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id){
        return followService.isFollow(id);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id){
        return followService.followCommons(id);
    }
}
