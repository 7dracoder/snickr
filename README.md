# Snickr

## 1. Prerequisites

Before running this project, please ensure you have the following software installed on your machine:

- **Java Development Kit (JDK) 25** (or compatible newer versions)
- **Docker** & **Docker Compose** (for running the PostgreSQL database)
- **Maven** (Included via `mvnw` wrapper in the project, but having it installed is helpful)
- Your preferred Java IDE (IntelliJ IDEA, Eclipse, or VS Code)

## 2. Database Initialization Configuration

This project is configured to automatically initialize the database schema and populate it with sample data upon startup. This behavior is controlled in the `src/main/resources/application.properties` file:

```
# ==========================================
# Initialize database
# ==========================================
spring.sql.init.mode=always

# Add sample data
spring.sql.init.data-locations=classpath:03_sample_data.sql
```

- `spring.sql.init.mode=always`: This setting forces Spring Boot to execute the SQL scripts (`schema.sql` and data scripts) every time the application starts. This is extremely useful for local development and testing, ensuring you always start with a clean state. **Warning: Change this to `never` in a production environment to prevent data loss.**
- `spring.sql.init.data-locations=classpath:03_sample_data.sql`: This specifies the location of the SQL script used to populate the database with mock users, workspaces, channels, and messages.

## 3. Starting the Database

We use Docker Compose to quickly spin up a PostgreSQL instance without installing it directly on your host machine.

1. Open your terminal and navigate to the project root directory (where the `docker-compose.yml` file is located).

2. Run the following command to start the database in detached mode:

   ```
   docker-compose up -d
   ```

3. The database will now be running on `localhost:5432` with the credentials specified in your `docker-compose.yml` file.

## 4. How to Run the Application

Once your database is up and running, follow these steps to launch Snickr:

**Method A: Using an IDE (Recommended)**

1. Open the project in your IDE (e.g., IntelliJ IDEA).
2. Locate the main application class `SnickrApplication.java` (in `src/main/java/com/snickr`).
3. Click the "Run" or "Debug" button next to the class name.

**Method B: Using Maven (Command Line)**

1. Open your terminal in the project root directory.

2. Execute the following command:

   ```
   ./mvnw spring-boot:run
   ```

   *(If you are on Windows, use `mvnw.cmd spring-boot:run`)*

**Accessing the App:** Once the application starts successfully, open your web browser and navigate to: `http://localhost:8080`

**Test Accounts:** The password for all test accounts is simply **`password`**.

- `alice` (Admin of TechCorp)
- `bob`
- `carol` (Admin of BookClub)
- `dave`
- `eve`