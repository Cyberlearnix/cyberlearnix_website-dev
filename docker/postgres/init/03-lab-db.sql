CREATE DATABASE lab_db;
\c lab_db;

-- ─── Lab Templates ──────────────────────────────────────────────────────────
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

-- ─── Course ↔ Lab Configuration ─────────────────────────────────────────────
-- Admin links a lab template to a course
CREATE TABLE course_lab_configs (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    lab_template_id BIGINT NOT NULL REFERENCES lab_templates(id),
    requires_approval BOOLEAN DEFAULT true,
    is_active BOOLEAN DEFAULT true,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(course_id, lab_template_id)
);

-- ─── Lab Approval Requests ──────────────────────────────────────────────────
-- Instructor requests a lab for a student; admin approves/rejects
CREATE TABLE lab_approval_requests (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    student_id VARCHAR(255) NOT NULL,
    requested_by_instructor_id VARCHAR(255),
    lab_template_id BIGINT NOT NULL REFERENCES lab_templates(id),
    status VARCHAR(16) DEFAULT 'PENDING',     -- PENDING | APPROVED | REJECTED
    rejection_reason TEXT,
    approved_by_admin_id BIGINT,
    resulting_assignment_id BIGINT,           -- filled after approval
    requested_at TIMESTAMP DEFAULT NOW(),
    decided_at TIMESTAMP
);

-- ─── Lab Assignments ────────────────────────────────────────────────────────
-- Active student container sessions
CREATE TABLE lab_assignments (
    id BIGSERIAL PRIMARY KEY,
    student_id VARCHAR(255) NOT NULL,
    instructor_id VARCHAR(255),
    lab_template_id BIGINT REFERENCES lab_templates(id),
    course_id BIGINT,                         -- which course this lab belongs to
    approval_request_id BIGINT REFERENCES lab_approval_requests(id),
    container_id VARCHAR(64),
    container_name VARCHAR(255),
    status VARCHAR(32) DEFAULT 'PENDING',
    assigned_at TIMESTAMP DEFAULT NOW(),
    last_active_at TIMESTAMP,
    expires_at TIMESTAMP
);

-- ─── Indexes ─────────────────────────────────────────────────────────────────
CREATE INDEX idx_lab_assignments_student ON lab_assignments(student_id);
CREATE INDEX idx_lab_assignments_course ON lab_assignments(course_id);
CREATE INDEX idx_lab_assignments_status ON lab_assignments(status);
CREATE INDEX idx_lab_approval_course_student ON lab_approval_requests(course_id, student_id);
CREATE INDEX idx_lab_approval_status ON lab_approval_requests(status);

-- ─── Default Lab Templates ───────────────────────────────────────────────────
INSERT INTO lab_templates (name, docker_image, description, tools_list, cpu_limit, memory_limit)
VALUES
  ('Linux Basics',
   'alpine:3.19',
   'Lightweight Alpine Linux — perfect for learning shell commands, file navigation, and basic scripting.',
   'sh, vim, nano, curl, wget, grep, awk, sed',
   0.25, 268435456),          -- 0.25 CPU, 256 MB

  ('Ubuntu Developer',
   'ubuntu:22.04',
   'Ubuntu 22.04 LTS with popular development tools pre-configured for coding practice.',
   'bash, vim, git, python3, pip3, nodejs, npm, curl, wget, gcc, make',
   0.5, 536870912),           -- 0.5 CPU, 512 MB

  ('Cybersecurity Lab',
   'kalilinux/kali-rolling',
   'Kali Linux rolling — fully equipped for cybersecurity labs, CTF practice, and offensive/defensive security techniques.',
   'nmap, netcat, curl, wget, python3, metasploit-framework, john, hashcat, gobuster',
   1.0, 1073741824),          -- 1.0 CPU, 1 GB

  ('Python Coding',
   'python:3.12-slim',
   'Minimal Python 3.12 environment for algorithm practice and coding challenges.',
   'python3, pip, vim',
   0.25, 268435456),

  ('Node.js Dev',
   'node:20-alpine',
   'Node.js 20 Alpine — for JavaScript and TypeScript development practice.',
   'node, npm, npx, vim',
   0.25, 268435456);
