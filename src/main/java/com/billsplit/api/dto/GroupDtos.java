package com.billsplit.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class GroupDtos {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateGroupRequest {
        @NotBlank(message = "group name is required")
        private String name;

        @NotEmpty(message = "at least one member userId is required")
        private List<Long> memberUserIds;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddMemberRequest {
        private Long userId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupResponse {
        private Long id;
        private String name;
        private LocalDateTime createdAt;
        private List<UserDtos.UserResponse> members;
    }
}
