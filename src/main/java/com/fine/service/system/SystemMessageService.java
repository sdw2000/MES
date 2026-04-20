package com.fine.service.system;

import java.util.Map;

public interface SystemMessageService {

    void createRoleMessage(String targetRole,
                           String title,
                           String content,
                           String bizType,
                           String bizId,
                           String routePath,
                           String routeQueryJson,
                           String createdBy);

    long unreadCountForCurrentUser();

    Map<String, Object> pageForCurrentUser(int current, int size, boolean onlyUnread);

    boolean markReadForCurrentUser(Long messageId);

    int markAllReadForCurrentUser();
}
