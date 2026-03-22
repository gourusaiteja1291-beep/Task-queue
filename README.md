# Distributed Task Queue System

A backend task queue system built with Java Spring Boot and PostgreSQL. Accepts tasks from clients, queues them in a database, and processes them asynchronously using a background worker — without blocking the client.

---

## What It Does

Instead of making the client wait for a long-running job to finish, this system:

1. Client submits a task → gets a **task ID instantly**
2. Background worker **picks it up and processes it**
3. Client checks status anytime using the task ID

---

## Tech Stack

| Technology      | Version | Purpose              |
|---              |---      |---                   |
| Java            | 17      | Programming language |
| Spring Boot     | 3.5     | Backend framework    |
| PostgreSQL      | 16      | Database             |
| Spring Data JPA | -       | Database ORM         |
| Maven           | -       | Build tool           |

---

## Project Structure

src/main/java/com/taskqueue/task_queue/
├── TaskQueueApplication.java   → Main entry point + @EnableScheduling
├── Task.java                   → Task entity (maps tasks table)
├── Product.java                → Product entity (maps products table)
├── TaskRepository.java         → DB queries for tasks table
├── ProductRepository.java      → DB queries for products table
├── TaskService.java            → Business logic layer (submit, get, filter tasks)
├── TaskController.java         → REST API endpoints (HTTP layer only)
└── TaskWorker.java             → Background worker + all 4 task handlers
---

## Prerequisites

- Java 17
- PostgreSQL 16
- Maven

---

## Setup & Running

**1. Clone the repository**
```bash
git clone https://github.com/gourusaiteja1291-beep/Task-queue.git
cd Task-queue
```

**2. Create the database**

Open pgAdmin or psql and run:
```sql
CREATE DATABASE taskqueue_db;
```

**3. Configure database connection**

Open `src/main/resources/application.properties` and update:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/taskqueue_db
spring.datasource.username=postgres
spring.datasource.password=YOUR_PASSWORD
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

**4. Run the application**
```bash
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`

Tables are created automatically on first run.

---

## API Endpoints

### Submit a task
```
POST /api/tasks
```
Request body:
```json
{
  "taskType": "email_send",
  "payload": "{\"to\":\"john@example.com\",\"subject\":\"Hello\",\"body\":\"Hi John!\"}"
}
```
Response:
```json
{
  "id": "a1b2-c3d4-...",
  "taskType": "email_send",
  "status": "PENDING",
  "createdAt": "2026-03-21T10:00:00"
}
```

---

### Get task by ID
```
GET /api/tasks/{id}
```
Response:
```json
{
  "id": "a1b2-c3d4-...",
  "status": "COMPLETED",
  "result": "{\"status\":\"sent\",\"message\":\"Email sent successfully\"}",
  "startedAt": "2026-03-21T10:00:02",
  "completedAt": "2026-03-21T10:00:05"
}
```

---

### Get all tasks
```
GET /api/tasks
```

### Filter tasks by status
```
GET /api/tasks?status=PENDING
GET /api/tasks?status=RUNNING
GET /api/tasks?status=COMPLETED
GET /api/tasks?status=FAILED
```

---

## Task Types

### 1. email_send
Simulates sending a transactional email.

**Payload:**
```json
{
  "taskType": "email_send",
  "payload": "{\"to\":\"user@example.com\",\"subject\":\"Order confirmed\",\"body\":\"Hi, your order is ready!\"}"
}
```
**What it does:** Logs email details, sleeps 2-5 seconds (mock delay), returns success.

**Result:**
```json
{"status": "sent", "message": "Email sent successfully"}
```

---

### 2. csv_process
Reads a CSV file from disk, validates each row, and imports valid data into the products table.

**Payload:**
```json
{
  "taskType": "csv_process",
  "payload": "{\"file_url\":\"C:/task-queue/test.csv\",\"operation\":\"import\"}"
}
```

**CSV format:**
```
id,name,price,category
1,Laptop,999.99,Electronics
2,Phone,499.99,Electronics
```

**Validation rules:**
- Skips rows where name is empty
- Skips rows where price is negative

**Result:**
```json
{"status": "processed", "rows_read": 6, "rows_imported": 4, "rows_skipped": 2}
```

---

### 3. data_import
Bulk imports a JSON array of products directly into the database.

**Payload:**
```json
{
  "taskType": "data_import",
  "payload": "{\"items\":[{\"name\":\"Keyboard\",\"price\":49.99,\"category\":\"Electronics\"},{\"name\":\"Mouse\",\"price\":29.99,\"category\":\"Electronics\"}]}"
}
```

**Validation rules:**
- Skips items where name is empty
- Skips items where price is negative

**Result:**
```json
{"status": "imported", "total_items": 4, "imported": 3, "skipped": 1}
```

---

### 4. report_generation
Simulates generating a PDF or Excel report.

**Payload:**
```json
{
  "taskType": "report_generation",
  "payload": "{\"report_type\":\"monthly_sales\",\"month\":\"2026-03\",\"format\":\"PDF\"}"
}
```
**What it does:** Sleeps 4 seconds (mock delay), returns file path.

**Result:**
```json
{"status": "generated", "file_path": "/outputs/report.pdf"}
```

---

## Task Status Lifecycle

```
PENDING → RUNNING → COMPLETED
                 → FAILED
```

| Status   | Set By    | Meaning                             |
|---       |---        |---                                  |
| PENDING  | API Layer | Task saved, waiting for worker      |
| RUNNING  | Worker    | Worker picked it up, processing now |
| COMPLETED| Worker    | Finished successfully, result saved |
| FAILED   | Worker    | Error occurred, error_message saved |

---

## Database Schema

### tasks table
| Column        | Type      | Description                |
|---            |---        |---                         |
| id            | UUID      | Unique task identifier     |
| task_type     | VARCHAR   | Type of task               |
| payload       | JSONB     | Input data for the handler |
| status        | VARCHAR   | Current state              |
| result        | JSONB     | Output on success          |
| error_message | TEXT      | Error details on failure   |
| created_at    | TIMESTAMP | When task was submitted    |
| started_at    | TIMESTAMP | When worker picked it up   |
| completed_at  | TIMESTAMP | When task finished         |

### products table
| Column   | Type    | Description       |
|---       |---      |---                |
| id       | BIGINT  | Auto-generated ID |
| name     | VARCHAR | Product name      |
| price    | DOUBLE  | Product price     |
| category | VARCHAR | Product category  |

---

## How the Worker Works

The background worker runs every 2 seconds:

1. Queries DB for tasks with `status = PENDING`
2. Locks the task and sets `status = RUNNING`
3. Dispatches to the correct handler based on `task_type`
4. On success → sets `status = COMPLETED`, saves result
5. On failure → sets `status = FAILED`, saves error message
6. Sleeps 2 seconds, repeats

---

## Testing with Postman

Import the following requests into Postman:

| Request            | Method | URL |
|---                 |---     |---                              
| Submit email task  | POST   | http://localhost:8080/api/tasks 
| Submit CSV task    | POST   | http://localhost:8080/api/tasks 
| Submit data import | POST   | http://localhost:8080/api/tasks 
| Get task by ID     | GET    | http://localhost:8080/api/tasks/{id} 
| Get all tasks      | GET    | http://localhost:8080/api/tasks 
| Filter by status   | GET   | http://localhost:8080/api/tasks?status=COMPLETED  

---

## Author

**Gourusai Teja**
Internship Technical Assessment — Evolving Systems Ltd
March 2026
