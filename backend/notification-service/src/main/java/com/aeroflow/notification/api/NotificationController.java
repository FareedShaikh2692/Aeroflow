package com.aeroflow.notification.api;

import com.aeroflow.common.security.CurrentUser;
import com.aeroflow.common.security.Roles;
import com.aeroflow.common.web.PageResponse;
import com.aeroflow.notification.service.NotificationService;
import com.aeroflow.notification.service.NotificationService.NotificationView;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.IS_STAFF)
    public PageResponse<NotificationView> list(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) String channel,
                                               @RequestParam(required = false) String type,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.search(q, channel, type, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by("createdAt").descending()));
    }

    @GetMapping("/me")
    public List<NotificationView> mine() {
        return service.forRecipient(CurrentUser.require().email());
    }
}
