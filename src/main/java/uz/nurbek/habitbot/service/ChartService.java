package uz.nurbek.habitbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a bar chart via QuickChart.io's free chart-image API.
 * We POST a Chart.js config and get PNG bytes back - no local chart library needed.
 */
@Slf4j
@Service
public class ChartService {

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://quickchart.io")
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @param title  chart title
     * @param labels x-axis labels (e.g. subtype names or dates)
     * @param values corresponding numeric totals
     */
    public byte[] buildBarChart(String title, List<String> labels, List<Double> values) {
        try {
            Map<String, Object> chartConfig = new LinkedHashMap<>();
            chartConfig.put("type", "bar");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("labels", labels);

            Map<String, Object> dataset = new LinkedHashMap<>();
            dataset.put("label", title);
            dataset.put("data", values);
            dataset.put("backgroundColor", "#4C6EF5");
            data.put("datasets", List.of(dataset));

            chartConfig.put("data", data);

            Map<String, Object> options = new LinkedHashMap<>();
            Map<String, Object> plugins = new LinkedHashMap<>();
            Map<String, Object> titleOpt = new LinkedHashMap<>();
            titleOpt.put("display", true);
            titleOpt.put("text", title);
            plugins.put("title", titleOpt);
            options.put("plugins", plugins);
            chartConfig.put("options", options);

            String chartJson = mapper.writeValueAsString(chartConfig);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chart", chartJson);
            body.put("width", 600);
            body.put("height", 400);
            body.put("backgroundColor", "white");
            body.put("format", "png");

            return webClient.post()
                    .uri("/chart")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to build chart", e);
            return null;
        }
    }
}
