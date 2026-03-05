package com.example.aqi_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SearchController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * GET /api/search?q=Kochi
     * Returns list of Indian city suggestions with full address
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchCities(@RequestParam String q) {
        try {
            // Nominatim: free OpenStreetMap geocoding, no key needed
            String url = "https://nominatim.openstreetmap.org/search"
                    + "?q=" + q.replace(" ", "+")
                    + "&countrycodes=in"        // India only
                    + "&addressdetails=1"        // full address breakdown
                    + "&limit=6"
                    + "&format=json"
                    + "&featuretype=city";

            HttpHeaders headers = new HttpHeaders();
            // Nominatim requires a User-Agent
            headers.set("User-Agent", "AQI-Dashboard-App/1.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            JsonNode results = objectMapper.readTree(response.getBody());
            List<Map<String, String>> suggestions = new ArrayList<>();

            for (JsonNode result : results) {
                JsonNode address = result.path("address");

                String city  = firstNonEmpty(
                        address.path("city").asText(""),
                        address.path("town").asText(""),
                        address.path("village").asText(""),
                        address.path("county").asText("")
                );
                String state   = address.path("state").asText("");
                String country = address.path("country").asText("India");
                double lat     = result.path("lat").asDouble();
                double lon     = result.path("lon").asDouble();

                if (city.isEmpty()) continue;

                // Format: 🇮🇳 Kochi, Kerala, India
                String displayName = "🇮🇳 " + city
                        + (state.isEmpty()   ? "" : ", " + state)
                        + ", " + country;

                // Search name to pass to AQI endpoint (city only)
                Map<String, String> suggestion = new HashMap<>();
                suggestion.put("display",  displayName);
                suggestion.put("city",     city);
                suggestion.put("state",    state);
                suggestion.put("country",  country);
                suggestion.put("lat",      String.valueOf(lat));
                suggestion.put("lon",      String.valueOf(lon));

                // Avoid duplicates
                boolean duplicate = suggestions.stream()
                        .anyMatch(s -> s.get("city").equalsIgnoreCase(city)
                                && s.get("state").equalsIgnoreCase(state));
                if (!duplicate) suggestions.add(suggestion);
            }

            return ResponseEntity.ok(suggestions);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }
}
