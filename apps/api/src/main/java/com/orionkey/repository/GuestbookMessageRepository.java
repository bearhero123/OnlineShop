package com.orionkey.repository;

import com.orionkey.entity.GuestbookMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GuestbookMessageRepository extends JpaRepository<GuestbookMessage, UUID> {

    Page<GuestbookMessage> findByVisibleTrueOrderByCreatedAtDesc(Pageable pageable);

    Page<GuestbookMessage> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
