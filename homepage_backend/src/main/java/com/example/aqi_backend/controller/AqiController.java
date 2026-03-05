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

    // Search by city name: GET /api/aqi?city=Kochi
    @GetMapping("/aqi")
    public ResponseEntity<?> getAqi(@RequestParam String city) {
        try {
            return ResponseEntity.ok(aqiService.getAqiByCity(city));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Locate Me: GET /api/aqi/locate?lat=9.93&lon=76.26
    @GetMapping("/aqi/locate")
    public ResponseEntity<?> getAqiByLocation(@RequestParam double lat,
                                               @RequestParam double lon) {
        try {
            return ResponseEntity.ok(aqiService.getAqiByCoords(lat, lon));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
