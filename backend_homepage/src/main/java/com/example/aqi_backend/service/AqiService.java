package com.example.aqi_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class AqiService {

    @Value("${owm.api.key}")
    private String owmKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Search by city name ───────────────────────────────────────
    public Map<String, Object> getAqiByCity(String city) {
        try {
            // Step 1: Geocode city → lat/lon
            String geoUrl = "https://api.openweathermap.org/geo/1.0/direct?q="
                    + city.replace(" ", "+") + ",IN&limit=1&appid=" + owmKey;
            System.out.println("=== Geocode URL: " + geoUrl);

            String geoResponse = restTemplate.getForObject(geoUrl, String.class);
            JsonNode geoRoot = objectMapper.readTree(geoResponse);

            if (!geoRoot.isArray() || geoRoot.size() == 0) {
                // Fallback: try without India constraint
                geoUrl = "https://api.openweathermap.org/geo/1.0/direct?q="
                        + city.replace(" ", "+") + "&limit=1&appid=" + owmKey;
                geoResponse = restTemplate.getForObject(geoUrl, String.class);
                geoRoot = objectMapper.readTree(geoResponse);
                if (!geoRoot.isArray() || geoRoot.size() == 0) {
                    throw new RuntimeException("City not found: " + city);
                }
            }

            double lat = geoRoot.get(0).path("lat").asDouble();
            double lon = geoRoot.get(0).path("lon").asDouble();
            String resolvedCity = geoRoot.get(0).path("name").asText(city);
            System.out.println("=== Geocoded: " + resolvedCity + " -> " + lat + "," + lon);

            return fetchAllData(lat, lon, resolvedCity);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to fetch data: " + e.getMessage());
        }
    }

    // ── Search by coordinates (Locate Me) ────────────────────────
    public Map<String, Object> getAqiByCoords(double lat, double lon) {
        try {
            // Reverse geocode to get city name
            String reverseUrl = "https://api.openweathermap.org/geo/1.0/reverse?lat="
                    + lat + "&lon=" + lon + "&limit=1&appid=" + owmKey;
            String reverseResponse = restTemplate.getForObject(reverseUrl, String.class);
            JsonNode reverseRoot = objectMapper.readTree(reverseResponse);

            String cityName = (reverseRoot.isArray() && reverseRoot.size() > 0)
                    ? reverseRoot.get(0).path("name").asText("Your Location")
                    : "Your Location";

            return fetchAllData(lat, lon, cityName);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to fetch location data: " + e.getMessage());
        }
    }

    // ── Fetch weather + air pollution from OWM ────────────────────
    private Map<String, Object> fetchAllData(double lat, double lon, String cityName) throws Exception {

        // ── Weather ──────────────────────────────────────────────
        String weatherUrl = "https://api.openweathermap.org/data/2.5/weather?lat="
                + lat + "&lon=" + lon + "&units=metric&appid=" + owmKey;
        String weatherResponse = restTemplate.getForObject(weatherUrl, String.class);
        JsonNode weatherRoot = objectMapper.readTree(weatherResponse);

        double temp      = weatherRoot.path("main").path("temp").asDouble();
        double humidity  = weatherRoot.path("main").path("humidity").asDouble();
        double windSpeed = weatherRoot.path("wind").path("speed").asDouble() * 3.6; // m/s → km/h

        System.out.println("=== Weather: temp=" + temp + " humidity=" + humidity + " wind=" + windSpeed);

        // ── Air Pollution ─────────────────────────────────────────
        String pollutionUrl = "https://api.openweathermap.org/data/2.5/air_pollution?lat="
                + lat + "&lon=" + lon + "&appid=" + owmKey;
        String pollutionResponse = restTemplate.getForObject(pollutionUrl, String.class);
        JsonNode pollutionRoot = objectMapper.readTree(pollutionResponse);

        System.out.println("=== Pollution response: " + pollutionResponse);

        JsonNode components = pollutionRoot.path("list").get(0).path("components");

        double pm25 = components.path("pm2_5").asDouble();
        double pm10 = components.path("pm10").asDouble();
        double no2  = components.path("no2").asDouble();
        double o3   = components.path("o3").asDouble();
        double co   = components.path("co").asDouble();
        double so2  = components.path("so2").asDouble();

        System.out.println("=== Pollutants (µg/m³): pm25=" + pm25 + " pm10=" + pm10
                + " no2=" + no2 + " o3=" + o3);

        // ── Calculate CPCB India AQI from real concentrations ─────
        Map<String, Double> subIndices = new HashMap<>();
        if (pm25 > 0) subIndices.put("pm25", calcPm25(pm25));
        if (pm10 > 0) subIndices.put("pm10", calcPm10(pm10));
        if (no2  > 0) subIndices.put("no2",  calcNo2(no2));
        if (o3   > 0) subIndices.put("o3",   calcO3(o3));
        if (co   > 0) subIndices.put("co",   calcCo(co));
        if (so2  > 0) subIndices.put("so2",  calcSo2(so2));

        int cpcbAqi = subIndices.isEmpty() ? 0
                : (int) Math.round(Collections.max(subIndices.values()));

        System.out.println("=== CPCB Sub-indices: " + subIndices);
        System.out.println("=== Final CPCB AQI: " + cpcbAqi);

        // ── Build response ────────────────────────────────────────
        Map<String, Object> result = new HashMap<>();
        result.put("aqi",         cpcbAqi);
        result.put("city",        cityName);
        result.put("pm25",        pm25);
        result.put("pm10",        pm10);
        result.put("no2",         no2);
        result.put("o3",          o3);
        result.put("co",          co);
        result.put("so2",         so2);
        result.put("temperature", temp);
        result.put("humidity",    humidity);
        result.put("windSpeed",   windSpeed);
        result.put("subIndices",  subIndices);
        result.put("standard",    "CPCB India");
        return result;
    }

    // ── CPCB India AQI Calculations (real µg/m³ inputs) ──────────

    private double calcPm25(double cp) {
        double[][] t = {{0,30,0,50},{30,60,51,100},{60,90,101,200},
                {90,120,201,300},{120,250,301,400},{250,500,401,500}};
        return interpolate(cp, t);
    }
    private double calcPm10(double cp) {
        double[][] t = {{0,50,0,50},{50,100,51,100},{100,250,101,200},
                {250,350,201,300},{350,430,301,400},{430,600,401,500}};
        return interpolate(cp, t);
    }
    private double calcNo2(double cp) {
        double[][] t = {{0,40,0,50},{40,80,51,100},{80,180,101,200},
                {180,280,201,300},{280,400,301,400},{400,800,401,500}};
        return interpolate(cp, t);
    }
    private double calcO3(double cp) {
        double[][] t = {{0,50,0,50},{50,100,51,100},{100,168,101,200},
                {168,208,201,300},{208,748,301,400},{748,1000,401,500}};
        return interpolate(cp, t);
    }
    private double calcCo(double cp) {
        cp = cp / 1000.0; // µg/m³ → mg/m³
        double[][] t = {{0,1.0,0,50},{1.0,2.0,51,100},{2.0,10,101,200},
                {10,17,201,300},{17,34,301,400},{34,50,401,500}};
        return interpolate(cp, t);
    }
    private double calcSo2(double cp) {
        double[][] t = {{0,40,0,50},{40,80,51,100},{80,380,101,200},
                {380,800,201,300},{800,1600,301,400},{1600,2100,401,500}};
        return interpolate(cp, t);
    }
    private double interpolate(double cp, double[][] table) {
        for (double[] row : table) {
            if (cp <= row[1])
                return ((row[3]-row[2])/(row[1]-row[0])) * (cp-row[0]) + row[2];
        }
        return 500;
    }
}