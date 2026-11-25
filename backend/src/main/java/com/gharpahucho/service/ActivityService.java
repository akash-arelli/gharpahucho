package com.gharpahucho.service;

import com.gharpahucho.entity.ActivityEvent;
import com.gharpahucho.entity.User;
import com.gharpahucho.repository.ActivityEventRepository;
import com.gharpahucho.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityEventRepository events;
    private final UserRepository users;

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(UUID userId, String type, String message) {
        User ref = users.getReferenceById(userId);
        ActivityEvent e = new ActivityEvent();
        e.setUser(ref);
        e.setType(type);
        e.setMessage(message.length() > 300 ? message.substring(0, 300) : message);
        events.save(e);
    }
}
