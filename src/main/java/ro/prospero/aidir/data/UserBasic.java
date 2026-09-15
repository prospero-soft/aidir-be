package ro.prospero.aidir.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserBasic(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        @NotBlank String country,
        @NotBlank @Size(min = PASSWORD_MIN_LENGTH) String password,
        @NotBlank String confirmPassword,
        @AssertTrue(message = "basic.agreed must be accepted") boolean agreed
) {
    public static final int PASSWORD_MIN_LENGTH = 8;

    @JsonIgnore
    @AssertTrue(message = "basic.password and basic.confirmPassword must match")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }
}
