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
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
        } catch (Exception e) {
            System.err.println("BOT_ERROR: " + e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        try { if (botApplication != null) botApplication.close(); } catch (Exception e) { }
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage()) return;

        long chatId = update.getMessage().getChatId();

        if (update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            if (text.startsWith("/")) manejarComandos(chatId, text);
            else procesarConIA(chatId, text);
        } 
        else if (update.getMessage().hasVoice()) {
            manejarVoz(chatId, update.getMessage().getVoice());
        }
    }

    private void manejarVoz(long chatId, Voice voice) {
        try {
            enviarMensaje(chatId, "Escuchando audio... 🎧");
            
            // 1. Obtener la ruta del archivo en Telegram
            org.telegram.telegrambots.meta.api.objects.File fileMetadata = telegramClient.execute(new GetFile(voice.getFileId()));
            String fileUrl = "https://api.telegram.org/file/bot" + botToken + "/" + fileMetadata.getFilePath();
            
            // 2. Descargar el audio temporalmente
            File tempFile = File.createTempFile("voice", ".ogg");
            try (InputStream in = new URL(fileUrl).openStream()) {
                Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            
            // 3. Transcribir con Groq
            String textoTranscrito = groqService.transcribirAudio(tempFile);
            tempFile.delete();
            
            if (textoTranscrito.isEmpty()) {
                enviarMensaje(chatId, "No pude entender el audio. ¿Puedes repetirlo?");
            } else {
                enviarMensaje(chatId, "Entendido: \"" + textoTranscrito + "\"");
                procesarConIA(chatId, textoTranscrito);
            }
        } catch (Exception e) {
            enviarMensaje(chatId, "Error al procesar el audio.");
        }
    }

    private void manejarComandos(long chatId, String command) {
        if (command.equals("/start")) {
            enviarMensaje(chatId, "¡Hola! Puedes hablarme o enviarme audios para gestionar tus tareas.");
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
                
                if (p.getDescripcion().equals("No especificado") || p.getDescripcion().isEmpty()) {
                    enviarMensaje(chatId, "No entendí qué tarea quieres crear. ¿Podrías ser más específico?");
                    return;
                }

                pendienteRepository.save(p);
                enviarMensaje(chatId, "✅ Tarea registrada:\n📝 " + p.getDescripcion() + "\n👤 Resp: " + p.getResponsable());
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
        try { telegramClient.execute(SendMessage.builder().chatId(chatId).text(texto).build()); } catch (Exception e) { }
    }
}
