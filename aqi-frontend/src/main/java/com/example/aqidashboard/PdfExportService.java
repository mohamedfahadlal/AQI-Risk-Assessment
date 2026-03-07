package com.example.aqidashboard;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PdfExportService {

    public static void exportReport(JsonNode data, String city) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            float width  = page.getMediaBox().getWidth();
            float margin = 50;
            float y      = page.getMediaBox().getHeight() - 50;

            PDFont bold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont oblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                // Header bar
                cs.setNonStrokingColor(0.10f, 0.45f, 0.91f);
                cs.addRect(0, y - 10, width, 60);
                cs.fill();

                cs.setNonStrokingColor(1f, 1f, 1f);
                cs.beginText();
                cs.setFont(bold, 22);
                cs.newLineAtOffset(margin, y + 22);
                cs.showText("AiQI  Air Quality Report");
                cs.endText();

                y -= 20;

                cs.setNonStrokingColor(0.5f, 0.5f, 0.5f);
                cs.beginText();
                cs.setFont(regular, 10);
                cs.newLineAtOffset(margin, y);
                String ts = new SimpleDateFormat("dd MMM yyyy, h:mm a").format(new Date());
                cs.showText("Generated: " + ts + "   |   Location: " + city);
                cs.endText();

                y -= 35;

                // AQI Overview
                int aqi      = data.path("aqi").asInt();
                String level = getAqiLevel(aqi);
                float[] col  = aqiColor(aqi);

                drawSectionHeader(cs, bold, "AQI Overview", margin, y, width);
                y -= 30;

                cs.setNonStrokingColor(col[0], col[1], col[2]);
                cs.beginText();
                cs.setFont(bold, 38);
                cs.newLineAtOffset(margin, y);
                cs.showText(String.valueOf(aqi));
                cs.endText();

                cs.beginText();
                cs.setFont(bold, 16);
                cs.newLineAtOffset(margin + 80, y + 8);
                cs.showText(level);
                cs.endText();

                y -= 50;

                // Pollutants
                drawSectionHeader(cs, bold, "Pollutant Concentrations", margin, y, width);
                y -= 25;

                String[][] pollutants = {
                        {"PM2.5", String.format("%.1f ug/m3", data.path("pm25").asDouble())},
                        {"PM10",  String.format("%.1f ug/m3", data.path("pm10").asDouble())},
                        {"NO2",   String.format("%.1f ug/m3", data.path("no2").asDouble())},
                        {"O3",    String.format("%.1f ug/m3", data.path("o3").asDouble())},
                        {"SO2",   String.format("%.1f ug/m3", data.path("so2").asDouble())},
                        {"CO",    String.format("%.1f ug/m3", data.path("co").asDouble())},
                        {"NH3",   String.format("%.1f ug/m3", data.path("nh3").asDouble())},
                        {"NO",    String.format("%.1f ug/m3", data.path("no").asDouble())}
                };

                int c = 0;
                for (String[] p : pollutants) {
                    float px = margin + c * 240;
                    cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
                    cs.beginText(); cs.setFont(regular, 10); cs.newLineAtOffset(px, y); cs.showText(p[0]); cs.endText();
                    cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
                    cs.beginText(); cs.setFont(bold, 13); cs.newLineAtOffset(px, y - 14); cs.showText(p[1]); cs.endText();
                    c++;
                    if (c >= 2) { c = 0; y -= 40; }
                }

                y -= 20;

                // Weather
                drawSectionHeader(cs, bold, "Current Weather", margin, y, width);
                y -= 25;

                String[][] weather = {
                        {"Temperature",  String.format("%.1f C", data.path("temperature").asDouble())},
                        {"Feels Like",   String.format("%.1f C", data.path("feelsLike").asDouble())},
                        {"Humidity",     String.format("%.0f%%", data.path("humidity").asDouble())},
                        {"Wind Speed",   String.format("%.1f km/h", data.path("windSpeed").asDouble())},
                        {"Pressure",     String.format("%.0f hPa", data.path("pressure").asDouble())},
                        {"Visibility",   String.format("%.1f km", data.path("visibility").asDouble())}
                };

                c = 0;
                for (String[] w : weather) {
                    float wx = margin + c * 160;
                    cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
                    cs.beginText(); cs.setFont(regular, 10); cs.newLineAtOffset(wx, y); cs.showText(w[0]); cs.endText();
                    cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
                    cs.beginText(); cs.setFont(bold, 13); cs.newLineAtOffset(wx, y - 14); cs.showText(w[1]); cs.endText();
                    c++;
                    if (c >= 3) { c = 0; y -= 40; }
                }

                y -= 30;

                // Health Recommendation
                drawSectionHeader(cs, bold, "Health Recommendation", margin, y, width);
                y -= 25;

                cs.setNonStrokingColor(col[0], col[1], col[2]);
                cs.beginText();
                cs.setFont(bold, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText(getRecommendation(aqi));
                cs.endText();

                y -= 40;

                // Footer line
                cs.setNonStrokingColor(0.8f, 0.8f, 0.8f);
                cs.addRect(margin, y, width - 2 * margin, 0.5f);
                cs.fill();

                y -= 15;
                cs.setNonStrokingColor(0.6f, 0.6f, 0.6f);
                cs.beginText();
                cs.setFont(oblique, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("AiQI - Intelligence in Every Breath  |  Data from OpenWeatherMap (CPCB India AQI standard)");
                cs.endText();
            }

            // Save to Downloads
            String fileName = "AiQI_Report_" + city.replaceAll("[^a-zA-Z0-9]", "_") + "_"
                    + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ".pdf";
            File output = new File(System.getProperty("user.home")
                    + File.separator + "Downloads" + File.separator + fileName);
            doc.save(output);
            System.out.println("PDF saved: " + output.getAbsolutePath());
        }
    }

    private static void drawSectionHeader(PDPageContentStream cs, PDFont bold,
                                          String title, float x, float y, float pageWidth) throws Exception {
        cs.setNonStrokingColor(0.94f, 0.96f, 0.99f);
        cs.addRect(x - 5, y - 5, pageWidth - 2 * x + 10, 22);
        cs.fill();
        cs.setNonStrokingColor(0.10f, 0.45f, 0.91f);
        cs.beginText();
        cs.setFont(bold, 12);
        cs.newLineAtOffset(x, y + 3);
        cs.showText(title);
        cs.endText();
    }

    private static String getAqiLevel(int aqi) {
        if (aqi <= 50)  return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Poor";
        if (aqi <= 200) return "Unhealthy";
        if (aqi <= 300) return "Severe";
        return "Hazardous";
    }

    private static float[] aqiColor(int aqi) {
        if (aqi <= 50)  return new float[]{0.18f, 0.80f, 0.44f};
        if (aqi <= 100) return new float[]{0.95f, 0.77f, 0.06f};
        if (aqi <= 150) return new float[]{0.90f, 0.49f, 0.13f};
        if (aqi <= 200) return new float[]{0.83f, 0.33f, 0.00f};
        if (aqi <= 300) return new float[]{0.56f, 0.27f, 0.68f};
        return new float[]{0.75f, 0.22f, 0.17f};
    }

    private static String getRecommendation(int aqi) {
        if (aqi <= 50)  return "Air quality is Good. Enjoy outdoor activities freely.";
        if (aqi <= 100) return "Air quality is Moderate. Sensitive groups should limit prolonged outdoor exertion.";
        if (aqi <= 150) return "Air quality is Poor. Everyone should reduce outdoor activity duration.";
        if (aqi <= 200) return "Air quality is Unhealthy. Avoid outdoor activity. Wear a mask if going outside.";
        if (aqi <= 300) return "Air quality is Severe. Stay indoors. Use air purifiers. Seek help if unwell.";
        return "Air quality is Hazardous. Emergency conditions. Do not go outdoors under any circumstance.";
    }
}
