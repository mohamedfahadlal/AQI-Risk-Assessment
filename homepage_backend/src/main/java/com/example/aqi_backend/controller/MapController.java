package com.example.aqi_backend.controller;

import com.example.aqi_backend.service.AqiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MapController {

    private final AqiService aqiService;

    public MapController(AqiService aqiService) {
        this.aqiService = aqiService;
    }

    private static final Object[][] CITIES = {
        // North India
        {28.6139, 77.2090, "Delhi"},
        {26.8467, 80.9462, "Lucknow"},
        {25.3176, 82.9739, "Varanasi"},
        {26.9124, 75.7873, "Jaipur"},
        {30.7333, 76.7794, "Chandigarh"},
        {27.1767, 78.0081, "Agra"},
        // West India
        {19.0760, 72.8777, "Mumbai"},
        {23.0225, 72.5714, "Ahmedabad"},
        {18.5204, 73.8567, "Pune"},
        {21.1458, 79.0882, "Nagpur"},
        // South India
        {13.0827, 80.2707, "Chennai"},
        {12.9716, 77.5946, "Bengaluru"},
        {17.3850, 78.4867, "Hyderabad"},
        {11.0168, 76.9558, "Coimbatore"},
        // Kerala
        {9.9312,  76.2673, "Kochi"},
        {8.5241,  76.9366, "Thiruvananthapuram"},
        {11.2588, 75.7804, "Kozhikode"},
        {8.8932,  76.6141, "Kollam"},
        {10.5276, 76.2144, "Thrissur"},
        {11.8745, 75.3704, "Kannur"},
        // East India
        {22.5726, 88.3639, "Kolkata"},
        {20.2961, 85.8245, "Bhubaneswar"},
    };

    @GetMapping("/aqi/map")
    public ResponseEntity<?> getMapData() {
        List<Map<String, Object>> cityData = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(6);
        List<Future<?>> futures = new ArrayList<>();

        for (Object[] cityInfo : CITIES) {
            futures.add(executor.submit(() -> {
                try {
                    double lat  = (double) cityInfo[0];
                    double lon  = (double) cityInfo[1];
                    String name = (String) cityInfo[2];

                    Map<String, Object> data = aqiService.getAqiByCoords(lat, lon);

                    Map<String, Object> city = new HashMap<>();
                    city.put("name", name);
                    city.put("lat",  lat);
                    city.put("lon",  lon);
                    city.put("aqi",  data.get("aqi"));
                    city.put("pm25", data.get("pm25"));
                    city.put("pm10", data.get("pm10"));
                    city.put("temp", data.get("temperature"));
                    cityData.add(city);
                    System.out.println("Loaded: " + cityInfo[2] + " AQI=" + data.get("aqi"));
                } catch (Exception e) {
                    System.out.println("Failed: " + cityInfo[2] + " - " + e.getMessage());
                }
            }));
        }

        for (Future<?> f : futures) {
            try { f.get(15, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        executor.shutdown();

        System.out.println("Map data loaded: " + cityData.size() + " cities");
        return ResponseEntity.ok(cityData);
    }
}
