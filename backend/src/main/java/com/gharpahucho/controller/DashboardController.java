package com.gharpahucho.controller;

import com.gharpahucho.dto.DashboardDtos.Dashboard;
import com.gharpahucho.security.CurrentUser;
import com.gharpahucho.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboard;

    @GetMapping
    public Dashboard get() {
        return dashboard.load(CurrentUser.id());
    }
}
