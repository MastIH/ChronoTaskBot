package org.example.bot;

import org.example.model.Priority;
import org.example.model.Task;
import org.example.model.UserState;
import org.example.repository.Storage;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChronoTaskBot extends TelegramLongPollingBot {

    @Override
    public String getBotUsername() {
        return "ИМЯ_ТВОЕГО_БОТА";
    }

    @Override
    public String getBotToken() {
        return "ВАШ_ТОКЕН_БОТА";
    }

    // Фоновый поток: расчет задержки до ближайших 10:00 утра и запуск ежедневного обхода базы данных
    public void startReminderScheduler() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(10).withMinute(0).withSecond(0).withNano(0);

        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }

        long initialDelay = Duration.between(now, nextRun).toSeconds();
        long period = TimeUnit.DAYS.toSeconds(1);

        scheduler.scheduleAtFixedRate(() -> {
            LocalDate tomorrow = LocalDate.now().plusDays(1);

            for (Map.Entry<Long, List<Task>> entry : Storage.userTasks.entrySet()) {
                Long chatId = entry.getKey();
                List<Task> tasks = entry.getValue();

                for (Task task : tasks) {
                    if (!task.isCompleted() && task.getDeadline().toLocalDate().equals(tomorrow)) {
                        sendMessage(chatId, "🔔 *Напоминание!* Завтра дедлайн по задаче:\n• " + task.getTitle());
                    }
                }
            }
        }, initialDelay, period, TimeUnit.SECONDS);
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Обработка входящего текста и кнопок нижнего меню
        if (update.hasMessage() && update.getMessage().hasText()) {
            Long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            if (messageText.equals("/start")) {
                Storage.userStates.put(chatId, UserState.MAIN_MENU);
                sendMainMenu(chatId, "Планировщик задач запущен. Выберите действие:");
                return;
            }

            UserState state = Storage.userStates.getOrDefault(chatId, UserState.MAIN_MENU);

            if (messageText.equals("➕ Создать задачу")) {
                Storage.userStates.put(chatId, UserState.AWAITING_TITLE);
                sendMessage(chatId, "Введите название задачи:");
                return;
            }

            if (messageText.equals("📋 Мои задачи")) {
                sendTaskList(chatId);
                return;
            }

            if (messageText.equals("📊 Статистика")) {
                sendStatistics(chatId);
                return;
            }

            // Контроль состояний: последовательная обработка шагов создания задачи
            if (state == UserState.AWAITING_TITLE) {
                Task newTask = new Task(chatId);
                newTask.setTitle(messageText);
                Storage.taskInProgress.put(chatId, newTask);

                Storage.userStates.put(chatId, UserState.AWAITING_PRIORITY);
                sendPriorityMenu(chatId);
                return;
            }

            if (state == UserState.AWAITING_DATE) {
                Task task = Storage.taskInProgress.get(chatId);
                if (task != null) {
                    try {
                        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                        LocalDate localDate = LocalDate.parse(messageText, inputFormatter);

                        if (localDate.isBefore(LocalDate.now())) {
                            sendMessage(chatId, "Ошибка: дата не может быть в прошлом. Введите корректную дату (ДД.ММ.ГГГГ):");
                            return;
                        }

                        LocalDateTime deadline = localDate.atTime(23, 59, 0);
                        task.setDeadline(deadline);

                        Storage.userTasks.computeIfAbsent(chatId, k -> new ArrayList<>()).add(task);

                        // Запись обновленных данных в файл конфигурации JSON
                        Storage.saveTasksToFile();

                        Storage.taskInProgress.remove(chatId);
                        Storage.userStates.put(chatId, UserState.MAIN_MENU);

                        sendMainMenu(chatId, "Задача успешно создана.");

                    } catch (DateTimeParseException e) {
                        sendMessage(chatId, "Неверный формат. Введите дату в формате ДД.ММ.ГГГГ (например: 20.06.2026):");
                    }
                }
                return;
            }

            // Обработка нажатий на инлайн-кнопки
        } else if (update.hasCallbackQuery()) {
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            String callbackData = update.getCallbackQuery().getData();

            handleCallbackQuery(chatId, callbackData);
        }
    }

    // Распределение действий на основе полученных callback-данных
    private void handleCallbackQuery(Long chatId, String callbackData) {
        UserState state = Storage.userStates.getOrDefault(chatId, UserState.MAIN_MENU);

        if (state == UserState.AWAITING_PRIORITY && callbackData.startsWith("PRIORITY_")) {
            Task task = Storage.taskInProgress.get(chatId);
            if (task != null) {
                String priorityStr = callbackData.replace("PRIORITY_", "");
                task.setPriority(Priority.valueOf(priorityStr));

                Storage.userStates.put(chatId, UserState.AWAITING_DATE);
                sendMessage(chatId, "📅 Теперь введи дату дедлайна в формате *ДД.ММ.ГГГГ* (например: `20.06.2026`):");
            }
        }

        else if (callbackData.startsWith("COMPLETE_")) {
            List<Task> tasks = Storage.userTasks.get(chatId);
            if (tasks != null) {
                int taskIndex = Integer.parseInt(callbackData.replace("COMPLETE_", ""));

                if (taskIndex >= 0 && taskIndex < tasks.size()) {
                    Task task = tasks.get(taskIndex);
                    task.setCompleted(true);

                    // Перезапись измененного статуса задачи в файл JSON
                    Storage.saveTasksToFile();

                    sendMainMenu(chatId, "Задача выполнена.");
                }
            }
        }
    }

    // Формирование меню выбора приоритета на основе структуры Enum
    private void sendPriorityMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Выберите приоритет задачи:");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        for (Priority p : Priority.values()) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(p.getLabel());
            button.setCallbackData("PRIORITY_" + p.name());

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            rowsInline.add(row);
        }

        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Отображение актуального списка задач с сортировкой по приоритету
    private void sendTaskList(Long chatId) {
        List<Task> tasks = Storage.userTasks.get(chatId);

        boolean hasActiveTasks = false;
        if (tasks != null) {
            for (Task t : tasks) {
                if (!t.isCompleted()) {
                    hasActiveTasks = true;
                    break;
                }
            }
        }

        if (!hasActiveTasks) {
            sendMessage(chatId, "Активных задач нет.");
            return;
        }

        StringBuilder response = new StringBuilder("*Список задач по приоритетам:*\n\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        for (Priority p : Priority.values()) {
            response.append(p.getLabel()).append(":\n");

            boolean hasTasksInPriority = false;
            for (int i = 0; i < tasks.size(); i++) {
                Task task = tasks.get(i);

                if (task.getPriority() == p && !task.isCompleted()) {
                    response.append("• ").append(task.getTitle())
                            .append(" (до ").append(task.getDeadline().format(formatter)).append(")\n");
                    hasTasksInPriority = true;

                    InlineKeyboardButton completeBtn = new InlineKeyboardButton();
                    completeBtn.setText("✅ Закрыть: " + task.getTitle());
                    completeBtn.setCallbackData("COMPLETE_" + i);

                    rowsInline.add(List.of(completeBtn));
                }
            }

            if (!hasTasksInPriority) {
                response.append("Задач нет\n");
            }
            response.append("\n");
        }

        markupInline.setKeyboard(rowsInline);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(response.toString());
        message.setParseMode("Markdown");
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Расчет числовых показателей выполнения и отправка статистики пользователю
    private void sendStatistics(Long chatId) {
        List<Task> tasks = Storage.userTasks.get(chatId);

        if (tasks == null || tasks.isEmpty()) {
            sendMessage(chatId, "*Статистика:*\n\nНет данных для отображения.");
            return;
        }

        int totalTasks = tasks.size();
        int completedCount = 0;
        int activeHigh = 0;
        int activeMedium = 0;
        int activeLow = 0;

        for (Task task : tasks) {
            if (task.isCompleted()) {
                completedCount++;
            } else {
                if (task.getPriority() == Priority.HIGH) activeHigh++;
                else if (task.getPriority() == Priority.MEDIUM) activeMedium++;
                else if (task.getPriority() == Priority.LOW) activeLow++;
            }
        }

        int progressPercent = (completedCount * 100) / totalTasks;

        StringBuilder sb = new StringBuilder();
        sb.append("*Текущая статистика:*\n\n");
        sb.append("Всего задач: ").append(totalTasks).append("\n");
        sb.append("Выполнено: ").append(completedCount).append("\n");
        sb.append("Прогресс: ").append(progressPercent).append("%\n\n");
        sb.append("*Осталось выполнить:*\n");
        sb.append("Высокий приоритет: ").append(activeHigh).append("\n");
        sb.append("Средний приоритет: ").append(activeMedium).append("\n");
        sb.append("Низкий приоритет: ").append(activeLow);

        sendMessage(chatId, sb.toString());
    }

    // Формирование и отправка текстового сообщения с поддержкой разметки Markdown
    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Инициализация и отправка постоянного нижнего меню навигации
    private void sendMainMenu(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("➕ Создать задачу"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("📋 Мои задачи"));
        row2.add(new KeyboardButton("📊 Статистика"));

        keyboard.add(row1);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}