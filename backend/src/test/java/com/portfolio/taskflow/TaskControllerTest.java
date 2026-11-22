package com.portfolio.taskflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import(TaskService.class)
class TaskControllerTest {

    private static final String TASKS = "/api/tasks";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskRepository taskRepository;

    @Test
    void createsTaskWithGeneratedIdAndDefaultStatus() throws Exception {
        when(taskRepository.save(any(TaskItem.class))).thenAnswer(invocation -> {
            TaskItem task = invocation.getArgument(0);
            assertThat(task.getId()).isNull();
            assertThat(task.getStatus()).isEqualTo(TaskItem.Status.TODO);
            task.setId(7L);
            return task;
        });

        mockMvc.perform(json(post(TASKS), """
                        {"id":99,"title":"Créer une tâche","description":"Ajouter les DTO"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.title").value("Créer une tâche"))
                .andExpect(jsonPath("$.description").value("Ajouter les DTO"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void acceptsValuesAtTheLengthLimits() throws Exception {
        String title = "t".repeat(120);
        String description = "d".repeat(2000);
        when(taskRepository.save(any(TaskItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(json(post(TASKS), """
                        {"title":"%s","description":"%s","status":"IN_PROGRESS"}
                        """.formatted(title, description)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.description").value(description))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void listsTasks() throws Exception {
        when(taskRepository.findAll()).thenReturn(List.of(existingTask()));

        mockMvc.perform(get(TASKS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].title").value("Ancien titre"));
    }

    @Test
    void retrievesTaskById() throws Exception {
        when(taskRepository.findById(7L)).thenReturn(Optional.of(existingTask()));

        mockMvc.perform(get(TASKS + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.title").value("Ancien titre"))
                .andExpect(jsonPath("$.description").value("Ancienne description"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void updatesExistingTask() throws Exception {
        TaskItem task = existingTask();
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        mockMvc.perform(json(put(TASKS + "/7"), """
                        {"id":99,"title":"Nouveau titre","description":"Nouvelle description","status":"DONE"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.title").value("Nouveau titre"))
                .andExpect(jsonPath("$.description").value("Nouvelle description"))
                .andExpect(jsonPath("$.status").value("DONE"));

        verify(taskRepository).save(task);
    }

    @Test
    void deletesExistingTask() throws Exception {
        TaskItem task = existingTask();
        when(taskRepository.findById(7L)).thenReturn(Optional.of(task));

        mockMvc.perform(delete(TASKS + "/7"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(taskRepository).delete(task);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":\"   \"}"})
    void rejectsMissingOrBlankTitle(String body) throws Exception {
        mockMvc.perform(json(post(TASKS), body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Les données de la tâche sont invalides."))
                .andExpect(jsonPath("$.errors.title[0]").value("Le titre est obligatoire."));

        verifyNoInteractions(taskRepository);
    }

    @Test
    void reportsBothLengthViolationsOnUpdate() throws Exception {
        mockMvc.perform(json(put(TASKS + "/7"), """
                        {"title":"%s","description":"%s","status":"TODO"}
                        """.formatted("t".repeat(121), "d".repeat(2001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.title[0]").value("Le titre ne doit pas dépasser 120 caractères."))
                .andExpect(jsonPath("$.errors.description[0]")
                        .value("La description ne doit pas dépasser 2000 caractères."));

        verifyNoInteractions(taskRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"BLOCKED\"", "\"todo\"", "1", "null", "{}"})
    void rejectsInvalidStatuses(String value) throws Exception {
        mockMvc.perform(json(post(TASKS), """
                        {"title":"Tâche","status":%s}
                        """.formatted(value)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.status[0]").isNotEmpty());

        verifyNoInteractions(taskRepository);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(json(post(TASKS), "{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Le corps de la requête doit être un objet JSON valide."))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void rejectsNonNumericId() throws Exception {
        mockMvc.perform(get(TASKS + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Le paramètre « id » a un format invalide."))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void returnsConsistentErrorWhenTaskIsMissing(String method) throws Exception {
        when(taskRepository.findById(7L)).thenReturn(Optional.empty());

        mockMvc.perform(json(request(HttpMethod.valueOf(method), TASKS + "/7"),
                        "{\"title\":\"Tâche\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("La tâche 7 est introuvable."))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static TaskItem existingTask() {
        TaskItem task = new TaskItem();
        task.setId(7L);
        task.setTitle("Ancien titre");
        task.setDescription("Ancienne description");
        task.setStatus(TaskItem.Status.IN_PROGRESS);
        return task;
    }
}
