package com.example.aqi_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health-profile")
@CrossOrigin(origins = "*")
public class HealthProfileController {

    private final JdbcTemplate db;

    public HealthProfileController(JdbcTemplate db) {
        this.db = db;
    }

    /**
     * GET /api/health-profile/{userId}
     * Loads an existing health profile for pre-filling the form.
     * Returns 204 No Content if the user hasn't saved a profile yet.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getProfile(@PathVariable String userId) {
        try {
            List<Map<String, Object>> rows = db.queryForList(
                    "SELECT * FROM health_profiles WHERE user_id = ?::uuid", userId
            );

            if (rows.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            Map<String, Object> row = rows.get(0);

            // Calculate age from dob and add to response
            if (row.get("dob") != null) {
                LocalDate dob = ((java.sql.Date) row.get("dob")).toLocalDate();
                int age = Period.between(dob, LocalDate.now()).getYears();
                row.put("age", age);
            }

            // Convert PostgreSQL array to Java List for proper JSON serialization
            if (row.get("breathing_conditions") != null) {
                java.sql.Array arr = (java.sql.Array) row.get("breathing_conditions");
                row.put("breathing_conditions", java.util.Arrays.asList((Object[]) arr.getArray()));
            }

            return ResponseEntity.ok(row);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/health-profile
     * Saves or updates a health profile (UPSERT).
     *
     * Expected JSON body:
     * {
     *   "userId":               "uuid-string",
     *   "dob":                  "YYYY-MM-DD",
     *   "gender":               "Male" | "Female" | "Other",
     *   "location":             "city name",
     *   "asthmaBreathing":      "None" | "Mild" | "Moderate" | "Severe",
     *   "breathingConditions":  ["Asthma", "COPD", ...],  -- empty array if None
     *   "isSmoker":             true | false,
     *   "isAllergic":           true | false,
     *   "isPregnant":           true | false
     * }
     */
    @PostMapping
    public ResponseEntity<?> saveProfile(@RequestBody Map<String, Object> body) {
        try {
            String  userId     = (String)  body.get("userId");
            String  dob        = (String)  body.get("dob");
            String  gender     = (String)  body.get("gender");
            String  location   = (String)  body.get("location");
            String  asthma     = (String)  body.get("asthmaBreathing");
            boolean smoker     = Boolean.parseBoolean(body.getOrDefault("isSmoker",   false).toString());
            boolean allergic   = Boolean.parseBoolean(body.getOrDefault("isAllergic", false).toString());
            boolean pregnant   = Boolean.parseBoolean(body.getOrDefault("isPregnant", false).toString());

            // Convert breathingConditions list to a PostgreSQL text array string
            // e.g. ["Asthma", "COPD"] → {"Asthma","COPD"}
            String conditionsArray = "{}"; // default empty array
            if (body.get("breathingConditions") instanceof List<?> condList && !condList.isEmpty()) {
                StringBuilder sb = new StringBuilder("{");
                for (int i = 0; i < condList.size(); i++) {
                    sb.append("\"").append(condList.get(i)).append("\"");
                    if (i < condList.size() - 1) sb.append(",");
                }
                sb.append("}");
                conditionsArray = sb.toString();
            }

            // Validate required fields
            if (userId == null || dob == null || gender == null || location == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "userId, dob, gender and location are required"));
            }

            // UPSERT — insert new or update existing profile
            db.update("""
                INSERT INTO health_profiles
                    (user_id, dob, gender, location, asthma_breathing,
                     breathing_conditions, is_smoker, is_allergic, is_pregnant, updated_at)
                VALUES (?::uuid, ?::date, ?::gender_type, ?, ?::asthma_level,
                        ?::text[], ?, ?, ?, NOW())
                ON CONFLICT (user_id)
                DO UPDATE SET
                    dob                  = EXCLUDED.dob,
                    gender               = EXCLUDED.gender,
                    location             = EXCLUDED.location,
                    asthma_breathing     = EXCLUDED.asthma_breathing,
                    breathing_conditions = EXCLUDED.breathing_conditions,
                    is_smoker            = EXCLUDED.is_smoker,
                    is_allergic          = EXCLUDED.is_allergic,
                    is_pregnant          = EXCLUDED.is_pregnant,
                    updated_at           = NOW()
                """,
                    userId, dob, gender, location, asthma,
                    conditionsArray, smoker, allergic, pregnant
            );

            return ResponseEntity.ok(Map.of("message", "Profile saved successfully"));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
