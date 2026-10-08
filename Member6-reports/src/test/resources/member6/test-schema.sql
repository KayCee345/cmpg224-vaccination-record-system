-- Minimal H2 copy of the tables the Member 6 queries touch. Keep in sync with Member 5's schema.
DROP TABLE IF EXISTS certificate_issues;
DROP TABLE IF EXISTS vaccination_records;
DROP TABLE IF EXISTS vaccine_batches;
DROP TABLE IF EXISTS vaccines;
DROP TABLE IF EXISTS patients;
DROP TABLE IF EXISTS users;

CREATE TABLE users (user_id INT PRIMARY KEY, username VARCHAR(50) NOT NULL, role VARCHAR(20) NOT NULL);
CREATE TABLE patients (
    patient_id INT PRIMARY KEY, user_id INT, full_name VARCHAR(150) NOT NULL, national_id VARCHAR(20),
    date_of_birth DATE NOT NULL, gender VARCHAR(20));
CREATE TABLE vaccines (vaccine_id INT PRIMARY KEY, name VARCHAR(100) NOT NULL);
CREATE TABLE vaccination_records (
    record_id INT PRIMARY KEY, patient_id INT NOT NULL, vaccine_id INT NOT NULL, worker_id INT NOT NULL,
    dose_number INT NOT NULL, batch_number VARCHAR(50), date_administered DATE NOT NULL);
CREATE TABLE vaccine_batches (
    batch_id INT PRIMARY KEY, vaccine_id INT NOT NULL, batch_number VARCHAR(50) NOT NULL, quantity INT NOT NULL,
    manufacturer VARCHAR(150), manufacture_date DATE, expiry_date DATE NOT NULL);
CREATE TABLE certificate_issues (
    certificate_id VARCHAR(30) PRIMARY KEY, patient_id INT NOT NULL, issued_at TIMESTAMP NOT NULL,
    issued_by INT NOT NULL, dose_count INT NOT NULL, snapshot_hash CHAR(64) NOT NULL);
