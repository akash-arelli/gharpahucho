package com.gharpahucho.controller;

import com.gharpahucho.dto.UserDtos.Me;
import com.gharpahucho.dto.UserDtos.PublicProfile;
import com.gharpahucho.dto.UserDtos.UpdateProfileRequest;
import com.gharpahucho.security.CurrentUser;
import com.gharpahucho.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService users;

    @GetMapping("/me")
    public Me me() {
        return users.me(CurrentUser.id());
    }

    @PutMapping("/me")
    public Me update(@Valid @RequestBody UpdateProfileRequest request) {
        return users.update(CurrentUser.id(), request);
    }

    /** Privacy-safe profile of another traveller (first name, coarse area, public journeys). */
    @GetMapping("/{id}/profile")
    public PublicProfile profile(@PathVariable UUID id) {
        return users.publicProfile(id);
    }
}
