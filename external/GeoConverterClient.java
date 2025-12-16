package com.example.jobrec.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;

public class GeoConverterClient {
    private static final String URL_TEMPLATE = "https://maps.googleapis.com/maps/api/geocode/json?latlng=%s&key=%s";

    private static final String API_KEY = "YOUR_API_KEY";

    public static void main(String[] args) {
        GeoConverterClient client = new GeoConverterClient();
        // For API test
        System.out.println(client.convert(37.334886, -122.008988));
    }

    public String convert(Double lat, Double lon) {
        String latlng = lat.toString() + "," + lon.toString();

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

            try {
                JsonNode root = mapper.readTree(entity.getContent());
                JsonNode plusCode = root.get("plus_code");
                String uule = plusCode.get("global_code").asText();
                return uule;
            } catch (UnknownError e) {
                e.printStackTrace();
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
}
