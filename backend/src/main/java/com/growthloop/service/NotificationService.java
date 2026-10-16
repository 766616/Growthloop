package com.growthloop.service;

import com.growthloop.entity.Notification;
import com.growthloop.entity.User;
import com.growthloop.exception.ApiException;
import com.growthloop.repository.NotificationRepository;
import com.growthloop.util.M;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class NotificationService {
    private final NotificationRepository repo;
    private final Views views;

    public NotificationService(NotificationRepository repo, Views views) {
        this.repo = repo;
        this.views = views;
    }

    @Transactional
    public void notify(User user, String title, String message) {
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setMessage(message);
        n.setRead(false);
        repo.save(n);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(Long userId) {
        return M.of("items", repo.findTop30ByUserIdOrderByCreatedAtDesc(userId).stream().map(views::notification).toList(),
                "unread", repo.countByUserIdAndReadFalse(userId));
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification n = repo.findById(id).orElseThrow(() -> ApiException.notFound("Notification"));
        if (!n.getUser().getId().equals(userId)) throw ApiException.notFound("Notification");
        n.setRead(true);
    }

    @Transactional
    public void markAllRead(Long userId) {
        repo.findTop30ByUserIdOrderByCreatedAtDesc(userId).forEach(n -> n.setRead(true));
    }
}
