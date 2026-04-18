package com.orionkey.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "guestbook_messages")
public class GuestbookMessage extends BaseEntity {

    @Column(nullable = false)
    private UUID orderId;

    @Column(length = 40)
    private String nickname;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String productSummary;

    @Column(name = "is_visible", nullable = false)
    private boolean visible = true;
}
