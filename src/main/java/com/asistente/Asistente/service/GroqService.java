package com.asistente.Asistente.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Service
public class GroqService {

    @Value("${groq.api.key}")
    private String apiKey;

    private final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String procesarMensaje(String mensajeUsuario) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            
            String systemPrompt = "Extract task info. Respond ONLY with JSON format: " +
                    "{\"accion\": \"CREAR\"|\"LISTAR\"|\"DESCONOCIDO\", \"descripcion\": \"text\", \"area\": \"text\", \"responsable\": \"text\"}. " +
                    "If user wants to add/create: CREAR. If user wants to see/list: LISTAR. Else: DESCONOCIDO.";

            Map<String, Object> body = Map.of(
                    "model", "llama3-8b-8192",
                    "messages", new Object[]{
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", mensajeUsuario)
                    },
                    "temperature", 0.0,
                    "response_format", Map.of("type", "json_object")
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            // LOGS PARA DIAGNÓSTICO
            System.out.println("GROQ_RAW_RESPONSE: " + response.body());

            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("choices")) {
                return root.path("choices").get(0).path("message").path("content").asText();
            }
            return "{\"accion\":\"DESCONOCIDO\"}";

        } catch (Exception e) {
            System.err.println("GROQ_ERROR: " + e.getMessage());
            return "{\"accion\":\"DESCONOCIDO\"}";
        }
    }
}
