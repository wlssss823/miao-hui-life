package com.dianpingplus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dianpingplus.dto.Result;
import com.dianpingplus.entity.Notice;
import com.dianpingplus.mapper.NoticeMapper;
import com.dianpingplus.service.INoticeService;
import com.dianpingplus.utils.ThreadLocalUserUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements INoticeService {

    /** 用户 SSE 连接池：userId → emitter 列表 */
    private final Map<Long, List<SseEmitter>> sseEmitters = new ConcurrentHashMap<>();

    @Override
    public void addNotice(Long userId, Integer type, String title, String content, Long relatedId) {
        // 落地 DB
        Notice notice = new Notice();
        notice.setUserId(userId);
        notice.setType(type);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setRelatedId(relatedId);
        notice.setIsRead(false);
        save(notice);

        // SSE 实时推送
        List<SseEmitter> emitters = sseEmitters.get(userId);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("notice")
                            .data(notice));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                    emitters.remove(emitter);
                }
            }
        }
    }

    @Override
    public Result queryNoticeList(Integer current) {
        Long userId = ThreadLocalUserUtils.getUser().getId();
        Page<Notice> page = page(
                new Page<>(current, 20),
                new QueryWrapper<Notice>()
                        .eq("user_id", userId)
                        .orderByDesc("create_time"));
        return Result.ok(page.getRecords(), page.getTotal());
    }

    @Override
    public Result unreadCount() {
        Long userId = ThreadLocalUserUtils.getUser().getId();
        long count = count(new QueryWrapper<Notice>()
                .eq("user_id", userId)
                .eq("is_read", false));
        return Result.ok(count);
    }

    @Override
    public Result markRead(Long id) {
        Notice notice = getById(id);
        if (notice == null) return Result.fail("通知不存在");
        Long userId = ThreadLocalUserUtils.getUser().getId();
        if (!notice.getUserId().equals(userId)) return Result.fail("无权操作");
        notice.setIsRead(true);
        updateById(notice);
        return Result.ok();
    }

    @Override
    public Result markAllRead() {
        Long userId = ThreadLocalUserUtils.getUser().getId();
        update().eq("user_id", userId).eq("is_read", false)
                .set("is_read", 1).update();
        return Result.ok();
    }

    @Override
    public SseEmitter subscribe() {
        Long userId = ThreadLocalUserUtils.getUser().getId();
        // 超时时间 30 分钟，由客户端重连
        SseEmitter emitter = new SseEmitter(1_800_000L);

        sseEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        return emitter;
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = sseEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                sseEmitters.remove(userId);
            }
        }
    }
}
