package org.example.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.model.Task;
import org.example.model.UserState;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Storage {
    public static final Map<Long, UserState> userStates = new HashMap<>();
    public static final Map<Long, Task> taskInProgress = new HashMap<>();


    public static final Map<Long, List<Task>> userTasks = new HashMap<>();


    private static final String FILE_PATH = "tasks.json";

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public static void saveTasksToFile() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(FILE_PATH), userTasks);
        } catch (IOException e) {
            System.out.println("Ошибка при сохранении задач в файл: " + e.getMessage());
        }
    }

    public static void loadTasksFromFile() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            System.out.println("Файл задач еще не создан. Автоматическое создание при первой записи.");
            return;
        }
        try {
            Map<Long, List<Task>> loadedTasks = mapper.readValue(file, new TypeReference<Map<Long, List<Task>>>() {});

            userTasks.clear();
            userTasks.putAll(loadedTasks);
            System.out.println("Задачи успешно загружены из файла. Количество пользователей: " + userTasks.size());
        } catch (IOException e) {
            System.out.println("Ошибка при загрузке задач из файла: " + e.getMessage());
        }
    }
}