package org.example;

import org.example.bot.ChronoTaskBot;
import org.example.repository.Storage;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            Storage.loadTasksFromFile();

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            ChronoTaskBot bot = new ChronoTaskBot();

            botsApi.registerBot(bot);
            bot.startReminderScheduler();

            System.out.println("Бот успешно запущен.");

        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}