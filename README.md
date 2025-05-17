# Attendance Management System

A Spring Boot application for managing employee attendance with features like punch-in/out tracking, multiple daily entries, and employee management.

## Features

- Mark attendance (punch in/out) for employees
- Get attendance records for a specific duration
- Daily attendance summary with total work hours
- Overall duration summary

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Docker and Docker Compose
- PostgreSQL 15 (if running without Docker)

## Database Setup

### Option 1: Using Docker (Recommended)

The application comes with a Docker Compose configuration that sets up both the application and PostgreSQL database.

1. Make sure Docker and Docker Compose are installed on your system
2. The database configuration is already set in `docker-compose.yml`:
   ```yaml
   postgres:
     image: postgres:15
     environment:
       POSTGRES_DB: attendance_db
       POSTGRES_USER: postgres
       POSTGRES_PASSWORD: postgres
     ports:
       - "5432:5432"
     volumes:
       - postgres_data:/var/lib/postgresql/data
   ```

### Option 2: Manual PostgreSQL Setup

1. Install PostgreSQL 15 on your system
2. Create a new database:
   ```sql
   CREATE DATABASE attendance_db;
   ```
3. Create a user (optional):
   ```sql
   CREATE USER postgres WITH PASSWORD 'postgres';
   GRANT ALL PRIVILEGES ON DATABASE attendance_db TO postgres;
   ```
4. Update `application.properties` with your database credentials:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/attendance_db
   spring.datasource.username=postgres
   spring.datasource.password=postgres
   ```

## Deployment

### Prerequisites
- Docker and Docker Compose installed
- Docker Hub account (for pulling the image)

### Running with Docker Compose

1. Create a `.env` file in the project root (optional):
   ```
   DOCKER_USERNAME=your-dockerhub-username
   ```

2. Start the application:
   ```bash
   docker-compose up -d
   ```

3. The application will be available at `http://localhost:8080`

### CI/CD Pipeline

This project uses GitHub Actions for continuous integration and deployment. The pipeline:

1. Triggers on push to master and pull requests
2. Builds the application using Maven
3. Builds and pushes the Docker image to Docker Hub
4. Tags the image with both `latest` and the commit SHA

To set up the pipeline:

1. Fork or clone this repository
2. Add the following secrets to your GitHub repository:
   - `DOCKER_USERNAME`: Your Docker Hub username
   - `DOCKER_PASSWORD`: Your Docker Hub password or access token

## API Endpoints

### Employee Management
- Create Employee: `POST /api/employees`
  ```json
  {
    "name": "John Doe",
    "email": "john.doe@example.com",
    "department": "IT"
  }
  ```

### Attendance Management
- Mark Attendance: `POST /api/attendance/{employeeId}/mark/{event}`
  - `event` can be either `PUNCH_IN` or `PUNCH_OUT`
  - Example: `POST /api/attendance/1/mark/PUNCH_IN`

## Database Schema

### Employees Table
```sql
CREATE TABLE employees (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    department VARCHAR(255) DEFAULT 'General'
);
```

### Attendance Table
```sql
CREATE TABLE attendance (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    date DATE NOT NULL,
    time TIME NOT NULL,
    action VARCHAR(20) NOT NULL,
    duration_minutes INTEGER,
    is_working_day BOOLEAN DEFAULT true,
    is_holiday BOOLEAN DEFAULT false,
    holiday_name VARCHAR(255),
    is_weekend BOOLEAN DEFAULT false,
    is_overtime BOOLEAN DEFAULT false,
    remarks TEXT,
    FOREIGN KEY (employee_id) REFERENCES employees(id)
);
```

## Environment Variables

The following environment variables can be configured:

| Variable | Description | Default Value |
|----------|-------------|---------------|
| `SPRING_DATASOURCE_URL` | Database URL | jdbc:postgresql://postgres:5432/attendance_db |
| `SPRING_DATASOURCE_USERNAME` | Database username | postgres |
| `SPRING_DATASOURCE_PASSWORD` | Database password | postgres |
| `SERVER_PORT` | Application port | 8080 |

## Troubleshooting

1. Database Connection Issues:
   - Verify PostgreSQL is running
   - Check database credentials in `application.properties`
   - Ensure database port (5432) is not blocked

2. Application Startup Issues:
   - Check Java version (should be 17 or higher)
   - Verify all required ports are available
   - Check application logs for detailed error messages

## Support

For any issues or questions, please create an issue in the repository.

## Development

The project structure follows standard Spring Boot conventions:

```
src/main/java/com/attendance/
├── AttendanceManagementApplication.java
├── controller/
│   └── AttendanceController.java
├── entity/
│   ├── Employee.java
│   └── Attendance.java
├── repository/
│   ├── EmployeeRepository.java
│   └── AttendanceRepository.java
└── service/
    └── AttendanceService.java
```

## Error Handling

The application includes basic error handling for:
- Employee not found
- Invalid date ranges
- Invalid attendance actions

## Security

For production deployment, consider adding:
- Spring Security for authentication and authorization
- HTTPS configuration
- API key validation
- Rate limiting 