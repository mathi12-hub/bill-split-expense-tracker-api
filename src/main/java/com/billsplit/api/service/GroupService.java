package com.billsplit.api.service;

import com.billsplit.api.dto.GroupDtos.AddMemberRequest;
import com.billsplit.api.dto.GroupDtos.CreateGroupRequest;
import com.billsplit.api.dto.GroupDtos.GroupResponse;
import com.billsplit.api.dto.UserDtos.UserResponse;
import com.billsplit.api.entity.Group;
import com.billsplit.api.entity.GroupMember;
import com.billsplit.api.entity.User;
import com.billsplit.api.exception.BadRequestException;
import com.billsplit.api.exception.ResourceNotFoundException;
import com.billsplit.api.repository.GroupMemberRepository;
import com.billsplit.api.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserService userService;

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request) {
        Group group = Group.builder().name(request.getName()).build();
        Group saved = groupRepository.save(group);

        for (Long userId : request.getMemberUserIds()) {
            User user = userService.findUserOrThrow(userId);
            GroupMember member = GroupMember.builder().group(saved).user(user).build();
            groupMemberRepository.save(member);
        }
        return toResponse(saved);
    }

    @Transactional
    public GroupResponse addMember(Long groupId, AddMemberRequest request) {
        Group group = findGroupOrThrow(groupId);
        User user = userService.findUserOrThrow(request.getUserId());

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, request.getUserId())) {
            throw new BadRequestException("User " + request.getUserId() + " is already a member of group " + groupId);
        }

        groupMemberRepository.save(GroupMember.builder().group(group).user(user).build());
        return toResponse(group);
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(Long id) {
        return toResponse(findGroupOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getAllGroups() {
        return groupRepository.findAll().stream().map(this::toResponse).toList();
    }

    public Group findGroupOrThrow(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + id));
    }

    private GroupResponse toResponse(Group group) {
        List<UserResponse> members = groupMemberRepository.findByGroupId(group.getId()).stream()
                .map(gm -> UserResponse.builder()
                        .id(gm.getUser().getId())
                        .name(gm.getUser().getName())
                        .email(gm.getUser().getEmail())
                        .createdAt(gm.getUser().getCreatedAt())
                        .build())
                .toList();

        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .createdAt(group.getCreatedAt())
                .members(members)
                .build();
    }
}
