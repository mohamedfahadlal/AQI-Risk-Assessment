package com.example.aqi_backend.controller;

import com.example.aqi_backend.service.AqiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AqiController {

    private final AqiService aqiService;

    public AqiController(AqiService aqiService) {
        this.aqiService = aqiService;
    }

    // GET /api/aqi?city=Kochi
    @GetMapping("/aqi")
    public ResponseEntity<?> getAqi(@RequestParam String city) {
        try {
            return ResponseEntity.ok(aqiService.getAqiByCity(city));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/aqi/locate?lat=9.93&lon=76.26
    @GetMapping("/aqi/locate")
    public ResponseEntity<?> getAqiByLocation(@RequestParam double lat,
                                              @RequestParam double lon) {
        try {
            return ResponseEntity.ok(aqiService.getAqiByCoords(lat, lon));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/aqi/history?lat=9.93&lon=76.26
    // Returns past 5 days of hourly AQI readings (~120 points)
    // Used by Flask /metrics to get real y_true ground truth values
    @GetMapping("/aqi/history")
    public ResponseEntity<?> getAqiHistory(@RequestParam double lat,
                                           @RequestParam double lon) {
        try {
            return ResponseEntity.ok(aqiService.getAqiHistory(lat, lon));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/search?q=Koc
    @GetMapping("/search")
    public ResponseEntity<?> searchCities(@RequestParam String q) {
        try {
            return ResponseEntity.ok(aqiService.searchCities(q));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/forecast?city=Kochi
    @GetMapping("/forecast")
    public ResponseEntity<?> getForecast(@RequestParam String city) {
        try {
            return ResponseEntity.ok(aqiService.getForecast(city));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/forecast/locate?lat=9.93&lon=76.26
    @GetMapping("/forecast/locate")
    public ResponseEntity<?> getForecastByLocation(@RequestParam double lat,
                                                   @RequestParam double lon) {
        try {
            return ResponseEntity.ok(aqiService.getForecastByCoords(lat, lon));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // GET /api/cities/india
    @GetMapping("/cities/india")
    public ResponseEntity<?> getIndiaCities() {
        try {
            return ResponseEntity.ok(aqiService.getIndiaCities());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
