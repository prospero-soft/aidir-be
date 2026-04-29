package ro.prospero.aidir.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class DrupalConfig {
    private String url;
    private String user;
    // fixme: should add to http client config on build once I have a better grasp on drupal
    private String password;
}
