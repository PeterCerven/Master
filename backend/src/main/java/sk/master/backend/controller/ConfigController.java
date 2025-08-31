package sk.master.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sk.master.backend.config.ApiConfig;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ApiConfig apiConfig;

    public ConfigController(ApiConfig apiConfig) {
        this.apiConfig = apiConfig;
    }

    @GetMapping("/google-maps-key")
    public Map<String, String> getGoogleMapsApiKey() {
        return Collections.singletonMap("apiKey", apiConfig.getGoogleMapsApiKey());
    }
}
