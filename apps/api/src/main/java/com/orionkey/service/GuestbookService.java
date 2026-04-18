package com.orionkey.service;

import com.orionkey.common.PageResult;

import java.util.Map;
import java.util.UUID;

public interface GuestbookService {

    PageResult<?> listPublicMessages(int page, int pageSize);

    Object createMessage(Map<String, Object> request);

    PageResult<?> listAdminMessages(int page, int pageSize);

    void updateVisibility(UUID id, boolean isVisible);
}
