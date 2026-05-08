package com.asistente.Asistente.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

@Service
public class GroqService {

    @Value("${groq.api.key}")
    private String apiKey;

    private final String CHAT_URL = "https://api.groq.com/openai/v1/chat/completions";
    private final String TRANSCRIPT_URL = "https://api.groq.com/openai/v1/audio/transcriptions";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String transcribirAudio(File audioFile) {
        try {
            String boundary = "---" + UUID.randomUUID().toString();
            byte[] fileContent = Files.readAllBytes(audioFile.toPath());
            
            // Construcción manual de multipart/form-data para evitar dependencias extra
            StringBuilder bodyStart = new StringBuilder();
            bodyStart.append("--").append(boundary).append("\r\n");
            bodyStart.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
            bodyStart.append("whisper-large-v3\r\n");
            bodyStart.append("--").append(boundary).append("\r\n");
            bodyStart.append("Content-Disposition: form-data; name=\"file\"; filename=\"audio.ogg\"\r\n");
            bodyStart.append("Content-Type: audio/ogg\r\n\r\n");

            byte[] bodyEnd = ("\r\n--" + boundary + "--\r\n").getBytes();
            
            byte[] fullBody = new byte[bodyStart.toString().getBytes().length + fileContent.length + bodyEnd.length];
            System.arraycopy(bodyStart.toString().getBytes(), 0, fullBody, 0, bodyStart.toString().getBytes().length);
            System.arraycopy(fileContent, 0, fullBody, bodyStart.toString().getBytes().length, fileContent.length);
            System.arraycopy(bodyEnd, 0, fullBody, bodyStart.toString().getBytes().length + fileContent.length, bodyEnd.length);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TRANSCRIPT_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(fullBody))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("text").asText();
        } catch (Exception e) {
            System.err.println("TRANSCRIPT_ERROR: " + e.getMessage());
            return "";
        }
    }

    public String procesarMensaje(String mensajeUsuario) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String systemPrompt = "Extract task info. Respond ONLY with JSON format: " +
                    "{\"accion\": \"CREAR\"|\"LISTAR\"|\"DESCONOCIDO\", \"descripcion\": \"text\", \"area\": \"text\", \"responsable\": \"text\"}. " +
                    "If user wants to add/create: CREAR. If user wants to see/list: LISTAR. Else: DESCONOCIDO.";

            Map<String, Object> body = Map.of(
                    "model", "llama-3.1-8b-instant",
                    "messages", new Object[]{
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", mensajeUsuario)
                    },
                    "temperature", 0.0,
                    "response_format", Map.of("type", "json_object")
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CHAT_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            return "{\"accion\":\"DESCONOCIDO\"}";
        }
    }
}
