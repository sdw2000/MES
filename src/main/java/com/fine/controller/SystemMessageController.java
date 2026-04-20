package com.fine.controller;

import com.fine.Utils.ResponseResult;
import com.fine.service.system.SystemMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/system/messages")
@PreAuthorize("hasAnyAuthority('admin','purchase','warehouse','production','finance','sales','quality','plan','scheduler')")
public class SystemMessageController {

    @Autowired
    private SystemMessageService systemMessageService;

    @GetMapping("/unread-count")
    public ResponseResult<?> unreadCount() {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("unreadCount", systemMessageService.unreadCountForCurrentUser());
            return ResponseResult.success(data);
        } catch (Exception e) {
            Map<String, Object> data = new HashMap<>();
            data.put("unreadCount", 0);
            return ResponseResult.success("系统消息服务暂不可用", data);
        }
    }

    @GetMapping("/page")
    public ResponseResult<?> page(@RequestParam(value = "current", required = false, defaultValue = "1") Integer current,
                                  @RequestParam(value = "size", required = false, defaultValue = "10") Integer size,
                                  @RequestParam(value = "onlyUnread", required = false, defaultValue = "false") Boolean onlyUnread) {
        return ResponseResult.success(systemMessageService.pageForCurrentUser(current, size, Boolean.TRUE.equals(onlyUnread)));
    }

    @PostMapping("/{id}/read")
    public ResponseResult<?> markRead(@PathVariable("id") Long id) {
        boolean ok = systemMessageService.markReadForCurrentUser(id);
        return ok ? ResponseResult.success() : ResponseResult.error(400, "标记已读失败");
    }

    @PostMapping("/read-all")
    public ResponseResult<?> readAll() {
        Map<String, Object> data = new HashMap<>();
        data.put("updated", systemMessageService.markAllReadForCurrentUser());
        return ResponseResult.success(data);
    }
}
