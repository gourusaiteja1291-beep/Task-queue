package com.taskqueue.task_queue;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TaskWorker {

    private static final Logger log = LoggerFactory.getLogger(TaskWorker.class);

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProductRepository productRepository;

    @Scheduled(fixedDelay = 2000)
    public void processTasks() {
        List<Task> pendingTasks = taskRepository.findByStatus("PENDING");

        for (Task task : pendingTasks) {
            processTask(task);
        }
    }

    private void processTask(Task task) {
        log.info("[task_id={}] Picking up task of type: {}", task.getId(), task.getTaskType());

        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        taskRepository.save(task);

        try {
            String result = switch (task.getTaskType()) {
                case "email_send" -> handleEmailSend(task.getPayload());
                case "csv_process" -> handleCsvProcess(task.getPayload());
                case "report_generation" -> handleReportGeneration(task.getPayload());
                default -> throw new Exception("Unknown task type: " + task.getTaskType());
            };

            task.setStatus("COMPLETED");
            task.setResult(result);
            log.info("[task_id={}] Completed successfully", task.getId());

        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            log.error("[task_id={}] Failed: {}", task.getId(), e.getMessage());
        }

        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);
    }

    private String handleEmailSend(String payload) throws InterruptedException {
        log.info("Sending email with payload: {}", payload);
        int delay = 2000 + (int)(Math.random() * 3000);
        Thread.sleep(delay);
        return "{\"status\":\"sent\",\"message\":\"Email sent successfully\"}";
    }

    private String handleCsvProcess(String payload) throws Exception {
        log.info("Processing CSV with payload: {}", payload);

        String fileUrl = payload.replace("{", "").replace("}", "")
            .replace("\"", "").split("file_url:")[1].split(",")[0].trim();

        log.info("Reading CSV file from: {}", fileUrl);

        java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.FileReader(fileUrl)
        );

        String line;
        int rowsRead = 0;
        int rowsImported = 0;
        int rowsSkipped = 0;
        boolean isHeader = true;

        while ((line = reader.readLine()) != null) {
            if (isHeader) {
                isHeader = false;
                continue;
            }

            rowsRead++;
            String[] columns = line.split(",");

            try {
                String name = columns[1].trim();
                double price = Double.parseDouble(columns[2].trim());
                String category = columns[3].trim();

                if (name.isEmpty()) {
                    log.warn("Row {} skipped — name is empty", rowsRead);
                    rowsSkipped++;
                    continue;
                }
                if (price < 0) {
                    log.warn("Row {} skipped — price is negative: {}", rowsRead, price);
                    rowsSkipped++;
                    continue;
                }

                Product product = new Product();
                product.setName(name);
                product.setPrice(price);
                product.setCategory(category);
                productRepository.save(product);
                rowsImported++;
                log.info("Imported product: {}", name);

            } catch (Exception e) {
                log.warn("Row {} skipped — error: {}", rowsRead, e.getMessage());
                rowsSkipped++;
            }
        }

        reader.close();

        return String.format(
            "{\"status\":\"processed\",\"rows_read\":%d,\"rows_imported\":%d,\"rows_skipped\":%d}",
            rowsRead, rowsImported, rowsSkipped
        );
    }

    private String handleReportGeneration(String payload) throws InterruptedException {
        log.info("Generating report with payload: {}", payload);
        Thread.sleep(4000);
        return "{\"status\":\"generated\",\"file_path\":\"/outputs/report.pdf\"}";
    }
}
