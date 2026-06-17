import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import pg from 'pg';
import { createSign } from 'crypto';

const { Pool } = pg;
const app = express();
const PORT = 8082;

// PostgreSQL connection to Docker container
const pool = new Pool({
    host: 'localhost',
    port: 5433,
    database: 'cyberlearnix',
    user: 'postgres',
    password: 'postgres',
});

app.use(cors({
    origin: '*',
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'X-File-Type', 'X-Requested-With']
}));
app.use(express.json());

// Create tables on startup if they don't exist
async function initDB() {
    try {
        await pool.query(`
            CREATE TABLE IF NOT EXISTS courses (
                id SERIAL PRIMARY KEY,
                title VARCHAR(255) NOT NULL,
                slug VARCHAR(255) UNIQUE,
                description TEXT,
                short_description TEXT,
                price NUMERIC(10, 2) DEFAULT 0,
                difficulty VARCHAR(50) DEFAULT 'Beginner',
                thumbnail_url TEXT,
                is_published BOOLEAN DEFAULT false,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                updated_at TIMESTAMPTZ DEFAULT NOW()
            );

            CREATE TABLE IF NOT EXISTS course_modules (
                id SERIAL PRIMARY KEY,
                course_id INTEGER REFERENCES courses(id) ON DELETE CASCADE,
                title VARCHAR(255) NOT NULL,
                description TEXT,
                order_index INTEGER DEFAULT 0,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );

            CREATE TABLE IF NOT EXISTS course_lessons (
                id SERIAL PRIMARY KEY,
                module_id INTEGER REFERENCES course_modules(id) ON DELETE CASCADE,
                course_id INTEGER REFERENCES courses(id) ON DELETE CASCADE,
                title VARCHAR(255) NOT NULL,
                content_type VARCHAR(50) DEFAULT 'video',
                content_url TEXT,
                duration_minutes INTEGER DEFAULT 0,
                order_index INTEGER DEFAULT 0,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );

            CREATE TABLE IF NOT EXISTS teacher_courses (
                id SERIAL PRIMARY KEY,
                teacher_id VARCHAR(255) NOT NULL,
                course_id INTEGER REFERENCES courses(id) ON DELETE CASCADE,
                assigned_at TIMESTAMPTZ DEFAULT NOW(),
                UNIQUE(teacher_id, course_id)
            );

            CREATE TABLE IF NOT EXISTS student_enrollments (
                id SERIAL PRIMARY KEY,
                student_id VARCHAR(255) NOT NULL,
                course_id INTEGER REFERENCES courses(id) ON DELETE CASCADE,
                student_name VARCHAR(255),
                student_email VARCHAR(255),
                enrolled_at TIMESTAMPTZ DEFAULT NOW(),
                course_started_at TIMESTAMPTZ,
                last_activity_at TIMESTAMPTZ,
                UNIQUE(student_id, course_id)
            );

            CREATE TABLE IF NOT EXISTS student_progress (
                id SERIAL PRIMARY KEY,
                student_id VARCHAR(255) NOT NULL,
                course_id INTEGER REFERENCES courses(id) ON DELETE CASCADE,
                lesson_id INTEGER REFERENCES course_lessons(id) ON DELETE CASCADE,
                completed_at TIMESTAMPTZ DEFAULT NOW(),
                UNIQUE(student_id, lesson_id)
            );

            CREATE TABLE IF NOT EXISTS lab_submissions (
                id SERIAL PRIMARY KEY,
                student_id VARCHAR(255) NOT NULL,
                course_id INTEGER REFERENCES courses(id) ON DELETE CASCADE,
                lab_id VARCHAR(255),
                lab_title VARCHAR(255),
                file_url TEXT,
                notes TEXT,
                status VARCHAR(50) DEFAULT 'SUBMITTED',
                submitted_at TIMESTAMPTZ DEFAULT NOW(),
                reviewed_at TIMESTAMPTZ,
                reviewer_notes TEXT
            );

            CREATE TABLE IF NOT EXISTS hero_banners (
                id SERIAL PRIMARY KEY,
                title VARCHAR(255) NOT NULL DEFAULT '',
                subtitle TEXT DEFAULT '',
                img TEXT DEFAULT '',
                badge VARCHAR(255) DEFAULT '',
                width VARCHAR(20) DEFAULT 'split',
                btn1_text VARCHAR(255) DEFAULT '',
                btn1_link VARCHAR(500) DEFAULT '',
                btn2_text VARCHAR(255) DEFAULT '',
                btn2_link VARCHAR(500) DEFAULT '',
                features JSONB DEFAULT '[]',
                proof VARCHAR(255) DEFAULT '',
                sort_order INTEGER DEFAULT 0,
                is_active BOOLEAN DEFAULT true,
                created_at TIMESTAMPTZ DEFAULT NOW(),
                updated_at TIMESTAMPTZ DEFAULT NOW()
            );
        `);

        // Migrate existing student_enrollments table to add new columns if they don't exist
        await pool.query(`
            ALTER TABLE student_enrollments ADD COLUMN IF NOT EXISTS student_name VARCHAR(255);
            ALTER TABLE student_enrollments ADD COLUMN IF NOT EXISTS student_email VARCHAR(255);
            ALTER TABLE student_enrollments ADD COLUMN IF NOT EXISTS course_started_at TIMESTAMPTZ;
            ALTER TABLE student_enrollments ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMPTZ;
        `);

        // Extend courses table with richer admin fields
        await pool.query(`
            ALTER TABLE courses ADD COLUMN IF NOT EXISTS category VARCHAR(100) DEFAULT '';
            ALTER TABLE courses ADD COLUMN IF NOT EXISTS difficulty_level VARCHAR(50) DEFAULT 'BEGINNER';
            ALTER TABLE courses ADD COLUMN IF NOT EXISTS duration VARCHAR(100) DEFAULT '';
            ALTER TABLE courses ADD COLUMN IF NOT EXISTS base_price NUMERIC(10,2) DEFAULT 0;
            ALTER TABLE courses ADD COLUMN IF NOT EXISTS gst_percent INTEGER DEFAULT 18;
            ALTER TABLE courses ADD COLUMN IF NOT EXISTS final_price NUMERIC(10,2) DEFAULT 0;
            ALTER TABLE courses ADD COLUMN IF NOT EXISTS content_url TEXT DEFAULT '';
            ALTER TABLE courses ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true;
            ALTER TABLE courses ADD COLUMN IF NOT EXISTS certificate_enabled BOOLEAN DEFAULT false;
            ALTER TABLE courses ADD COLUMN IF NOT EXISTS instructor_name VARCHAR(255) DEFAULT '';
            ALTER TABLE courses ADD COLUMN IF NOT EXISTS certificate_image_url TEXT DEFAULT '';
            ALTER TABLE courses ADD COLUMN IF NOT EXISTS prerequisites TEXT DEFAULT '';
            ALTER TABLE courses ADD COLUMN IF NOT EXISTS syllabus TEXT DEFAULT '';
            ALTER TABLE courses ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
            ALTER TABLE courses ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) DEFAULT '';
        `);

        // Module contents table
        await pool.query(`
            CREATE TABLE IF NOT EXISTS course_module_contents (
                id SERIAL PRIMARY KEY,
                module_id INTEGER REFERENCES course_modules(id) ON DELETE CASCADE,
                course_id INTEGER REFERENCES courses(id) ON DELETE CASCADE,
                title VARCHAR(255) NOT NULL,
                content_type VARCHAR(50) DEFAULT 'video',
                content_url TEXT DEFAULT '',
                description TEXT DEFAULT '',
                duration_minutes INTEGER DEFAULT 0,
                order_index INTEGER DEFAULT 0,
                is_active BOOLEAN DEFAULT true,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );
        `);

        console.log('✅ Database tables ready');
    } catch (err) {
        console.error('❌ DB init error:', err.message);
    }
}

