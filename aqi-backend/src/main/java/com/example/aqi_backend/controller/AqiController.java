package com.example.aqi_backend.controller;

import com.example.aqi_backend.service.AqiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow JavaFX client
public class AqiController {

    private final AqiService aqiService;

    public AqiController(AqiService aqiService) {
        this.aqiService = aqiService;
    }

    /**
     * GET /api/aqi?city=Kochi
     * Returns real-time AQI + pollutants + weather for a city
     */
    @GetMapping("/aqi")
    public ResponseEntity<?> getAqi(@RequestParam String city) {
        try {
            Map<String, Object> data = aqiService.getAqiByCity(city);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                Map.of("error", e.getMessage())
            );
        }
    }
}
