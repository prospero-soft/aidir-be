package ro.prospero.aidir.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class DirectusConfig {
    private String url;
    private String apiToken;
}
