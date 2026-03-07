package com.example.aqi_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;

@Service
public class AqiService {

    @Value("${owm.api.key}")
    private String owmKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── India cities list ─────────────────────────────────────────
    private static final List<String> INDIA_CITIES = List.of(
            "Delhi", "Mumbai", "Kolkata", "Chennai",
            "Bengaluru", "Hyderabad", "Ahmedabad", "Kochi"
    );

    // ── Search by city name ───────────────────────────────────────
    public Map<String, Object> getAqiByCity(String city) {
        try {
            String geoUrl = "https://api.openweathermap.org/geo/1.0/direct?q="
                    + city.replace(" ", "+") + ",IN&limit=1&appid=" + owmKey;
            String geoResponse = restTemplate.getForObject(geoUrl, String.class);
            JsonNode geoRoot = objectMapper.readTree(geoResponse);

            if (!geoRoot.isArray() || geoRoot.size() == 0) {
                geoUrl = "https://api.openweathermap.org/geo/1.0/direct?q="
                        + city.replace(" ", "+") + "&limit=1&appid=" + owmKey;
                geoResponse = restTemplate.getForObject(geoUrl, String.class);
                geoRoot = objectMapper.readTree(geoResponse);
                if (!geoRoot.isArray() || geoRoot.size() == 0)
                    throw new RuntimeException("City not found: " + city);
            }

            double lat = geoRoot.get(0).path("lat").asDouble();
            double lon = geoRoot.get(0).path("lon").asDouble();
            String resolvedCity = geoRoot.get(0).path("name").asText(city);

            return fetchAllData(lat, lon, resolvedCity);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to fetch data: " + e.getMessage());
        }
    }

