# Hospital Job Scheduling System

Java SE (JDBC, built-in HTTP server) + MySQL/PostgreSQL + vanilla HTML/CSS/JS.

## Run
```bash
mysql -u root -p -e "CREATE DATABASE hospital_scheduler"
mysql -u root -p hospital_scheduler < db/schema.sql
export DB_USER=root DB_PASSWORD=yourpassword
# PostgreSQL: export DB_URL=jdbc:postgresql://localhost:5432/hospital_scheduler
mvn compile exec:java
```
Open http://localhost:8080
