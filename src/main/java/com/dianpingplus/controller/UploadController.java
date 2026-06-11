package com.dianpingplus.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.dianpingplus.dto.Result;
import com.dianpingplus.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, filename);
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    @PostMapping("review")
    public Result uploadReviewImage(@RequestParam("file") MultipartFile image) {
        // 校验文件类型
        String originalFilename = image.getOriginalFilename();
        if (originalFilename == null) {
            return Result.fail("文件名称不能为空");
        }
        String suffix = StrUtil.subAfter(originalFilename, ".", true).toLowerCase();
        if (!"jpg".equals(suffix) && !"jpeg".equals(suffix) && !"png".equals(suffix) && !"webp".equals(suffix)) {
            return Result.fail("仅支持 jpg/png/webp 格式的图片");
        }
        // 校验文件大小（最大 5MB）
        if (image.getSize() > 5 * 1024 * 1024) {
            return Result.fail("图片大小不能超过 5MB");
        }
        try {
            String fileName = createNewFileName(originalFilename, "reviews");
            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
            log.debug("评价图片上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @GetMapping("/review/delete")
    public Result deleteReviewImg(@RequestParam("name") String filename) {
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, filename);
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    private String createNewFileName(String originalFilename, String module) {
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/{}/{}/{}", module, d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return StrUtil.format("/{}/{}/{}/{}.{}", module, d1, d2, name, suffix);
    }

    private String createNewFileName(String originalFilename) {
        return createNewFileName(originalFilename, "blogs");
    }
}
