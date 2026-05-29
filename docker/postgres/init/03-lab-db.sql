CREATE DATABASE lab_db;
\c lab_db;

CREATE TABLE lab_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    docker_image VARCHAR(255) NOT NULL,
    description TEXT,
    tools_list TEXT,
    cpu_limit DECIMAL(4,2) DEFAULT 0.5,
    memory_limit BIGINT DEFAULT 536870912,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    created_by BIGINT
);

CREATE TABLE lab_assignments (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    instructor_id BIGINT,
    lab_template_id BIGINT REFERENCES lab_templates(id),
    container_id VARCHAR(64),
    container_name VARCHAR(255),
    status VARCHAR(32) DEFAULT 'PENDING',
    assigned_at TIMESTAMP DEFAULT NOW(),
    last_active_at TIMESTAMP,
    expires_at TIMESTAMP
);

-- Seed default lab templates
INSERT INTO lab_templates (name, docker_image, description, tools_list, cpu_limit, memory_limit)
VALUES
  ('Linux Basics', 'alpine:3.19', 'Lightweight Alpine Linux for shell basics', 'bash, vim, curl, wget', 0.25, 268435456),
  ('Ubuntu Dev Environment', 'ubuntu:22.04', 'Ubuntu with common dev tools', 'bash, vim, git, python3, nodejs', 0.5, 536870912),
  ('Cybersecurity Lab', 'kalilinux/kali-rolling', 'Kali Linux for security practice', 'nmap, netcat, curl, wget, python3', 1.0, 1073741824);
