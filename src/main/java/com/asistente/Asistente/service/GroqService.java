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
            
            // System Prompt para que la IA sepa qué hacer
            String systemPrompt = "Eres un asistente de gestión de tareas. Tu objetivo es extraer información de mensajes en lenguaje natural. " +
                    "Debes responder ÚNICAMENTE con un objeto JSON que tenga los campos: " +
                    "'accion' (puede ser: CREAR, LISTAR, COMPLETAR, DESCONOCIDO), " +
                    "'descripcion', 'area', 'responsable'. " +
                    "Si el usuario dice de crear algo, usa CREAR. Si pide ver tareas, usa LISTAR. " +
                    "Si no entiendes, usa DESCONOCIDO.";

            Map<String, Object> body = Map.of(
                    "model", "llama3-8b-8192",
                    "messages", new Object[]{
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", mensajeUsuario)
                    },
                    "response_format", Map.of("type", "json_object")
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("choices").get(0).path("message").path("content").asText();

        } catch (Exception e) {
            e.printStackTrace();
            return "{\"accion\":\"ERROR\"}";
        }
    }
}
