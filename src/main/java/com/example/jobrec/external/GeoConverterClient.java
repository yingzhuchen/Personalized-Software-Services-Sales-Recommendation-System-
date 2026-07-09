package com.example.jobrec.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class GeoConverterClient {
    private static final String URL_TEMPLATE =
            "https://maps.googleapis.com/maps/api/geocode/json?latlng=%s&key=%s";

    private static final String API_KEY = "YOUR_API_KEY";

    public String getLocationName(Double lat, Double lon) {
        String latlng = lat + "," + lon;
        String url = String.format(URL_TEMPLATE, latlng, API_KEY);

        CloseableHttpClient httpClient = HttpClients.createDefault();
        ResponseHandler<String> responseHandler = response -> {
            if (response.getStatusLine().getStatusCode() != 200) {
                return "";
            }
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                return "";
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(entity.getContent());
            JsonNode results = root.get("results");
            if (results != null && results.isArray() && results.size() > 0) {
                return results.get(0).get("formatted_address").asText("");
            }
            return "";
        };

        try {
            return httpClient.execute(new HttpGet(url), responseHandler);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public String convert(Double lat, Double lon) {
        return getLocationName(lat, lon);
    }
}