// Helper to generate slug from title
function slugify(title) {
    return title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
}

// Normalise a raw DB course row to camelCase frontend-friendly shape
function mapCourse(row) {
    const basePrice = Number(row.base_price ?? row.price ?? 0);
    const gstPercent = Number(row.gst_percent ?? 18);
    const finalPrice = Number(row.final_price ?? (basePrice * (1 + gstPercent / 100)));
    return {
        id: row.id,
        title: row.title,
        slug: row.slug,
        description: row.description || '',
        shortDescription: row.short_description || '',
        category: row.category || '',
        difficultyLevel: row.difficulty_level || row.difficulty || 'BEGINNER',
        duration: row.duration || '',
        basePrice,
        price: basePrice,
        gstPercent,
        finalPrice,
        contentUrl: row.content_url || '',
        thumbnailUrl: row.thumbnail_url || '',
        isActive: row.is_active !== null && row.is_active !== undefined ? row.is_active : (row.is_published ?? true),
        isPublished: row.is_published ?? row.is_active ?? true,
        certificateEnabled: row.certificate_enabled ?? false,
        instructorName: row.instructor_name || '',
        certificateImageUrl: row.certificate_image_url || '',
        prerequisites: row.prerequisites || '',
        syllabus: row.syllabus || '',
        createdBy: row.created_by || '',
        createdAt: row.created_at,
        updatedAt: row.updated_at,
    };
}

// ─── COURSE MANAGEMENT (Admin) ───────────────────────────────────────────────

// GET /api/course-management — list all courses with module count
app.get('/api/course-management', async (req, res) => {
    try {
        const result = await pool.query(`
            SELECT c.*, COUNT(m.id)::int AS module_count
            FROM courses c
            LEFT JOIN course_modules m ON m.course_id = c.id
            GROUP BY c.id
            ORDER BY c.created_at DESC
        `);
        res.json(result.rows);
    } catch (err) {
        console.error(err);
        res.status(500).json({ error: err.message });
    }
});

// POST /api/course-management — create course
app.post('/api/course-management', async (req, res) => {
    const { title, description, shortDescription, price, difficulty, thumbnailUrl } = req.body;
    if (!title) return res.status(400).json({ error: 'title is required' });
    const slug = slugify(title) + '-' + Date.now();
    try {
        const result = await pool.query(
            `INSERT INTO courses (title, slug, description, short_description, price, difficulty, thumbnail_url)
             VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING *`,
            [title, slug, description || '', shortDescription || '', price || 0, difficulty || 'Beginner', thumbnailUrl || '']
        );
        res.status(201).json(result.rows[0]);
    } catch (err) {
        console.error(err);
        res.status(500).json({ error: err.message });
    }
});

// PUT /api/course-management/:id — update course
app.put('/api/course-management/:id', async (req, res) => {
    const { id } = req.params;
    const { title, description, shortDescription, price, difficulty, thumbnailUrl, isPublished } = req.body;
    try {
        const result = await pool.query(
            `UPDATE courses SET
                title = COALESCE($1, title),
                description = COALESCE($2, description),
                short_description = COALESCE($3, short_description),
                price = COALESCE($4, price),
                difficulty = COALESCE($5, difficulty),
                thumbnail_url = COALESCE($6, thumbnail_url),
                is_published = COALESCE($7, is_published),
                updated_at = NOW()
             WHERE id = $8 RETURNING *`,
            [title, description, shortDescription, price, difficulty, thumbnailUrl, isPublished, id]
        );
        if (result.rows.length === 0) return res.status(404).json({ error: 'Course not found' });
        res.json(result.rows[0]);
    } catch (err) {
        console.error(err);
        res.status(500).json({ error: err.message });
    }
});

// DELETE /api/course-management/:id — delete course
app.delete('/api/course-management/:id', async (req, res) => {
    try {
        await pool.query('DELETE FROM courses WHERE id = $1', [req.params.id]);
        res.json({ success: true });
    } catch (err) {
        console.error(err);
        res.status(500).json({ error: err.message });
    }
});

// ─── MODULES ─────────────────────────────────────────────────────────────────

