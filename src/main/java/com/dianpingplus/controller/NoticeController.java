package com.dianpingplus.controller;

import com.dianpingplus.dto.Result;
import com.dianpingplus.service.INoticeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/notice")
public class NoticeController {

    @Resource
    private INoticeService noticeService;

    /**
     * 订阅 SSE 实时通知
     */
    @GetMapping("/subscribe")
    public SseEmitter subscribe() {
        return noticeService.subscribe();
    }

    /**
     * 分页查询通知列表
     */
    @GetMapping("/list")
    public Result list(@RequestParam(defaultValue = "1") Integer current) {
        return noticeService.queryNoticeList(current);
    }

    /**
     * 未读数量
     */
    @GetMapping("/unread")
    public Result unreadCount() {
        return noticeService.unreadCount();
    }

    /**
     * 标记单条已读
     */
    @PutMapping("/read/{id}")
    public Result markRead(@PathVariable("id") Long id) {
        return noticeService.markRead(id);
    }

    /**
     * 标记全部已读
     */
    @PutMapping("/read/all")
    public Result markAllRead() {
        return noticeService.markAllRead();
    }
}
