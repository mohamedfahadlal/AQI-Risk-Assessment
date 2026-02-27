package com.example.aqi_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;

@Service
public class AqiService {

    @Value("${openaq.api.key}")
    private String openAqKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> getAqiByCity(String city) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", openAqKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Step 1: Search locations
            String locationUrl = "https://api.openaq.org/v3/locations?city=" + city + "&limit=10";
            System.out.println("=== Calling: " + locationUrl);

            ResponseEntity<String> locResponse = restTemplate.exchange(
                    locationUrl, HttpMethod.GET, entity, String.class);

            System.out.println("=== Location response: " + locResponse.getBody());

            JsonNode locRoot = objectMapper.readTree(locResponse.getBody());
            JsonNode results = locRoot.path("results");

            if (results.isEmpty()) {
                throw new RuntimeException("No monitoring stations found for: " + city);
            }

            // Pick station with most sensors
            JsonNode bestStation = null;
            int maxSensors = -1;
            for (JsonNode station : results) {
                int sensorCount = station.path("sensors").size();
                System.out.println("Station: " + station.path("name").asText() + " sensors: " + sensorCount);
                if (sensorCount > maxSensors) {
                    maxSensors = sensorCount;
                    bestStation = station;
                }
            }

            int locationId = bestStation.path("id").asInt();
            JsonNode sensors = bestStation.path("sensors");
            System.out.println("=== Best station ID: " + locationId + " with " + sensors.size() + " sensors");

            // Step 2: Fetch each sensor
            Map<String, Double> pollutants = new HashMap<>();
            for (JsonNode sensor : sensors) {
                int sensorId = sensor.path("id").asInt();
                String paramName = sensor.path("parameter").path("name").asText().toLowerCase();
                System.out.println("--- Sensor " + sensorId + " param: " + paramName);

                String sensorUrl = "https://api.openaq.org/v3/sensors/" + sensorId + "/hours/recent";
                try {
                    ResponseEntity<String> sensorResponse = restTemplate.exchange(
                            sensorUrl, HttpMethod.GET, entity, String.class);
                    System.out.println("--- Response: " + sensorResponse.getBody());

                    JsonNode sensorData = objectMapper.readTree(sensorResponse.getBody());
                    JsonNode sensorResults = sensorData.path("results");

                    if (!sensorResults.isEmpty()) {
                        double value = sensorResults.get(0).path("value").asDouble(-1);
                        System.out.println("--- Value: " + value);
                        if (value >= 0) pollutants.put(paramName, value);
                    } else {
                        System.out.println("--- Empty results for sensor " + sensorId);
                    }
                } catch (Exception e) {
                    System.out.println("--- ERROR sensor " + sensorId + ": " + e.getMessage());
                }
            }

            System.out.println("=== Pollutants: " + pollutants);

            // Step 3: Calculate CPCB AQI
            Map<String, Double> subIndices = new HashMap<>();
            if (pollutants.containsKey("pm25")) subIndices.put("pm25", calcPm25(pollutants.get("pm25")));
            if (pollutants.containsKey("pm10")) subIndices.put("pm10", calcPm10(pollutants.get("pm10")));
            if (pollutants.containsKey("no2"))  subIndices.put("no2",  calcNo2(pollutants.get("no2")));
            if (pollutants.containsKey("o3"))   subIndices.put("o3",   calcO3(pollutants.get("o3")));
            if (pollutants.containsKey("co"))   subIndices.put("co",   calcCo(pollutants.get("co")));
            if (pollutants.containsKey("so2"))  subIndices.put("so2",  calcSo2(pollutants.get("so2")));

            int aqi = subIndices.isEmpty() ? 0 : (int) Math.round(Collections.max(subIndices.values()));

            Map<String, Object> result = new HashMap<>();
            result.put("aqi",         aqi);
            result.put("city",        city);
            result.put("pm25",        pollutants.getOrDefault("pm25", 0.0));
            result.put("pm10",        pollutants.getOrDefault("pm10", 0.0));
            result.put("no2",         pollutants.getOrDefault("no2",  0.0));
            result.put("o3",          pollutants.getOrDefault("o3",   0.0));
            result.put("co",          pollutants.getOrDefault("co",   0.0));
            result.put("so2",         pollutants.getOrDefault("so2",  0.0));
            result.put("temperature", pollutants.getOrDefault("temperature", 0.0));
            result.put("humidity",    pollutants.getOrDefault("relativehumidity", 0.0));
            result.put("windSpeed",   pollutants.getOrDefault("windspeed", 0.0));
            result.put("subIndices",  subIndices);
            result.put("standard",    "CPCB India");
            result.put("stationId",   locationId);
            return result;

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to fetch AQI: " + e.getMessage());
        }
    }

    private double calcPm25(double cp) {
        double[][] t = {{0,30,0,50},{30,60,51,100},{60,90,101,200},{90,120,201,300},{120,250,301,400},{250,500,401,500}};
        return linearInterpolate(cp, t);
    }
    private double calcPm10(double cp) {
        double[][] t = {{0,50,0,50},{50,100,51,100},{100,250,101,200},{250,350,201,300},{350,430,301,400},{430,600,401,500}};
        return linearInterpolate(cp, t);
    }
    private double calcNo2(double cp) {
        double[][] t = {{0,40,0,50},{40,80,51,100},{80,180,101,200},{180,280,201,300},{280,400,301,400},{400,800,401,500}};
        return linearInterpolate(cp, t);
    }
    private double calcO3(double cp) {
        double[][] t = {{0,50,0,50},{50,100,51,100},{100,168,101,200},{168,208,201,300},{208,748,301,400},{748,1000,401,500}};
        return linearInterpolate(cp, t);
    }
    private double calcCo(double cp) {
        cp = cp / 1000.0;
        double[][] t = {{0,1.0,0,50},{1.0,2.0,51,100},{2.0,10.0,101,200},{10,17,201,300},{17,34,301,400},{34,50,401,500}};
        return linearInterpolate(cp, t);
    }
    private double calcSo2(double cp) {
        double[][] t = {{0,40,0,50},{40,80,51,100},{80,380,101,200},{380,800,201,300},{800,1600,301,400},{1600,2100,401,500}};
        return linearInterpolate(cp, t);
    }
    private double linearInterpolate(double cp, double[][] table) {
        for (double[] row : table) {
            if (cp <= row[1]) return ((row[3]-row[2])/(row[1]-row[0]))*(cp-row[0])+row[2];
        }
        return 500;
    }
}