// GET /api/course-management/:id/modules
app.get('/api/course-management/:id/modules', async (req, res) => {
    try {
        const result = await pool.query(
            'SELECT * FROM course_modules WHERE course_id = $1 ORDER BY order_index ASC',
            [req.params.id]
        );
        res.json({ modules: result.rows });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// POST /api/course-management/:id/modules
app.post('/api/course-management/:id/modules', async (req, res) => {
    const { title, description, orderIndex } = req.body;
    if (!title) return res.status(400).json({ error: 'title is required' });
    try {
        const result = await pool.query(
            `INSERT INTO course_modules (course_id, title, description, order_index)
             VALUES ($1, $2, $3, $4) RETURNING *`,
            [req.params.id, title, description || '', orderIndex || 0]
        );
        res.status(201).json(result.rows[0]);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// DELETE /api/course-management/:courseId/modules/:moduleId
app.delete('/api/course-management/:courseId/modules/:moduleId', async (req, res) => {
    try {
        await pool.query('DELETE FROM course_modules WHERE id = $1 AND course_id = $2',
            [req.params.moduleId, req.params.courseId]);
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ─── COURSE MANAGEMENT v2 — /courses/* routes (frontend-expected URL structure) ───

// GET /api/course-management/courses/:id/full — course + all modules + contents
app.get('/api/course-management/courses/:id/full', async (req, res) => {
    try {
        const result = await pool.query(
            'SELECT * FROM courses WHERE id = $1 AND deleted_at IS NULL', [req.params.id]
        );
        if (result.rows.length === 0) return res.status(404).json({ error: 'Course not found' });
        const modulesResult = await pool.query(
            'SELECT * FROM course_modules WHERE course_id = $1 ORDER BY order_index ASC', [req.params.id]
        );
        const modules = await Promise.all(modulesResult.rows.map(async (mod) => {
            const contentsResult = await pool.query(
                'SELECT * FROM course_module_contents WHERE module_id = $1 ORDER BY order_index ASC', [mod.id]
            );
            return {
                id: mod.id,
                title: mod.title,
                description: mod.description || '',
                orderIndex: mod.order_index,
                isActive: mod.is_active !== undefined ? mod.is_active : true,
                contents: contentsResult.rows.map(c => ({
                    id: c.id,
                    title: c.title,
                    contentType: c.content_type,
                    contentUrl: c.content_url,
                    description: c.description || '',
                    durationMinutes: c.duration_minutes,
                    orderIndex: c.order_index,
                    isActive: c.is_active,
                })),
            };
        }));
        res.json({ success: true, course: mapCourse(result.rows[0]), modules });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// POST /api/course-management/courses — create course (frontend CourseModal)
app.post('/api/course-management/courses', async (req, res) => {
    const { title, description, category, difficultyLevel, duration, basePrice,
            gstPercent, finalPrice, contentUrl, thumbnailUrl, isActive,
            certificateEnabled, instructorName, certificateImageUrl } = req.body;
    if (!title) return res.status(400).json({ error: 'title is required' });
    const slug = slugify(title) + '-' + Date.now();
    const bp = Number(basePrice ?? 0);
    const gst = Number(gstPercent ?? 18);
    const fp = Number(finalPrice ?? (bp * (1 + gst / 100)));
    try {
        const result = await pool.query(
            `INSERT INTO courses
             (title, slug, description, price, difficulty, thumbnail_url, is_published,
              category, difficulty_level, duration, base_price, gst_percent, final_price,
              content_url, is_active, certificate_enabled, instructor_name, certificate_image_url)
             VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18)
             RETURNING *`,
            [title, slug, description || '', bp, difficultyLevel || 'BEGINNER', thumbnailUrl || '',
             isActive !== false, category || '', difficultyLevel || 'BEGINNER', duration || '',
             bp, gst, fp, contentUrl || '', isActive !== false,
             certificateEnabled ?? false, instructorName || '', certificateImageUrl || '']
        );
        res.status(201).json({ success: true, course: mapCourse(result.rows[0]) });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// PUT /api/course-management/courses/:id — update course (frontend CourseModal + toggleCourseStatus)
app.put('/api/course-management/courses/:id', async (req, res) => {
    const { title, description, category, difficultyLevel, duration, basePrice,
            gstPercent, finalPrice, contentUrl, thumbnailUrl, isActive,
            certificateEnabled, instructorName, certificateImageUrl } = req.body;
    try {
        const result = await pool.query(
            `UPDATE courses SET
                title             = COALESCE($1, title),
                description       = COALESCE($2, description),
                category          = COALESCE($3, category),
                difficulty_level  = COALESCE($4, difficulty_level),
                difficulty        = COALESCE($4, difficulty),
                duration          = COALESCE($5, duration),
                base_price        = COALESCE($6, base_price),
                price             = COALESCE($6, price),
                gst_percent       = COALESCE($7, gst_percent),
                final_price       = COALESCE($8, final_price),
                content_url       = COALESCE($9, content_url),
                thumbnail_url     = COALESCE($10, thumbnail_url),
                is_active         = COALESCE($11, is_active),
                is_published      = COALESCE($11, is_published),
                certificate_enabled    = COALESCE($12, certificate_enabled),
                instructor_name        = COALESCE($13, instructor_name),
                certificate_image_url  = COALESCE($14, certificate_image_url),
                updated_at        = NOW()
             WHERE id = $15 AND deleted_at IS NULL RETURNING *`,
            [title, description, category, difficultyLevel, duration,
             basePrice != null ? Number(basePrice) : null,
             gstPercent != null ? Number(gstPercent) : null,
             finalPrice != null ? Number(finalPrice) : null,
             contentUrl, thumbnailUrl, isActive, certificateEnabled, instructorName,
             certificateImageUrl, req.params.id]
        );
        if (result.rows.length === 0) return res.status(404).json({ error: 'Course not found' });
        res.json({ success: true, course: mapCourse(result.rows[0]) });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// POST /api/course-management/courses/:courseId/modules
app.post('/api/course-management/courses/:courseId/modules', async (req, res) => {
    const { title, description, orderIndex } = req.body;
    if (!title) return res.status(400).json({ error: 'title is required' });
    try {
        const result = await pool.query(
            'INSERT INTO course_modules (course_id, title, description, order_index) VALUES ($1,$2,$3,$4) RETURNING *',
            [req.params.courseId, title, description || '', orderIndex ?? 0]
        );
        res.status(201).json({ success: true, module: result.rows[0] });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// DELETE /api/course-management/modules/:moduleId
app.delete('/api/course-management/modules/:moduleId', async (req, res) => {
    try {
        await pool.query('DELETE FROM course_modules WHERE id = $1', [req.params.moduleId]);
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// POST /api/course-management/modules/:moduleId/contents
app.post('/api/course-management/modules/:moduleId/contents', async (req, res) => {
    const { title, contentType, contentUrl, description, durationMinutes, orderIndex, courseId } = req.body;
    if (!title) return res.status(400).json({ error: 'title is required' });
    try {
        // resolve course_id from module if not supplied
        let cid = courseId;
        if (!cid) {
            const m = await pool.query('SELECT course_id FROM course_modules WHERE id = $1', [req.params.moduleId]);
            cid = m.rows[0]?.course_id;
        }
        const result = await pool.query(
            `INSERT INTO course_module_contents
             (module_id, course_id, title, content_type, content_url, description, duration_minutes, order_index)
             VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`,
            [req.params.moduleId, cid, title, contentType || 'video', contentUrl || '',
             description || '', durationMinutes ?? 0, orderIndex ?? 0]
        );
        res.status(201).json({ success: true, content: result.rows[0] });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// DELETE /api/course-management/contents/:contentId
app.delete('/api/course-management/contents/:contentId', async (req, res) => {
    try {
        await pool.query('DELETE FROM course_module_contents WHERE id = $1', [req.params.contentId]);
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ─── PUBLIC COURSES ───────────────────────────────────────────────────────────

// GET /api/courses — returns all non-deleted courses; callers filter client-side
app.get('/api/courses', async (req, res) => {
    try {
        const result = await pool.query(
            'SELECT * FROM courses WHERE deleted_at IS NULL ORDER BY created_at DESC'
        );
        res.json(result.rows.map(mapCourse));
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// POST /api/courses — create course (generic; accepts both Spring-style and simple field names)
app.post('/api/courses', async (req, res) => {
    const {
        title, description, syllabus, duration,
        price, basePrice,
        level, difficultyLevel,
        prerequisites,
        thumbnail, thumbnailUrl,
        category, gstPercent, finalPrice, contentUrl,
        isActive, certificateEnabled, instructorName, certificateImageUrl,
    } = req.body;
    if (!title) return res.status(400).json({ error: 'title is required' });
    const slug = slugify(title) + '-' + Date.now();
    const bp = Number(basePrice ?? price ?? 0);
    const gst = Number(gstPercent ?? 18);
    const fp = Number(finalPrice ?? (bp * (1 + gst / 100)));
    const diff = difficultyLevel || level || 'BEGINNER';
    const thumb = thumbnailUrl || thumbnail || '';
    try {
        const result = await pool.query(
            `INSERT INTO courses
             (title, slug, description, short_description, price, difficulty, thumbnail_url, is_published,
              category, difficulty_level, duration, base_price, gst_percent, final_price,
              content_url, is_active, certificate_enabled, instructor_name, certificate_image_url,
              prerequisites, syllabus)
             VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21)
             RETURNING *`,
            [title, slug, description || '', syllabus || '', bp, diff, thumb,
             isActive !== false, category || '', diff, duration || '', bp, gst, fp,
             contentUrl || '', isActive !== false, certificateEnabled ?? false,
             instructorName || '', certificateImageUrl || '', prerequisites || '', syllabus || '']
        );
        res.status(201).json({ success: true, course: mapCourse(result.rows[0]) });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// GET /api/courses/trash — trashed courses (must be BEFORE /:slugOrId)
app.get('/api/courses/trash', async (req, res) => {
    try {
        const result = await pool.query(
            'SELECT * FROM courses WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC'
        );
        res.json({ success: true, courses: result.rows.map(mapCourse) });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// PATCH /api/courses/:id/restore
app.patch('/api/courses/:id/restore', async (req, res) => {
    try {
        const result = await pool.query(
            'UPDATE courses SET deleted_at = NULL, updated_at = NOW() WHERE id = $1 RETURNING *',
            [req.params.id]
        );
        if (result.rows.length === 0) return res.status(404).json({ error: 'Course not found' });
        res.json({ success: true, course: mapCourse(result.rows[0]) });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// DELETE /api/courses/:id — soft delete
app.delete('/api/courses/:id', async (req, res) => {
    try {
        const result = await pool.query(
            'UPDATE courses SET deleted_at = NOW(), updated_at = NOW() WHERE id = $1 RETURNING id',
            [req.params.id]
        );
        if (result.rows.length === 0) return res.status(404).json({ error: 'Course not found' });
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// GET /api/courses/:slugOrId
app.get('/api/courses/:slugOrId', async (req, res) => {
    const { slugOrId } = req.params;
    try {
        const isId = /^\d+$/.test(slugOrId);
        const result = await pool.query(
            isId
                ? 'SELECT * FROM courses WHERE id = $1 AND deleted_at IS NULL'
                : 'SELECT * FROM courses WHERE slug = $1 AND deleted_at IS NULL',
            [slugOrId]
        );
        if (result.rows.length === 0) return res.status(404).json({ error: 'Not found' });
        const course = result.rows[0];
        const modules = await pool.query(
            'SELECT * FROM course_modules WHERE course_id = $1 ORDER BY order_index ASC',
            [course.id]
        );
        res.json({ ...mapCourse(course), modules: modules.rows });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ─── TEACHER ASSIGNMENTS ──────────────────────────────────────────────────────

// GET /api/courses/teacher/:teacherId
app.get('/api/courses/teacher/:teacherId', async (req, res) => {
    try {
        const result = await pool.query(
            `SELECT c.* FROM courses c
             JOIN teacher_courses tc ON tc.course_id = c.id
             WHERE tc.teacher_id = $1`,
            [req.params.teacherId]
        );
        res.json(result.rows);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// POST /api/course-management/:id/assign-teacher
app.post('/api/course-management/:id/assign-teacher', async (req, res) => {
    const { teacherId } = req.body;
    if (!teacherId) return res.status(400).json({ error: 'teacherId required' });
    try {
        await pool.query(
            'INSERT INTO teacher_courses (teacher_id, course_id) VALUES ($1, $2) ON CONFLICT DO NOTHING',
            [teacherId, req.params.id]
        );
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ─── STUDENT ENROLLMENT ───────────────────────────────────────────────────────

// POST /api/enrollments — admin enrolls a student
app.post('/api/enrollments', async (req, res) => {
    const { studentId, courseId, studentName, studentEmail } = req.body;
    if (!studentId || !courseId) return res.status(400).json({ error: 'studentId and courseId required' });
    try {
        await pool.query(
            `INSERT INTO student_enrollments (student_id, course_id, student_name, student_email)
             VALUES ($1, $2, $3, $4)
             ON CONFLICT (student_id, course_id) DO UPDATE
               SET student_name  = COALESCE(EXCLUDED.student_name, student_enrollments.student_name),
                   student_email = COALESCE(EXCLUDED.student_email, student_enrollments.student_email)`,
            [String(studentId), Number(courseId), studentName || null, studentEmail || null]
        );
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// POST /api/enrollments/bulk-assign — admin assigns multiple courses to a student
app.post('/api/enrollments/bulk-assign', async (req, res) => {
    const { userId, courseIds, studentName, studentEmail } = req.body;
    if (!userId || !Array.isArray(courseIds) || courseIds.length === 0) {
        return res.status(400).json({ error: 'userId and courseIds[] required' });
    }
    try {
        for (const courseId of courseIds) {
            await pool.query(
                `INSERT INTO student_enrollments (student_id, course_id, student_name, student_email)
                 VALUES ($1, $2, $3, $4)
                 ON CONFLICT (student_id, course_id) DO UPDATE
                   SET student_name  = COALESCE(EXCLUDED.student_name, student_enrollments.student_name),
                       student_email = COALESCE(EXCLUDED.student_email, student_enrollments.student_email)`,
                [String(userId), Number(courseId), studentName || null, studentEmail || null]
            );
        }
        res.json({ success: true, enrolled: courseIds.length });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ─── COURSE-BASED ENROLLMENT QUERY (Teacher/Admin view) ──────────────────────
// Returns all students enrolled in a specific course with progress and lab stats
const getEnrollmentsForCourse = async (courseId) => {
    const result = await pool.query(
        `SELECT
            se.id,
            se.student_id        AS "studentId",
            se.course_id         AS "courseId",
            se.student_name      AS "studentName",
            se.student_email     AS "studentEmail",
            se.enrolled_at       AS "enrolledAt",
            se.course_started_at AS "courseStartedAt",
            se.last_activity_at  AS "lastActivityAt",
            c.title              AS "courseTitle",
            COALESCE(sp.progress, 0)          AS progress,
            COALESCE(labs.lab_count, 0)       AS "labsSubmitted",
            COALESCE(labs.pending_count, 0)   AS "labsPending",
            COALESCE(labs.reviewed_count, 0)  AS "labsReviewed"
         FROM student_enrollments se
         JOIN courses c ON c.id = se.course_id
         LEFT JOIN (
             SELECT course_id, student_id,
                    ROUND(
                        100.0 * COUNT(*) /
                        NULLIF((SELECT COUNT(*) FROM course_lessons cl WHERE cl.course_id = sp2.course_id), 0)
                    ) AS progress
             FROM student_progress sp2
             WHERE sp2.course_id = $1
             GROUP BY course_id, student_id
         ) sp ON sp.course_id = se.course_id AND sp.student_id = se.student_id
         LEFT JOIN (
             SELECT course_id, student_id,
                    COUNT(*) AS lab_count,
                    COUNT(*) FILTER (WHERE status = 'SUBMITTED') AS pending_count,
                    COUNT(*) FILTER (WHERE status = 'REVIEWED')  AS reviewed_count
             FROM lab_submissions
             WHERE course_id = $1
             GROUP BY course_id, student_id
         ) labs ON labs.course_id = se.course_id AND labs.student_id = se.student_id
         WHERE se.course_id = $1
         ORDER BY se.enrolled_at DESC`,
        [Number(courseId)]
    );
    return result.rows.map(row => ({
        id: row.id,
        studentId: row.studentId,
        courseId: row.courseId,
        enrolledAt: row.enrolledAt,
        courseStartedAt: row.courseStartedAt,
        lastActivityAt: row.lastActivityAt,
        progress: Number(row.progress) || 0,
        labsSubmitted: Number(row.labsSubmitted) || 0,
        labsPending: Number(row.labsPending) || 0,
        labsReviewed: Number(row.labsReviewed) || 0,
        courseStarted: !!row.courseStartedAt,
        student: {
            id: row.studentId,
            full_name: row.studentName || row.studentEmail?.split('@')[0] || `Student ${row.studentId}`,
            email: row.studentEmail || '',
            avatar_url: null,
        },
    }));
};

// GET /api/enrollments?studentId=...  OR  /api/enrollments/:studentId
// Returns enrollment rows with embedded course data so the dashboard never shows blank cards
const getEnrollmentsForStudent = async (studentId) => {
    const result = await pool.query(
        `SELECT
            se.id,
            se.student_id   AS "studentId",
            se.course_id    AS "courseId",
            se.enrolled_at  AS "enrolledAt",
            c.id            AS "course_id_raw",
            c.title         AS "courseTitle",
            c.description   AS "courseDescription",
            c.short_description AS "courseShortDescription",
            c.thumbnail_url AS "courseThumbnail",
            c.difficulty    AS "difficultyLevel",
            c.price,
            c.is_published  AS "isPublished",
            COALESCE(sp.progress, 0) AS progress
         FROM student_enrollments se
         JOIN courses c ON c.id = se.course_id
         LEFT JOIN (
             SELECT course_id, student_id,
                    ROUND(
                        100.0 * COUNT(*) /
                        NULLIF((SELECT COUNT(*) FROM course_lessons cl WHERE cl.course_id = sp2.course_id), 0)
                    ) AS progress
             FROM student_progress sp2
             WHERE sp2.student_id = $1
             GROUP BY course_id, student_id
         ) sp ON sp.course_id = se.course_id
         WHERE se.student_id = $1
         ORDER BY se.enrolled_at DESC`,
        [studentId]
    );
    return result.rows.map(row => ({
        id: row.id,
        studentId: row.studentId,
        courseId: row.courseId,
        course_id: row.courseId,
        enrolledAt: row.enrolledAt,
        progress: Number(row.progress) || 0,
        courseTitle: row.courseTitle,
        courseDescription: row.courseDescription,
        courseThumbnail: row.courseThumbnail,
        difficultyLevel: row.difficultyLevel,
        courses: {
            id: row.courseId,
            title: row.courseTitle,
            description: row.courseDescription,
            short_description: row.courseShortDescription,
            thumbnail_url: row.courseThumbnail,
            difficulty: row.difficultyLevel,
            price: row.price,
            is_published: row.isPublished,
        },
    }));
};

// GET /api/enrollments?studentId=...
app.get('/api/enrollments', async (req, res) => {
    const { studentId, courseId } = req.query;
    // Support courseId query param for teacher/admin view
    if (courseId && !studentId) {
        try {
            const rows = await getEnrollmentsForCourse(courseId);
            return res.json(rows);
        } catch (err) {
            return res.status(500).json({ error: err.message });
        }
    }
    if (!studentId) return res.status(400).json({ error: 'studentId or courseId query param required' });
    try {
        res.json(await getEnrollmentsForStudent(studentId));
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// GET /api/enrollments/course/:courseId — enrolled students for a course (teacher/admin view)
app.get('/api/enrollments/course/:courseId', async (req, res) => {
    try {
        res.json(await getEnrollmentsForCourse(req.params.courseId));
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// GET /api/enrollments/student/:studentId
app.get('/api/enrollments/student/:studentId', async (req, res) => {
    try {
        res.json(await getEnrollmentsForStudent(req.params.studentId));
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// GET /api/enrollments/:studentId  (legacy — keep for backward compat)
app.get('/api/enrollments/:studentId', async (req, res) => {
    // Avoid matching named sub-routes
    if (['course', 'student', 'bulk-assign'].includes(req.params.studentId)) return;
    try {
        res.json(await getEnrollmentsForStudent(req.params.studentId));
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ─── PROGRESS ────────────────────────────────────────────────────────────────

// GET /api/progress/:courseId
app.get('/api/progress/:courseId', async (req, res) => {
    const authHeader = req.headers['authorization'] || '';
    const studentId = req.headers['x-user-id'] || 'unknown';
    try {
        const total = await pool.query(
            'SELECT COUNT(*) FROM course_lessons WHERE course_id = $1', [req.params.courseId]);
        const done = await pool.query(
            'SELECT COUNT(*) FROM student_progress WHERE course_id = $1 AND student_id = $2',
            [req.params.courseId, studentId]);
        const totalCount = parseInt(total.rows[0].count) || 0;
        const doneCount = parseInt(done.rows[0].count) || 0;
        res.json({
            percentComplete: totalCount > 0 ? Math.round((doneCount / totalCount) * 100) : 0,
            completedLessons: doneCount,
            totalLessons: totalCount
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// POST /api/progress/mark-complete
app.post('/api/progress/mark-complete', async (req, res) => {
    const { courseId, lessonId } = req.body;
    const studentId = req.headers['x-user-id'] || 'unknown';
    try {
        await pool.query(
            'INSERT INTO student_progress (student_id, course_id, lesson_id) VALUES ($1, $2, $3) ON CONFLICT DO NOTHING',
            [studentId, courseId, lessonId]
        );
        res.json({ success: true });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ─── PARTNERS ────────────────────────────────────────────────────────────────

app.get('/api/partners', async (req, res) => {
    try {
        await pool.query(`
            CREATE TABLE IF NOT EXISTS partners (
                id SERIAL PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                logo_url TEXT,
                website_url TEXT,
                created_at TIMESTAMPTZ DEFAULT NOW()
            )
        `);
        const result = await pool.query('SELECT * FROM partners ORDER BY name ASC');
        res.json(result.rows);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ─── HERO BANNERS ─────────────────────────────────────────────────────────────

// GET /api/banners — public, returns active banners sorted by sort_order
app.get('/api/banners', async (req, res) => {
    try {
        const result = await pool.query(
            'SELECT * FROM hero_banners WHERE is_active = true ORDER BY sort_order ASC, id ASC'
        );
        res.json(result.rows.map(r => ({
            id: r.id,
            title: r.title,
            subtitle: r.subtitle,
            img: r.img,
            badge: r.badge,
            width: r.width,
            btn1Text: r.btn1_text,
            btn1Link: r.btn1_link,
            btn2Text: r.btn2_text,
            btn2Link: r.btn2_link,
            features: r.features || [],
            proof: r.proof,
            sortOrder: r.sort_order,
            buttons: [
                ...(r.btn1_text ? [{ text: r.btn1_text, link: r.btn1_link, page: r.btn1_link, class: 'btn-primary' }] : []),
                ...(r.btn2_text ? [{ text: r.btn2_text, link: r.btn2_link, page: r.btn2_link, class: 'btn-secondary' }] : [])
            ]
        })));
    } catch (err) {
        console.error(err);
        res.status(500).json({ error: err.message });
    }
});

// POST /api/banners — create banner
app.post('/api/banners', async (req, res) => {
    const { title, subtitle, img, badge, width, btn1Text, btn1Link, btn2Text, btn2Link, features, proof, sortOrder } = req.body;
    if (!title) return res.status(400).json({ error: 'title is required' });
    try {
        const result = await pool.query(
            `INSERT INTO hero_banners (title, subtitle, img, badge, width, btn1_text, btn1_link, btn2_text, btn2_link, features, proof, sort_order)
             VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12) RETURNING *`,
            [title, subtitle || '', img || '', badge || '', width || 'split',
             btn1Text || '', btn1Link || '', btn2Text || '', btn2Link || '',
             JSON.stringify(features || []), proof || '', sortOrder || 0]
        );
        res.status(201).json(result.rows[0]);
    } catch (err) {
        console.error(err);
        res.status(500).json({ error: err.message });
    }
});

// PUT /api/banners/:id — update banner
app.put('/api/banners/:id', async (req, res) => {
    const { title, subtitle, img, badge, width, btn1Text, btn1Link, btn2Text, btn2Link, features, proof, sortOrder, isActive } = req.body;
    try {
        const result = await pool.query(
            `UPDATE hero_banners SET
                title = COALESCE($1, title),
                subtitle = COALESCE($2, subtitle),
                img = COALESCE($3, img),
                badge = COALESCE($4, badge),
                width = COALESCE($5, width),
                btn1_text = COALESCE($6, btn1_text),
                btn1_link = COALESCE($7, btn1_link),
                btn2_text = COALESCE($8, btn2_text),
                btn2_link = COALESCE($9, btn2_link),
                features = COALESCE($10, features),
                proof = COALESCE($11, proof),
                sort_order = COALESCE($12, sort_order),
                is_active = COALESCE($13, is_active),
                updated_at = NOW()
             WHERE id = $14 RETURNING *`,
            [title, subtitle, img, badge, width,
             btn1Text, btn1Link, btn2Text, btn2Link,
             features ? JSON.stringify(features) : null,
             proof, sortOrder, isActive, req.params.id]
        );
        if (result.rows.length === 0) return res.status(404).json({ error: 'Banner not found' });
        res.json(result.rows[0]);
    } catch (err) {
        console.error(err);
        res.status(500).json({ error: err.message });
    }
});

// DELETE /api/banners/:id — soft delete
app.delete('/api/banners/:id', async (req, res) => {
    try {
        await pool.query(
            'UPDATE hero_banners SET is_active = false, updated_at = NOW() WHERE id = $1',
            [req.params.id]
        );
        res.json({ success: true });
    } catch (err) {
        console.error(err);
        res.status(500).json({ error: err.message });
    }
});

// ─── GOOGLE DRIVE UPLOAD ─────────────────────────────────────────────────────
// Uses Node.js built-in crypto + fetch — no googleapis package needed

const driveTokenCache = { oauth: { token: null, expiry: 0 }, jwt: { token: null, expiry: 0 } };
const driveFolderCache = {};

function b64url(buf) {
    return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
}

async function getDriveToken() {
    const now = Date.now();

    // OAuth path (preferred — uses user's quota)
    if (process.env.GOOGLE_REFRESH_TOKEN) {
        if (driveTokenCache.oauth.token && now < driveTokenCache.oauth.expiry) {
            return driveTokenCache.oauth.token;
        }
        const resp = await fetch('https://oauth2.googleapis.com/token', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({
                grant_type: 'refresh_token',
                refresh_token: process.env.GOOGLE_REFRESH_TOKEN,
                client_id: process.env.GOOGLE_CLIENT_ID,
                client_secret: process.env.GOOGLE_CLIENT_SECRET
            })
        });
        const data = await resp.json();
        if (!data.access_token) throw new Error(`OAuth token refresh failed: ${JSON.stringify(data)}`);
        driveTokenCache.oauth.token = data.access_token;
        driveTokenCache.oauth.expiry = now + 55 * 60 * 1000;
        return driveTokenCache.oauth.token;
    }

    // Fall back to service account JWT
    if (driveTokenCache.jwt.token && now < driveTokenCache.jwt.expiry - 60000) {
        return driveTokenCache.jwt.token;
    }
    const raw = process.env.GOOGLE_SERVICE_ACCOUNT_JSON;
    if (!raw) throw new Error('GOOGLE_SERVICE_ACCOUNT_JSON env var is not set');
    const creds = JSON.parse(raw);
    const privateKey = creds.private_key.replace(/\\n/g, '\n'); // handle dotenv escaping
    const nowSec = Math.floor(Date.now() / 1000);
    const header = b64url(Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT' })));
    const payload = b64url(Buffer.from(JSON.stringify({
        iss: creds.client_email,
        scope: 'https://www.googleapis.com/auth/drive.file',
        aud: 'https://oauth2.googleapis.com/token',
        exp: nowSec + 3600,
        iat: nowSec
    })));
    const signer = createSign('RSA-SHA256');
    signer.update(`${header}.${payload}`);
    signer.end();
    const sig = b64url(signer.sign(privateKey));
    const jwtResp = await fetch('https://oauth2.googleapis.com/token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
            grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            assertion: `${header}.${payload}.${sig}`
        })
    });
    const jwtData = await jwtResp.json();
    if (!jwtData.access_token) throw new Error(`Drive auth failed: ${JSON.stringify(jwtData)}`);
    driveTokenCache.jwt.token = jwtData.access_token;
    driveTokenCache.jwt.expiry = Date.now() + (jwtData.expires_in || 3600) * 1000;
    return driveTokenCache.jwt.token;
}

async function driveGetOrCreateFolder(token, name, parentId) {
    const cacheKey = `${parentId || 'root'}__${name}`;
    if (driveFolderCache[cacheKey]) return driveFolderCache[cacheKey];
    const q = `name='${name.replace(/'/g, "\\'")}'and mimeType='application/vnd.google-apps.folder' and trashed=false and '${parentId || 'root'}' in parents`;
    const searchResp = await fetch(
        `https://www.googleapis.com/drive/v3/files?q=${encodeURIComponent(q)}&fields=files(id)&spaces=drive`,
        { headers: { Authorization: `Bearer ${token}` } }
    );
    const searchData = await searchResp.json();
    if (searchData.files?.length > 0) {
        driveFolderCache[cacheKey] = searchData.files[0].id;
        return searchData.files[0].id;
    }
    const createResp = await fetch('https://www.googleapis.com/drive/v3/files', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({
            name,
            mimeType: 'application/vnd.google-apps.folder',
            parents: [parentId || 'root']
        })
    });
    const folder = await createResp.json();
    driveFolderCache[cacheKey] = folder.id;
    return folder.id;
}

// GET /auth/google/drive — redirects to Google OAuth consent page
app.get('/auth/google/drive', (req, res) => {
    const clientId = process.env.GOOGLE_CLIENT_ID;
    if (!clientId) return res.status(500).send('GOOGLE_CLIENT_ID not set in environment');
    const callbackBase = process.env.OAUTH_CALLBACK_BASE_URL || 'http://localhost:8082';
    const params = new URLSearchParams({
        client_id: clientId,
        redirect_uri: `${callbackBase}/auth/google/callback`,
        response_type: 'code',
        scope: 'https://www.googleapis.com/auth/drive.file',
        access_type: 'offline',
        prompt: 'consent'
    });
    res.redirect(`https://accounts.google.com/o/oauth2/v2/auth?${params}`);
});

// GET /auth/google/callback — exchanges code for tokens, writes GOOGLE_REFRESH_TOKEN to .env
app.get('/auth/google/callback', async (req, res) => {
    const { code, error } = req.query;
    if (error) return res.status(400).send(`OAuth error: ${error}`);
    if (!code) return res.status(400).send('No authorization code received');

    try {
        const callbackBase = process.env.OAUTH_CALLBACK_BASE_URL || 'http://localhost:8082';
        const tokenResp = await fetch('https://oauth2.googleapis.com/token', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({
                code,
                client_id: process.env.GOOGLE_CLIENT_ID,
                client_secret: process.env.GOOGLE_CLIENT_SECRET,
                redirect_uri: `${callbackBase}/auth/google/callback`,
                grant_type: 'authorization_code'
            })
        });
        const tokens = await tokenResp.json();
        if (!tokens.refresh_token) {
            return res.status(400).send(`No refresh token received. Response: ${JSON.stringify(tokens)}`);
        }

        // Set in-process env immediately (works until restart)
        process.env.GOOGLE_REFRESH_TOKEN = tokens.refresh_token;

        // Try to persist to .env file (works on local/VPS; may fail on containerised platforms)
        let savedToFile = false;
        try {
            const pathMod = await import('path');
            const fsMod = await import('fs');
            const envFile = pathMod.default.join(process.cwd(), '.env');
            let envContent = fsMod.default.readFileSync(envFile, 'utf8');
            if (envContent.includes('GOOGLE_REFRESH_TOKEN=')) {
                envContent = envContent.replace(/GOOGLE_REFRESH_TOKEN=.*/, `GOOGLE_REFRESH_TOKEN=${tokens.refresh_token}`);
            } else {
                envContent += `\nGOOGLE_REFRESH_TOKEN=${tokens.refresh_token}`;
            }
            fsMod.default.writeFileSync(envFile, envContent);
            savedToFile = true;
        } catch (_) { /* read-only filesystem — token shown below */ }

        res.send(`<!DOCTYPE html><html><head><meta charset="utf-8">
            <style>body{font-family:sans-serif;max-width:640px;margin:40px auto;padding:0 20px}
            code{background:#f4f4f4;padding:4px 8px;border-radius:4px;word-break:break-all;display:block;margin:8px 0}
            .box{border:2px solid #22c55e;border-radius:8px;padding:16px;background:#f0fdf4}</style></head><body>
            <h2>&#x2705; Google Drive authorized!</h2>
            <div class="box">
            <p><strong>Refresh Token:</strong></p>
            <code id="rt">${tokens.refresh_token}</code>
            <button onclick="navigator.clipboard.writeText(document.getElementById('rt').textContent)">Copy</button>
            </div>
            ${savedToFile
                ? '<p>&#x1F4BE; Token saved to <code>.env</code> automatically.</p>'
                : '<p>&#x26A0;&#xFE0F; Could not write to <code>.env</code> on this server. Copy the token above and add it manually as the <code>GOOGLE_REFRESH_TOKEN</code> environment variable, then restart the server.</p>'
            }
            <p>Uploads from the admin panel will now go to your Google Drive.</p>
        </body></html>`);
    } catch (err) {
        res.status(500).send(`Token exchange failed: ${err.message}`);
    }
});

// GET /api/upload-image/test — diagnostic: confirms whether Drive is configured
app.get('/api/upload-image/test', (req, res) => {
    const raw = process.env.GOOGLE_SERVICE_ACCOUNT_JSON;
    const hasRefreshToken = !!process.env.GOOGLE_REFRESH_TOKEN;
    const hasClientId = !!process.env.GOOGLE_CLIENT_ID;

    if (hasRefreshToken) {
        return res.json({ configured: true, method: 'oauth', ready: true, authUrl: null });
    }
    if (!raw) return res.json({ configured: false, method: 'none', error: 'No credentials configured' });
    try {
        const creds = JSON.parse(raw);
        res.json({
            configured: true,
            method: 'service_account',
            email: creds.client_email,
            warning: 'Service account cannot upload to personal Drive. Visit /auth/google/drive to authorize.',
            authUrl: hasClientId ? `${process.env.OAUTH_CALLBACK_BASE_URL || 'http://localhost:8082'}/auth/google/drive` : null
        });
    } catch (e) {
        res.json({ configured: false, error: 'Invalid GOOGLE_SERVICE_ACCOUNT_JSON' });
    }
});

// POST /api/upload-image — receives raw binary, uploads to Google Drive
// Query params: folder (e.g. 'banners'), filename
app.post('/api/upload-image', express.raw({ type: '*/*', limit: '25mb' }), async (req, res) => {
    if (!req.body || !req.body.length) return res.status(400).json({ error: 'No file data received' });
    if (!process.env.GOOGLE_REFRESH_TOKEN && !process.env.GOOGLE_SERVICE_ACCOUNT_JSON) {
        return res.status(500).json({ error: 'No Google Drive credentials configured. Visit /auth/google/drive to authorize.' });
    }
    try {
        const token = await getDriveToken();
        const folderSegments = (req.query.folder || 'general').split('/').filter(Boolean);
        const filename = req.query.filename || `upload_${Date.now()}`;
        const mimeType = req.query.mimeType || req.headers['content-type'] || 'application/octet-stream';

        // Build folder path: CyberLearnix > [segments...]
        const rootId = process.env.GOOGLE_DRIVE_ROOT_FOLDER_ID
            || await driveGetOrCreateFolder(token, 'CyberLearnix', null);
        let folderId = rootId;
        for (const part of folderSegments) {
            folderId = await driveGetOrCreateFolder(token, part, folderId);
        }

        // Multipart upload to Drive
        const boundary = `----driveupload${Date.now()}`;
        const meta = JSON.stringify({ name: filename, parents: [folderId] });
        const body = Buffer.concat([
            Buffer.from(`--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${meta}\r\n--${boundary}\r\nContent-Type: ${mimeType}\r\n\r\n`),
            req.body,
            Buffer.from(`\r\n--${boundary}--`)
        ]);
        const uploadResp = await fetch(
            'https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name',
            {
                method: 'POST',
                headers: {
                    Authorization: `Bearer ${token}`,
                    'Content-Type': `multipart/related; boundary=${boundary}`,
                    'Content-Length': String(body.length)
                },
                body
            }
        );
        const uploaded = await uploadResp.json();
        if (!uploaded.id) return res.status(500).json({ error: 'Drive upload failed', detail: uploaded });

        // Make file publicly readable
        await fetch(`https://www.googleapis.com/drive/v3/files/${uploaded.id}/permissions`, {
            method: 'POST',
            headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({ role: 'reader', type: 'anyone' })
        });

        res.json({ url: `/api/drive/image/${uploaded.id}`, fileId: uploaded.id });
    } catch (err) {
        console.error('Drive upload error:', err);
        res.status(500).json({ error: err.message });
    }
});

// ─── DRIVE IMAGE PROXY ────────────────────────────────────────────────────────
// GET /api/drive/image/:fileId — proxy-streams a Drive file (images, docs) to the client.
// No auth required — files are already made public when uploaded.
app.get('/api/drive/image/:fileId', async (req, res) => {
    const fileId = req.params.fileId;
    if (!fileId || !/^[a-zA-Z0-9_\-]{10,100}$/.test(fileId)) {
        return res.status(400).json({ error: 'Invalid file ID' });
    }
    if (!process.env.GOOGLE_REFRESH_TOKEN && !process.env.GOOGLE_SERVICE_ACCOUNT_JSON) {
        return res.status(503).json({ error: 'Google Drive not configured' });
    }
    try {
        const token = await getDriveToken();
        const driveRes = await fetch(
            `https://www.googleapis.com/drive/v3/files/${fileId}?alt=media`,
            { headers: { Authorization: `Bearer ${token}` } }
        );
        if (!driveRes.ok) {
            return res.status(driveRes.status).json({ error: 'Drive fetch failed' });
        }
        const contentType = driveRes.headers.get('content-type') || 'application/octet-stream';
        res.setHeader('Content-Type', contentType);
        res.setHeader('Cache-Control', 'public, max-age=86400');
        driveRes.body.pipe(res);
    } catch (err) {
        console.error('Drive image proxy error:', err);
        res.status(500).json({ error: err.message });
    }
});

// ─── HEALTH CHECK ─────────────────────────────────────────────────────────────
app.get('/api/health', (req, res) => res.json({ status: 'ok', port: PORT }));

// Start
initDB().then(() => {
    app.listen(PORT, () => {
        console.log(`🚀 Cyberlearnix API server running on http://localhost:${PORT}`);
        console.log(`   Health check: http://localhost:${PORT}/api/health`);
    });
});
