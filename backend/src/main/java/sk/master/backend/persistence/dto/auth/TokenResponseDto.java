package sk.master.backend.persistence.dto.auth;

public record TokenResponseDto(String accessToken, String tokenType) {

    public static TokenResponseDto of(String accessToken) {
        return new TokenResponseDto(accessToken, "Bearer");
    }
}
