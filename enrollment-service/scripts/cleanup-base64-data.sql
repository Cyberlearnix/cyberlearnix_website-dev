-- Database Cleanup Script: Remove Base64 Image Data
-- 
-- This script identifies and cleans up corrupted content entries that contain
-- massive base64 image data causing 500 errors in the /full endpoint
-- 
-- Usage:
-- 1. Connect to your database
-- 2. Run this script to identify corrupted entries
-- 3. Review and apply the fixes
-- 4. Test the /full endpoint
-- 
-- Find content with base64 images (over 100KB or containing 'data:image/')
SELECT 
    id,
    course_id,
    content_type,
    title,
    COALESCE(content_text, '') as content_text,
    COALESCE(content, '') as content,
    LENGTH(COALESCE(content_text, '')) as text_length,
    LENGTH(COALESCE(content, '')) as content_length
FROM course_content 
WHERE 
    content_text LIKE '%data:image/%' 
    OR LENGTH(COALESCE(content_text, '')) > 100000
    OR LENGTH(COALESCE(content, '')) > 100000
ORDER BY course_id, id;

-- Find content with problematic HTML (multiple nested divs)
SELECT 
    id,
    course_id,
    content_type,
    title,
    content_text,
    content
FROM course_content 
WHERE 
    content_text LIKE '%<div>%<div>%<div>%<div>%'
    OR content LIKE '%<figure>%<figure>%<figure>%<figure>%'
    AND (LENGTH(content_text) > 50000 OR LENGTH(content) > 50000)
ORDER BY course_id, id;

-- Replace corrupted content with placeholder (safe text)
UPDATE course_content 
SET 
    content_text = CASE 
        WHEN content_text LIKE '%data:image/%' THEN '[IMAGE REMOVED - Base64 image was too large. Please upload images using the image upload button.]'
        WHEN content_text LIKE '%<div>%<div>%<div>%<div>%' AND (LENGTH(content_text) > 50000 OR LENGTH(content) > 50000) THEN '[HTML CLEANED - Corrupted HTML structure was detected and cleaned up.]'
        ELSE content_text
    END,
    content = CASE
        WHEN content_text LIKE '%data:image/%' THEN '[Image content removed due to size restrictions]'
        WHEN content_text LIKE '%<div>%<div>%<div>%<div>%' AND (LENGTH(content_text) > 50000 OR LENGTH(content) > 50000) THEN '[HTML content cleaned due to corruption]'
        ELSE content
        END
WHERE 
    content_text LIKE '%data:image/%' 
    OR (content_text LIKE '%<div>%<div>%<div>%<div>%' AND (LENGTH(content_text) > 50000 OR LENGTH(content) > 50000)
    OR LENGTH(COALESCE(content_text, '')) > 100000
    OR LENGTH(COALESCE(content, '')) > 100000;

-- Create backup before making changes (optional)
CREATE TABLE course_content_backup_YYYYMMDD_HHMMSS AS
SELECT * FROM course_content 
WHERE content_text LIKE '%data:image/%' 
   OR (content_text LIKE '%<div>%<div>%<div>%<div>%' AND (LENGTH(content_text) > 50000 OR LENGTH(content) > 50000))
   OR LENGTH(COALESCE(content_text, '')) > 100000
   OR LENGTH(COALESCE(content, '')) > 100000;

-- Add index for better performance on large text fields
CREATE INDEX IF NOT EXISTS idx_course_content_text_length ON course_content(LENGTH(content_text));
CREATE INDEX IF NOT EXISTS idx_course_content_length ON course_content(LENGTH(content));

-- Verify changes after cleanup
SELECT 
    course_id,
    COUNT(*) as corrupted_entries,
    SUM(CASE WHEN content_text LIKE '%data:image/%' THEN 1 ELSE 0 END) as base64_images,
    SUM(CASE WHEN content_text LIKE '%<div>%<div>%<div>%<div>%' AND (LENGTH(content_text) > 50000 OR LENGTH(content) > 50000) THEN 1 ELSE 0 END) as corrupted_html
FROM course_content 
GROUP BY course_id;

COMMIT;
