-- Cyberlearnix Private Limited — Business & Partnership Enquiry System
-- PostgreSQL schema migration for user-service (cyberlearnix_users)

CREATE TABLE IF NOT EXISTS business_enquiries (
    id BIGSERIAL PRIMARY KEY,
    enquiry_reference VARCHAR(32) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    organization_name VARCHAR(200),
    email VARCHAR(255) NOT NULL,
    mobile_number VARCHAR(32) NOT NULL,
    city VARCHAR(120) NOT NULL,
    designation VARCHAR(100),
    enquiry_type VARCHAR(100) NOT NULL,
    selected_services TEXT NOT NULL DEFAULT '[]',
    requirements TEXT NOT NULL,
    estimated_budget VARCHAR(80),
    expected_timeline VARCHAR(80),
    preferred_meeting VARCHAR(80),
    institution_type VARCHAR(120),
    department VARCHAR(150),
    student_count VARCHAR(50),
    collaboration_services TEXT NOT NULL DEFAULT '[]',
    preferred_contact_method VARCHAR(40),
    marketing_source VARCHAR(80),
    privacy_consent BOOLEAN NOT NULL DEFAULT FALSE,
    contact_consent BOOLEAN NOT NULL DEFAULT FALSE,
    lead_status VARCHAR(40) NOT NULL DEFAULT 'New',
    assigned_to VARCHAR(150),
    admin_notes TEXT,
    email_notification_status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    email_delivery_details TEXT,
    utm_source VARCHAR(120),
    utm_medium VARCHAR(120),
    utm_campaign VARCHAR(150),
    utm_content VARCHAR(150),
    submission_hash VARCHAR(64),
    source_ip VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_business_enquiries_reference UNIQUE (enquiry_reference),
    CONSTRAINT chk_be_privacy_consent CHECK (privacy_consent = TRUE),
    CONSTRAINT chk_be_contact_consent CHECK (contact_consent = TRUE),
    CONSTRAINT chk_be_lead_status CHECK (
        lead_status IN ('New', 'Contacted', 'Meeting Scheduled', 'Proposal Sent', 'Negotiation', 'Converted', 'Lost')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_be_reference ON business_enquiries (enquiry_reference);
CREATE INDEX IF NOT EXISTS idx_be_email ON business_enquiries (email);
CREATE INDEX IF NOT EXISTS idx_be_enquiry_type ON business_enquiries (enquiry_type);
CREATE INDEX IF NOT EXISTS idx_be_lead_status ON business_enquiries (lead_status);
CREATE INDEX IF NOT EXISTS idx_be_created_at ON business_enquiries (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_be_submission_hash ON business_enquiries (submission_hash);
CREATE INDEX IF NOT EXISTS idx_be_utm_campaign ON business_enquiries (utm_campaign);
