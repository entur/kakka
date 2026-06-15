/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.entur.kakka.task.routes.control;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskMessage {

    private SortedSet<Task> tasks;

    public TaskMessage() {
        tasks = new TreeSet<>();
    }

    public TaskMessage(Collection<Task> tasks) {
        this();
        if (tasks != null) {
            this.tasks.addAll(tasks);
        }
    }

    public TaskMessage(Task... taskArray) {
        this();
        if (taskArray != null) {
            tasks.addAll(Arrays.asList(taskArray));
        }
    }

    public static TaskMessage fromString(String string) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(string, TaskMessage.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SortedSet<Task> getTasks() {
        return tasks;
    }

    public void setTasks(SortedSet<Task> tasks) {
        this.tasks = tasks;
    }

    @JsonIgnore
    public boolean isComplete() {
        return tasks.stream().allMatch(Task::isComplete);
    }

    public void addTask(Task task) {
        getTasks().add(task);
    }

    public Task popNextTask() {
        Task task = tasks.first();
        tasks.remove(task);
        return task;
    }

    public String toString() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, this);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
