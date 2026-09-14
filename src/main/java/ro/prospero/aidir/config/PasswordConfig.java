package ro.prospero.aidir.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The one thing this application takes from Spring Security.
 *
 * <p>{@code SecurityAutoConfiguration} is excluded (see application.properties), so the encoder that
 * would normally come with it has to be declared by hand. There is deliberately no filter chain and no
 * login here: onboarding only has to <em>store</em> a password it can verify later, and authentication
 * is its own task.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
