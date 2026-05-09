package com.cyberlearnix.course.config;

import com.cyberlearnix.shared.entity.course.*;
import com.cyberlearnix.shared.repository.course.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class DataSeeder implements CommandLineRunner {

    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final ModuleContentRepository contentRepository;
    private final CourseTeacherRepository courseTeacherRepository;

    public DataSeeder(CourseRepository courseRepository,
                      CourseModuleRepository moduleRepository,
                      ModuleContentRepository contentRepository,
                      CourseTeacherRepository courseTeacherRepository) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.contentRepository = contentRepository;
        this.courseTeacherRepository = courseTeacherRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Only seed if no courses exist
        if (courseRepository.count() > 0) {
            System.out.println("📊 Courses already exist, skipping seed");
            return;
        }

        System.out.println("🌱 Seeding sample data...");

        // Create sample courses
        Course course1 = createCourse(
            "Cybersecurity Fundamentals",
            "Learn the basics of cybersecurity, including network security, cryptography, and threat analysis. This comprehensive course covers essential security concepts for beginners.",
            "Cybersecurity",
            "Beginner",
            "8 weeks"
        );

        Course course2 = createCourse(
            "Web Application Security",
            "Master web security concepts including OWASP Top 10, SQL injection, XSS, and secure coding practices. Advanced training for developers and security professionals.",
            "Cybersecurity",
            "Intermediate",
            "10 weeks"
        );

        Course course3 = createCourse(
            "Penetration Testing",
            "Learn ethical hacking techniques, vulnerability assessment, and penetration testing methodologies. Hands-on course with real-world scenarios and lab exercises.",
            "Cybersecurity",
            "Advanced",
            "12 weeks"
        );

        Course course4 = createCourse(
            "Network Security",
            "Deep dive into network security protocols, firewall configuration, intrusion detection systems, and secure network architecture design.",
            "Cybersecurity",
            "Intermediate",
            "8 weeks"
        );

        Course course5 = createCourse(
            "Cloud Security",
            "Secure cloud infrastructure on AWS, Azure, and GCP. Learn about IAM policies, encryption, compliance, and cloud-specific security best practices.",
            "Cybersecurity",
            "Advanced",
            "10 weeks"
        );

        course1 = courseRepository.save(course1);
        course2 = courseRepository.save(course2);
        course3 = courseRepository.save(course3);
        course4 = courseRepository.save(course4);
        course5 = courseRepository.save(course5);

        // Assign creator as teacher for each course
        assignTeacher(course1.getId(), "system");
        assignTeacher(course2.getId(), "system");
        assignTeacher(course3.getId(), "system");
        assignTeacher(course4.getId(), "system");
        assignTeacher(course5.getId(), "system");

        // Create modules for Cybersecurity Fundamentals
        CourseModule module1 = createModule(course1, "Introduction to Cybersecurity", "Overview of cybersecurity landscape, threats, and career paths", 0);
        CourseModule module2 = createModule(course1, "Network Security Basics", "Fundamental concepts of network security and protocols", 1);
        CourseModule module3 = createModule(course1, "Cryptography Essentials", "Understanding encryption, hashing, and cryptographic protocols", 2);

        module1 = moduleRepository.save(module1);
        module2 = moduleRepository.save(module2);
        module3 = moduleRepository.save(module3);

        // Create modules for Web Application Security
        CourseModule module4 = createModule(course2, "OWASP Top 10", "Most critical web security risks and how to mitigate them", 0);
        CourseModule module5 = createModule(course2, "SQL Injection Attacks", "Understanding and preventing SQL injection vulnerabilities", 1);
        CourseModule module6 = createModule(course2, "Cross-Site Scripting (XSS)", "XSS attack types, prevention, and secure coding practices", 2);

        module4 = moduleRepository.save(module4);
        module5 = moduleRepository.save(module5);
        module6 = moduleRepository.save(module6);

        // Create modules for Penetration Testing
        CourseModule module7 = createModule(course3, "Reconnaissance", "Information gathering techniques and tools", 0);
        CourseModule module8 = createModule(course3, "Vulnerability Assessment", "Identifying and documenting security vulnerabilities", 1);
        CourseModule module9 = createModule(course3, "Exploitation", "Ethical hacking techniques and responsible disclosure", 2);

        module7 = moduleRepository.save(module7);
        module8 = moduleRepository.save(module8);
        module9 = moduleRepository.save(module9);

        // Create content for modules
        createLectureContent(module1, "What is Cybersecurity?", "Introduction to cybersecurity concepts and importance", 0);
        createLectureContent(module1, "CIA Triad", "Confidentiality, Integrity, and Availability principles", 1);
        createLectureContent(module2, "TCP/IP Fundamentals", "Understanding network protocols and security", 0);
        createLectureContent(module3, "Symmetric Encryption", "AES, DES, and other symmetric encryption algorithms", 0);

        createLectureContent(module4, "Injection Overview", "Introduction to injection attacks", 0);
        createLabContent(module5, "SQL Injection Demo", "Hands-on SQL injection examples and prevention", 0);
        createLectureContent(module6, "XSS Types", "Stored, Reflected, and DOM-based XSS", 0);

        createLectureContent(module7, "Passive Reconnaissance", "OSINT gathering techniques", 0);
        createLabContent(module8, "Active Scanning", "Network and application scanning tools", 0);
        createLectureContent(module9, "Metasploit Basics", "Introduction to Metasploit framework", 0);

        System.out.println("✅ Sample data seeded successfully");
    }

    private Course createCourse(String title, String description, String category, 
                                 String difficultyLevel, String duration) {
        Course course = new Course();
        course.setTitle(title);
        course.setDescription(description);
        course.setCategory(category);
        course.setDifficultyLevel(difficultyLevel);
        course.setDuration(duration);
        course.setBasePrice(0.0);
        course.setGstPercent(18);
        course.setFinalPrice(0.0);
        course.setIsActive(true);
        course.setStatus("APPROVED");
        course.setCreatedBy("system");
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());
        return course;
    }

    private CourseModule createModule(Course course, String title, String description, int orderIndex) {
        CourseModule module = new CourseModule();
        module.setCourse(course);
        module.setTitle(title);
        module.setDescription(description);
        module.setOrderIndex(orderIndex);
        module.setCreatedAt(LocalDateTime.now());
        module.setUpdatedAt(LocalDateTime.now());
        return module;
    }

    private void assignTeacher(Long courseId, String teacherId) {
        CourseTeacher ct = new CourseTeacher();
        ct.setCourseId(courseId);
        ct.setTeacherId(teacherId);
        courseTeacherRepository.save(ct);
    }

    private LectureContent createLectureContent(CourseModule module, String title, String description, int orderIndex) {
        LectureContent content = new LectureContent();
        content.setModule(module);
        content.setTitle(title);
        content.setDescription(description);
        content.setContentType("LECTURE");
        content.setOrderIndex(orderIndex);
        content.setCreatedAt(LocalDateTime.now());
        content.setUpdatedAt(LocalDateTime.now());
        contentRepository.save(content);
        return content;
    }

    private LabContent createLabContent(CourseModule module, String title, String description, int orderIndex) {
        LabContent content = new LabContent();
        content.setModule(module);
        content.setTitle(title);
        content.setDescription(description);
        content.setContentType("LAB");
        content.setOrderIndex(orderIndex);
        content.setCreatedAt(LocalDateTime.now());
        content.setUpdatedAt(LocalDateTime.now());
        contentRepository.save(content);
        return content;
    }
}
