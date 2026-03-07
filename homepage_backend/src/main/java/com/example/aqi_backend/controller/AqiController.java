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

    @GetMapping("/aqi")
    public ResponseEntity<?> getAqi(@RequestParam String city) {
        try {
            return ResponseEntity.ok(aqiService.getAqiByCity(city));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/aqi/locate")
    public ResponseEntity<?> getAqiByLocation(@RequestParam double lat,
                                              @RequestParam double lon) {
        try {
            return ResponseEntity.ok(aqiService.getAqiByCoords(lat, lon));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // REMOVED: searchCities method to resolve ambiguity with SearchController

    @GetMapping("/forecast")
    public ResponseEntity<?> getForecast(@RequestParam String city) {
        try {
            return ResponseEntity.ok(aqiService.getForecast(city));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/forecast/locate")
    public ResponseEntity<?> getForecastByLocation(@RequestParam double lat,
                                                   @RequestParam double lon) {
        try {
            return ResponseEntity.ok(aqiService.getForecastByCoords(lat, lon));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/pollutants")
    public ResponseEntity<?> getPollutants(@RequestParam String city) {
        try {
            return ResponseEntity.ok(aqiService.getPollutants(city));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/cities/india")
    public ResponseEntity<?> getIndiaCities() {
        try {
            return ResponseEntity.ok(aqiService.getIndiaCities());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}