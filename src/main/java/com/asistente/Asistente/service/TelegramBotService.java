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
    
    @Value("${bot.token:NOT_FOUND}")
    private String botToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private PendienteRepository pendienteRepository;

    @Autowired
    private GroqService groqService;

    @PostConstruct
    public void start() {
        if (botToken.equals("NOT_FOUND") || botToken.isEmpty()) return;
        try {
            this.telegramClient = new OkHttpTelegramClient(botToken);
            this.botApplication = new TelegramBotsLongPollingApplication();
            this.botApplication.registerBot(botToken, this);
            System.out.println("BOT: Iniciado.");
        } catch (Exception e) {
            System.err.println("BOT: Error: " + e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try { if (botApplication != null) botApplication.close(); } catch (Exception e) { }
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.startsWith("/")) {
                manejarComandos(chatId, messageText);
            } else {
                procesarConIA(chatId, messageText);
            }
        }
    }

    private void manejarComandos(long chatId, String command) {
        if (command.equals("/start")) {
            enviarMensaje(chatId, "¡Hola! Soy tu asistente. Puedo crear tareas si me dices algo como: 'Añadir reparar motor en área 1, responsable Luis'");
        } else if (command.equals("/listar")) {
            enviarMensaje(chatId, obtenerListaPendientes());
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
                
                // Si la IA no encontró descripción, no es una orden de crear válida
                if (p.getDescripcion().equals("No especificado") || p.getDescripcion().isEmpty()) {
                    enviarMensaje(chatId, "Entiendo que quieres crear algo, pero ¿podrías decirme qué tarea exactamente?");
                    return;
                }

                pendienteRepository.save(p);
                enviarMensaje(chatId, "✅ Tarea registrada:\n📝 " + p.getDescripcion() + "\n📍 Área: " + p.getArea() + "\n👤 Responsable: " + p.getResponsable());
            } 
            else if ("LISTAR".equals(accion)) {
                enviarMensaje(chatId, "📋 Aquí tienes tus pendientes actuales:\n" + obtenerListaPendientes());
            } 
            else {
                enviarMensaje(chatId, "No estoy seguro de qué quieres hacer. Prueba diciendo: 'Crea un pendiente de...' o 'Lista mis tareas'.");
            }
        } catch (Exception e) {
            enviarMensaje(chatId, "Ups, tuve un problema con mi cerebro de IA.");
        }
    }

    private String obtenerListaPendientes() {
        List<Pendiente> pendientes = pendienteRepository.findAll();
        if (pendientes.isEmpty()) return "No hay pendientes registrados.";
        return pendientes.stream()
                .map(p -> String.format("- %s (Área: %s, Resp: %s)", p.getDescripcion(), p.getArea(), p.getResponsable()))
                .collect(Collectors.joining("\n"));
    }

    private void enviarMensaje(long chatId, String texto) {
        if (telegramClient == null) return;
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(texto).build());
        } catch (Exception e) { }
    }
}
