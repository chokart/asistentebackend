package com.asistente.Asistente.service;

import com.asistente.Asistente.model.Pendiente;
import com.asistente.Asistente.repository.PendienteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TelegramBotService implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private TelegramClient telegramClient;
    
    @Value("${bot.token:}")
    private String botToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private PendienteRepository pendienteRepository;

    @Autowired
    private GroqService groqService;

    @PostConstruct
    public void init() {
        if (botToken != null && !botToken.isEmpty() && !botToken.equals("${TELEGRAM_BOT_TOKEN}")) {
            this.telegramClient = new OkHttpTelegramClient(botToken);
            System.out.println("Bot de Telegram inicializado correctamente.");
        } else {
            System.err.println("ADVERTENCIA: Token de Telegram no configurado. El bot estará desactivado.");
        }
    }

    @Override
    public String getBotToken() {
        return (botToken != null && !botToken.isEmpty()) ? botToken : "disabled";
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() { return this; }

    @Override
    public void consume(Update update) {
        if (telegramClient == null) return;
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.startsWith("/")) {
                manejarComandos(chatId, messageText);
            } else {
                manejarIA(chatId, messageText);
            }
        }
    }

    private void manejarComandos(long chatId, String command) {
        if (command.equals("/start")) {
            enviarMensaje(chatId, "¡Hola! Puedes hablarme para gestionar tus tareas.");
        } else if (command.equals("/listar")) {
            enviarMensaje(chatId, obtenerListaPendientes());
        }
    }

    private void manejarIA(long chatId, String mensaje) {
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
            enviarMensaje(chatId, "Error procesando con IA.");
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
        SendMessage message = SendMessage.builder().chatId(chatId).text(texto).build();
        try { telegramClient.execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
}
