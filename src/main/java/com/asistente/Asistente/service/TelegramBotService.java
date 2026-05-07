package com.asistente.Asistente.service;

import com.asistente.Asistente.model.Pendiente;
import com.asistente.Asistente.repository.PendienteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

//@Service
public class TelegramBotService implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final String botToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private PendienteRepository pendienteRepository;

    @Autowired
    private GroqService groqService;

    public TelegramBotService(@Value("${bot.token}") String botToken) {
        this.botToken = botToken;
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    @Override
    public String getBotToken() { return botToken; }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() { return this; }

    @Override
    public void consume(Update update) {
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
            enviarMensaje(chatId, "¡Hola! Puedes hablarme normalmente para gestionar tus tareas. Ejemplo: 'Crea un pendiente de soldar en el área 2000 responsable Luis'");
        } else if (command.equals("/listar")) {
            enviarMensaje(chatId, obtenerListaPendientes());
        }
    }

    private void manejarIA(long chatId, String mensaje) {
        try {
            String respuestaIA = groqService.procesarMensaje(mensaje);
            JsonNode nodo = objectMapper.readTree(respuestaIA);
            String accion = nodo.path("accion").asText();

            switch (accion) {
                case "CREAR":
                    Pendiente p = new Pendiente();
                    p.setDescripcion(nodo.path("descripcion").asText());
                    p.setArea(nodo.path("area").asText());
                    p.setResponsable(nodo.path("responsable").asText());
                    p.setEstado("Pendiente");
                    pendienteRepository.save(p);
                    enviarMensaje(chatId, "✅ Tarea creada: " + p.getDescripcion() + " para " + p.getResponsable());
                    break;
                case "LISTAR":
                    enviarMensaje(chatId, obtenerListaPendientes());
                    break;
                default:
                    enviarMensaje(chatId, "No estoy seguro de qué quieres hacer. ¿Puedes ser más específico?");
            }
        } catch (Exception e) {
            enviarMensaje(chatId, "Ups, tuve un error al procesar tu solicitud con IA.");
        }
    }

    private String obtenerListaPendientes() {
        List<Pendiente> pendientes = pendienteRepository.findAll();
        if (pendientes.isEmpty()) return "No tienes pendientes registrados.";
        return pendientes.stream()
                .map(p -> String.format("- %s [%s] (%s)", p.getDescripcion(), p.getEstado(), p.getResponsable()))
                .collect(Collectors.joining("\n"));
    }

    private void enviarMensaje(long chatId, String texto) {
        SendMessage message = SendMessage.builder().chatId(chatId).text(texto).build();
        try { telegramClient.execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
}
