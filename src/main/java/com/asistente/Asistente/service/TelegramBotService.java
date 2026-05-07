package com.asistente.Asistente.service;

import com.asistente.Asistente.model.Pendiente;
import com.asistente.Asistente.repository.PendienteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TelegramBotService implements LongPollingSingleThreadUpdateConsumer {

    private TelegramClient telegramClient;
    private TelegramBotsLongPollingApplication botApplication;
    
    @Value("${bot.token:}")
    private String botToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private PendienteRepository pendienteRepository;

    @Autowired
    private GroqService groqService;

    @PostConstruct
    public void start() {
        if (botToken == null || botToken.isEmpty() || botToken.contains("{")) {
            System.err.println("BOT: Token no configurado. Bot desactivado.");
            return;
        }

        try {
            this.telegramClient = new OkHttpTelegramClient(botToken);
            this.botApplication = new TelegramBotsLongPollingApplication();
            this.botApplication.registerBot(botToken, this);
            System.out.println("BOT: Registrado con éxito.");
        } catch (Exception e) {
            System.err.println("BOT: Error al iniciar (posible token inválido): " + e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (botApplication != null) botApplication.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.startsWith("/")) {
                if (messageText.equals("/start")) enviarMensaje(chatId, "¡Hola! Soy tu asistente.");
                else if (messageText.equals("/listar")) enviarMensaje(chatId, obtenerListaPendientes());
            } else {
                procesarConIA(chatId, messageText);
            }
        }
    }

    private void procesarConIA(long chatId, String mensaje) {
        try {
            String respuestaIA = groqService.procesarMensaje(mensaje);
            JsonNode nodo = objectMapper.readTree(respuestaIA);
            String accion = nodo.path("accion").asText();

            if ("CREAR".equals(accion)) {
                Pendiente p = new Pendiente();
                p.setDescripcion(nodo.path("descripcion").asText());
                p.setArea(nodo.path("area").asText());
                p.setResponsable(nodo.path("responsable").asText());
                p.setEstado("Pendiente");
                pendienteRepository.save(p);
                enviarMensaje(chatId, "✅ Tarea creada: " + p.getDescripcion());
            } else {
                enviarMensaje(chatId, obtenerListaPendientes());
            }
        } catch (Exception e) {
            enviarMensaje(chatId, "Error con la IA.");
        }
    }

    private String obtenerListaPendientes() {
        List<Pendiente> pendientes = pendienteRepository.findAll();
        if (pendientes.isEmpty()) return "No hay pendientes.";
        return pendientes.stream()
                .map(p -> "- " + p.getDescripcion() + " (" + p.getResponsable() + ")")
                .collect(Collectors.joining("\n"));
    }

    private void enviarMensaje(long chatId, String texto) {
        if (telegramClient == null) return;
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(texto).build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
