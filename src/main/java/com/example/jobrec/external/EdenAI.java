package com.example.jobrec.external;

import com.example.jobrec.entity.ExtractRequestBody;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.io.IOException;

public class EdenAI {
    private static final String EDENAI_TOEKN = "Bearer " + "YOUR_API_KEY";

    private static final String EXTRACT_URL = "https://api.edenai.run/v2/text/keyword_extraction";

    public static void main(String[] args) {
        String s = "Artificial Intelligence (AI) is revolutionizing various industries, from healthcare to finance. AI-powered systems are capable of performing tasks that traditionally required human intelligence, such as visual perception, speech recognition, decision-making, and language translation. \n\ In healthcare, AI is being used to improve diagnostic accuracy and personalize treatment plans. For instance, machine learning algorithms can analyze medical images to detect diseases like cancer at an early stage, significantly improving patient outcomes. AI also plays a crucial role in drug discovery, accelerating the process of identifying potential new medications. \n\ The finance sector is leveraging AI for fraud detection, algorithmic trading, and risk management. AI systems can process vast amounts of data in real-time to identify suspicious transactions and prevent fraud. Additionally, AI-driven trading algorithms can analyze market trends and execute trades at optimal times, maximizing returns for investors.";

        EdenAI client = new EdenAI();

        Set<String> keywordSet = client.extract(s, 3);
        System.out.println(keywordSet);
    }

    public Set<String> extract(String article, int keywords_num) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        ObjectMapper mapper = new ObjectMapper();

        HttpPost request = new HttpPost(EXTRACT_URL); //create POST request
        request.setHeader("Content-type", "application/json");
        request.setHeader("Authorization", EDENAI_TOEKN);
        request.setHeader("accept", "application/json");
        ExtractRequestBody body = new ExtractRequestBody(article); //pass article string to an object

//        System.out.println("POST Created");

        String jsonBody;
        try {
            jsonBody = mapper.writeValueAsString(body); //write into request body into json
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return Collections.emptySet();
        }

//        System.out.println("Reqeust Body Created");

        try {
            request.setEntity(new StringEntity(jsonBody)); //write json to request body
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return Collections.emptySet();
        }

//        System.out.println("Request Body Written");

        ResponseHandler<Set<String>> responseHandler = response -> {
            if (response.getStatusLine().getStatusCode() != 200) { //If not 200OK return empty set
                return Collections.emptySet();
            }
            HttpEntity entity = response.getEntity(); //initiate entity, if entity failed return empty set
            if (entity == null) {
                return Collections.emptySet();
            }
            JsonNode root = mapper.readTree(entity.getContent()); //get content till nlpcloud's items
            JsonNode ibm = root.get("ibm");

            System.out.println(ibm.asText());

            JsonNode ibmitems = ibm.get("items");

            TreeMap<Double, ArrayList<String>> keywords = new TreeMap<>();
            Iterator<JsonNode> itemsIterator = ibmitems.elements();
            //Read Value and store in keywords set
            while (itemsIterator.hasNext()) {
                JsonNode itemNode = itemsIterator.next();
                String keyword = itemNode.get("keyword").asText();
                double importance = itemNode.get("importance").asDouble();
                ArrayList<String> words_list = keywords.getOrDefault(importance, new ArrayList<String>());
                words_list.add(keyword);
                keywords.put(importance, words_list);
            }

            Set<String> refined_set = new HashSet<>();

            while (refined_set.size() < keywords_num && !keywords.isEmpty()) {
                ArrayList<String> words_list = keywords.pollLastEntry().getValue();
                while (!words_list.isEmpty() && refined_set.size() < keywords_num) {
                    refined_set.add(words_list.remove(0));
                }
            }

            return refined_set;
        };

//        System.out.println("Response Handler Created");

        try {
            return httpClient.execute(request, responseHandler); //handle request sent
        } catch (IOException e) {
            e.printStackTrace();
        }

//        System.out.println("HTTP Failed");

        return Collections.emptySet();
    }
}
