package com.ticketing.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public record Item(Long id, NotificationType type, String message, boolean read, LocalDateTime createdAt) {}

    public record Response(long unreadCount, List<Item> items) {}

    @GetMapping
    public Response mine(@AuthenticationPrincipal Long memberId) {
        List<Item> items = notificationRepository.findTop30ByMemberIdOrderByIdDesc(memberId).stream()
                .map(n -> new Item(n.getId(), n.getType(), n.getMessage(), n.isRead(), n.getCreatedAt()))
                .toList();
        return new Response(notificationRepository.countByMemberIdAndReadFalse(memberId), items);
    }

    @PostMapping("/read-all")
    public void readAll(@AuthenticationPrincipal Long memberId) {
        notificationRepository.markAllRead(memberId);
    }
}
