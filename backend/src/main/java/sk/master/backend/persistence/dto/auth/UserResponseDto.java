package sk.master.backend.persistence.dto.auth;

import sk.master.backend.persistence.entity.UserEntity;
import sk.master.backend.persistence.model.Role;

public record UserResponseDto(Long id, String name, String email, Role role, boolean enabled) {

    public static UserResponseDto fromEntity(UserEntity entity) {
        return new UserResponseDto(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getRole(),
                entity.isEnabled()
        );
    }
}