    // ── Search by coordinates ─────────────────────────────────────
    public Map<String, Object> getAqiByCoords(double lat, double lon) {
        try {
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

    // ── Fetch weather + air pollution ─────────────────────────────
    private Map<String, Object> fetchAllData(double lat, double lon, String cityName) throws Exception {

        // Weather
        String weatherUrl = "https://api.openweathermap.org/data/2.5/weather?lat="
                + lat + "&lon=" + lon + "&units=metric&appid=" + owmKey;
        String weatherResponse = restTemplate.getForObject(weatherUrl, String.class);
        JsonNode weatherRoot = objectMapper.readTree(weatherResponse);

        double temp        = weatherRoot.path("main").path("temp").asDouble();
        double feelsLike   = weatherRoot.path("main").path("feels_like").asDouble();
        double tempMin     = weatherRoot.path("main").path("temp_min").asDouble();
        double tempMax     = weatherRoot.path("main").path("temp_max").asDouble();
        double humidity    = weatherRoot.path("main").path("humidity").asDouble();
        double pressure    = weatherRoot.path("main").path("pressure").asDouble();
        double windSpeed   = weatherRoot.path("wind").path("speed").asDouble() * 3.6;
        double windDeg     = weatherRoot.path("wind").path("deg").asDouble();
        double visibility  = weatherRoot.path("visibility").asDouble() / 1000.0; // m → km
        double clouds      = weatherRoot.path("clouds").path("all").asDouble();
        long sunrise       = weatherRoot.path("sys").path("sunrise").asLong();
        long sunset        = weatherRoot.path("sys").path("sunset").asLong();
        String description = weatherRoot.path("weather").get(0).path("description").asText();
        String icon        = weatherRoot.path("weather").get(0).path("icon").asText();
        double windDirection = weatherRoot.path("wind").path("deg").asDouble(180);
        // Air Pollution
        String pollutionUrl = "https://api.openweathermap.org/data/2.5/air_pollution?lat="
                + lat + "&lon=" + lon + "&appid=" + owmKey;
        String pollutionResponse = restTemplate.getForObject(pollutionUrl, String.class);
        JsonNode pollutionRoot = objectMapper.readTree(pollutionResponse);
        JsonNode components = pollutionRoot.path("list").get(0).path("components");

        double pm25 = components.path("pm2_5").asDouble();
        double pm10 = components.path("pm10").asDouble();
        double no2  = components.path("no2").asDouble();
        double o3   = components.path("o3").asDouble();
        double co   = components.path("co").asDouble();
        double so2  = components.path("so2").asDouble();
        double nh3  = components.path("nh3").asDouble();
        double no   = components.path("no").asDouble();

        // CPCB AQI
        Map<String, Double> subIndices = new HashMap<>();
        if (pm25 > 0) subIndices.put("pm25", calcPm25(pm25));
        if (pm10 > 0) subIndices.put("pm10", calcPm10(pm10));
        if (no2  > 0) subIndices.put("no2",  calcNo2(no2));
        if (o3   > 0) subIndices.put("o3",   calcO3(o3));
        if (co   > 0) subIndices.put("co",   calcCo(co));
        if (so2  > 0) subIndices.put("so2",  calcSo2(so2));

        int cpcbAqi = subIndices.isEmpty() ? 0
                : (int) Math.round(Collections.max(subIndices.values()));

        // Build response
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("aqi",         cpcbAqi);
        result.put("city",        cityName);
        // Pollutants
        result.put("pm25",        pm25);
        result.put("pm10",        pm10);
        result.put("no2",         no2);
        result.put("o3",          o3);
        result.put("co",          co);
        result.put("so2",         so2);
        result.put("nh3",         nh3);
        result.put("no",          no);
        // Weather
        result.put("temperature", temp);
        result.put("feelsLike",   feelsLike);
        result.put("tempMin",     tempMin);
        result.put("tempMax",     tempMax);
        result.put("humidity",    humidity);
        result.put("pressure",    pressure);
        result.put("windSpeed",   windSpeed);
        result.put("windDeg",     windDeg);
        result.put("windDir",     degToCompass(windDeg));
        result.put("visibility",  visibility);
        result.put("clouds",      clouds);
        result.put("sunrise",     sunrise);
        result.put("sunset",      sunset);
        result.put("description", description);
        result.put("icon",        icon);
        result.put("subIndices",  subIndices);
        result.put("standard",    "CPCB India");
        return result;
    }

    // ── NEW: Forecast (5-day / 3-hour) ────────────────────────────
    public Map<String, Object> getForecast(String city) {
        try {
            // Geocode
            String geoUrl = "https://api.openweathermap.org/geo/1.0/direct?q="
                    + city.replace(" ", "+") + "&limit=1&appid=" + owmKey;
            JsonNode geoRoot = objectMapper.readTree(
                    restTemplate.getForObject(geoUrl, String.class));

            if (!geoRoot.isArray() || geoRoot.size() == 0)
                throw new RuntimeException("City not found: " + city);

            double lat = geoRoot.get(0).path("lat").asDouble();
            double lon = geoRoot.get(0).path("lon").asDouble();
            String cityName = geoRoot.get(0).path("name").asText(city);

            return fetchForecastData(lat, lon, cityName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch forecast: " + e.getMessage());
        }
    }

    public Map<String, Object> getForecastByCoords(double lat, double lon) {
        try {
            String reverseUrl = "https://api.openweathermap.org/geo/1.0/reverse?lat="
                    + lat + "&lon=" + lon + "&limit=1&appid=" + owmKey;
            JsonNode reverseRoot = objectMapper.readTree(
                    restTemplate.getForObject(reverseUrl, String.class));
            String cityName = (reverseRoot.isArray() && reverseRoot.size() > 0)
                    ? reverseRoot.get(0).path("name").asText("Your Location")
                    : "Your Location";
            return fetchForecastData(lat, lon, cityName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch forecast: " + e.getMessage());
        }
    }

    private Map<String, Object> fetchForecastData(double lat, double lon, String cityName) throws Exception {
        // 5-day weather forecast (3-hour intervals)
        String forecastUrl = "https://api.openweathermap.org/data/2.5/forecast?lat="
                + lat + "&lon=" + lon + "&units=metric&appid=" + owmKey;
        JsonNode forecastRoot = objectMapper.readTree(
                restTemplate.getForObject(forecastUrl, String.class));

        // Air pollution forecast (hourly, 4 days ahead)
        String pollForecastUrl = "https://api.openweathermap.org/data/2.5/air_pollution/forecast?lat="
                + lat + "&lon=" + lon + "&appid=" + owmKey;
        JsonNode pollForecastRoot = objectMapper.readTree(
                restTemplate.getForObject(pollForecastUrl, String.class));

        // Build a map of timestamp → AQI for pollution
        Map<Long, Integer> pollutionByTime = new LinkedHashMap<>();
        for (JsonNode item : pollForecastRoot.path("list")) {
            long dt = item.path("dt").asLong();
            JsonNode comp = item.path("components");
            double pm25 = comp.path("pm2_5").asDouble();
            double pm10 = comp.path("pm10").asDouble();
            double no2  = comp.path("no2").asDouble();
            double o3   = comp.path("o3").asDouble();
            double co   = comp.path("co").asDouble();
            double so2  = comp.path("so2").asDouble();

            Map<String, Double> sub = new HashMap<>();
            if (pm25 > 0) sub.put("pm25", calcPm25(pm25));
            if (pm10 > 0) sub.put("pm10", calcPm10(pm10));
            if (no2  > 0) sub.put("no2",  calcNo2(no2));
            if (o3   > 0) sub.put("o3",   calcO3(o3));
            if (co   > 0) sub.put("co",   calcCo(co));
            if (so2  > 0) sub.put("so2",  calcSo2(so2));

            int aqi = sub.isEmpty() ? 0 : (int) Math.round(Collections.max(sub.values()));
            pollutionByTime.put(dt, aqi);
        }

        // Build forecast entries
        List<Map<String, Object>> entries = new ArrayList<>();
        for (JsonNode item : forecastRoot.path("list")) {
            long dt       = item.path("dt").asLong();
            double temp   = item.path("main").path("temp").asDouble();
            double humidity = item.path("main").path("humidity").asDouble();
            String desc   = item.path("weather").get(0).path("description").asText();
            String icon   = item.path("weather").get(0).path("icon").asText();
            String dtTxt  = item.path("dt_txt").asText();

            // Find closest pollution timestamp
            int aqi = findClosestAqi(dt, pollutionByTime);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("dt",       dt);
            entry.put("dtTxt",    dtTxt);
            entry.put("temp",     temp);
            entry.put("humidity", humidity);
            entry.put("desc",     desc);
            entry.put("icon",     icon);
            entry.put("aqi",      aqi);
            entries.add(entry);
        }

        // Daily summary (min/max AQI per day)
        Map<String, Map<String, Object>> dailySummary = new LinkedHashMap<>();
        for (Map<String, Object> entry : entries) {
            String day = ((String) entry.get("dtTxt")).substring(0, 10);
            int aqi    = (int) entry.get("aqi");
            double temp = (double) entry.get("temp");

            dailySummary.computeIfAbsent(day, k -> {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("minAqi",  999); d.put("maxAqi",  0);
                d.put("minTemp", 999.0); d.put("maxTemp", -999.0);
                return d;
            });

            Map<String, Object> day_data = dailySummary.get(day);
            if (aqi  < (int)   day_data.get("minAqi"))  day_data.put("minAqi",  aqi);
            if (aqi  > (int)   day_data.get("maxAqi"))  day_data.put("maxAqi",  aqi);
            if (temp < (double)day_data.get("minTemp")) day_data.put("minTemp", temp);
            if (temp > (double)day_data.get("maxTemp")) day_data.put("maxTemp", temp);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city",         cityName);
        result.put("entries",      entries);
        result.put("dailySummary", dailySummary);
        return result;
    }

    private int findClosestAqi(long dt, Map<Long, Integer> pollutionByTime) {
        long closest = -1;
        long minDiff = Long.MAX_VALUE;
        for (long ts : pollutionByTime.keySet()) {
            long diff = Math.abs(ts - dt);
            if (diff < minDiff) { minDiff = diff; closest = ts; }
        }
        return closest == -1 ? 0 : pollutionByTime.get(closest);
    }

    // ── NEW: All pollutants for a city ────────────────────────────
    public Map<String, Object> getPollutants(String city) {
        try {
            String geoUrl = "https://api.openweathermap.org/geo/1.0/direct?q="
                    + city.replace(" ", "+") + "&limit=1&appid=" + owmKey;
            JsonNode geoRoot = objectMapper.readTree(
                    restTemplate.getForObject(geoUrl, String.class));

            if (!geoRoot.isArray() || geoRoot.size() == 0)
                throw new RuntimeException("City not found: " + city);

            double lat = geoRoot.get(0).path("lat").asDouble();
            double lon = geoRoot.get(0).path("lon").asDouble();

            String pollutionUrl = "https://api.openweathermap.org/data/2.5/air_pollution?lat="
                    + lat + "&lon=" + lon + "&appid=" + owmKey;
            JsonNode pollRoot = objectMapper.readTree(
                    restTemplate.getForObject(pollutionUrl, String.class));
            JsonNode comp = pollRoot.path("list").get(0).path("components");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("city", geoRoot.get(0).path("name").asText(city));
            result.put("pm25", comp.path("pm2_5").asDouble());
            result.put("pm10", comp.path("pm10").asDouble());
            result.put("no2",  comp.path("no2").asDouble());
            result.put("o3",   comp.path("o3").asDouble());
            result.put("co",   comp.path("co").asDouble());
            result.put("so2",  comp.path("so2").asDouble());
            result.put("nh3",  comp.path("nh3").asDouble());
            result.put("no",   comp.path("no").asDouble());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch pollutants: " + e.getMessage());
        }
    }

    // ── NEW: India cities AQI (parallel fetch) ────────────────────
    public List<Map<String, Object>> getIndiaCities() {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();

        for (String city : INDIA_CITIES) {
            futures.add(executor.submit(() -> {
                try {
                    Map<String, Object> data = getAqiByCity(city);
                    // Keep only what we need for city cards
                    Map<String, Object> card = new LinkedHashMap<>();
                    card.put("city",  data.get("city"));
                    card.put("aqi",   data.get("aqi"));
                    card.put("pm25",  data.get("pm25"));
                    card.put("pm10",  data.get("pm10"));
                    card.put("temp",  data.get("temperature"));
                    return card;
                } catch (Exception e) {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("city",  city);
                    err.put("aqi",   -1);
                    err.put("error", e.getMessage());
                    return err;
                }
            }));
        }

        executor.shutdown();
        List<Map<String, Object>> results = new ArrayList<>();
        for (Future<Map<String, Object>> f : futures) {
            try { results.add(f.get(10, TimeUnit.SECONDS)); }
            catch (Exception e) { /* skip failed city */ }
        }

        // Sort by AQI descending (worst first)
        results.sort((a, b) -> {
            int aqiA = (int) a.getOrDefault("aqi", -1);
            int aqiB = (int) b.getOrDefault("aqi", -1);
            return Integer.compare(aqiB, aqiA);
        });

        return results;
    }

    // ── City search suggestions ───────────────────────────────────
    public List<Map<String, Object>> searchCities(String query) {
        try {
            String url = "https://api.openweathermap.org/geo/1.0/direct?q="
                    + query.replace(" ", "+") + "&limit=5&appid=" + owmKey;
            JsonNode root = objectMapper.readTree(restTemplate.getForObject(url, String.class));

            List<Map<String, Object>> suggestions = new ArrayList<>();
            for (JsonNode node : root) {
                String name    = node.path("name").asText();
                String country = node.path("country").asText();
                String state   = node.path("state").asText("");
                double lat     = node.path("lat").asDouble();
                double lon     = node.path("lon").asDouble();

                String display = name
                        + (state.isEmpty() ? "" : ", " + state)
                        + ", " + country;

                Map<String, Object> s = new LinkedHashMap<>();
                s.put("display", display);
                s.put("city",    name);
                s.put("lat",     lat);
                s.put("lon",     lon);
                suggestions.add(s);
            }
            return suggestions;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ── Wind direction helper ─────────────────────────────────────
    private String degToCompass(double deg) {
        String[] dirs = {"N","NE","E","SE","S","SW","W","NW"};
        return dirs[(int)Math.round(deg / 45) % 8];
    }

    // ── CPCB AQI Calculations ─────────────────────────────────────
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
        cp = cp / 1000.0;
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
