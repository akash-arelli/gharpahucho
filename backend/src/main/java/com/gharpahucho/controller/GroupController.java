package com.gharpahucho.controller;

import com.gharpahucho.dto.GroupDtos.*;
import com.gharpahucho.integration.flagsmith.FeatureKey;
import com.gharpahucho.integration.flagsmith.RequiresFeature;
import com.gharpahucho.security.CurrentUser;
import com.gharpahucho.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groups;

    /** Creating and inviting is gated by the Flagsmith flag travel_group_creation; reading and chatting is not. */
    @PostMapping
    @RequiresFeature(FeatureKey.TRAVEL_GROUP_CREATION)
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDto create(@Valid @RequestBody CreateGroupRequest request) {
        return groups.create(CurrentUser.id(), request);
    }

    @GetMapping
    public List<GroupSummary> mine() {
        return groups.mine(CurrentUser.id());
    }

    @GetMapping("/invitations")
    public List<GroupSummary> invitations() {
        return groups.invitations(CurrentUser.id());
    }

    @GetMapping("/{id}")
    public GroupDto get(@PathVariable UUID id) {
        return groups.get(CurrentUser.id(), id);
    }

    @PatchMapping("/{id}")
    public GroupDto update(@PathVariable UUID id, @Valid @RequestBody UpdateGroupRequest request) {
        return groups.update(CurrentUser.id(), id, request);
    }

    @PostMapping("/{id}/invite")
    @RequiresFeature(FeatureKey.TRAVEL_GROUP_CREATION)
    public GroupDto invite(@PathVariable UUID id, @Valid @RequestBody InviteRequest request) {
        return groups.invite(CurrentUser.id(), id, request.userId());
    }

    @PostMapping("/{id}/respond")
    public GroupDto respond(@PathVariable UUID id, @RequestBody RespondInviteRequest request) {
        return groups.respond(CurrentUser.id(), id, request.accept());
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable UUID id) {
        groups.leave(CurrentUser.id(), id);
    }

    @GetMapping("/{id}/messages")
    public List<MessageDto> messages(@PathVariable UUID id) {
        return groups.messages(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto post(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest request) {
        return groups.post(CurrentUser.id(), id, request.body());
    }
}
