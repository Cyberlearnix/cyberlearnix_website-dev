package com.cyberlearnix.cms.config;

import com.cyberlearnix.shared.entity.cms.*;
import com.cyberlearnix.shared.repository.cms.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class CmsDatabaseSeeder implements CommandLineRunner {

    @Autowired private PageRepository pageRepository;
    @Autowired private PageSectionRepository sectionRepository;
    @Autowired private PageComponentRepository componentRepository;
    @Autowired private TestimonialRepository testimonialRepository;
    @Autowired private RecognitionRepository recognitionRepository;

    @Override
    public void run(String... args) {
        // Run seeding in a background thread to prevent slow DB connections or lock timeouts
        // from blocking the main startup thread and failing the Kubernetes readiness probe.
        Thread seedingThread = new Thread(() -> {
            try {
                System.out.println("[CmsSeeder] Background thread started. Waiting 5s for context setup...");
                Thread.sleep(5000);
                performSeeding();
            } catch (InterruptedException e) {
                System.err.println("[CmsSeeder] Seeding thread interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("[CmsSeeder] Error in seeding thread: " + e.getMessage());
            }
        }, "CmsSeeding-Thread");
        
        seedingThread.setDaemon(true);
        seedingThread.start();
        System.out.println("[CmsSeeder] Seeding thread scheduled in background.");
    }

    private void performSeeding() {
        System.out.println("[CmsSeeder] Starting database seeding check...");

        // ── Seed Testimonials ─────────────────────────────────────────────────
        try {
            if (testimonialRepository.count() == 0) {
                System.out.println("[CmsSeeder] Seeding testimonials...");
                seedTestimonials();
            } else {
                System.out.println("[CmsSeeder] Testimonials already exist.");
            }
        } catch (Exception e) {
            System.err.println("[CmsSeeder] Error seeding testimonials: " + e.getMessage());
        }

        // ── Seed Recognitions ─────────────────────────────────────────────────
        try {
            if (recognitionRepository.count() == 0) {
                System.out.println("[CmsSeeder] Seeding recognitions...");
                seedRecognitions();
            } else {
                System.out.println("[CmsSeeder] Recognitions already exist.");
            }
        } catch (Exception e) {
            System.err.println("[CmsSeeder] Error seeding recognitions: " + e.getMessage());
        }

        // ── Seed Pages ────────────────────────────────────────────────────────
        try {
            if (pageRepository.count() > 0) {
                System.out.println("[CmsSeeder] Pages already exist. Skipping page seeding.");
            } else {
                System.out.println("[CmsSeeder] Seeding all website pages...");
                seedAllPages();
                System.out.println("[CmsSeeder] Page seeding complete.");
            }
        } catch (Exception e) {
            System.err.println("[CmsSeeder] Error seeding pages: " + e.getMessage());
        }

        System.out.println("[CmsSeeder] Seeding process finished.");
    }

    // =========================================================================
    //  PAGE SEEDING – All website pages
    // =========================================================================
    private void seedAllPages() {
        try {
            // 1. HOME PAGE
            Page home = page("home", "Enterprise Digital Resilience & Innovation",
                "Pioneering global technology consulting and premium cyber services.",
                "Cyberlearnix — Cybersecurity Courses & IT Training",
                "Cyberlearnix offers industry-leading cybersecurity training, ethical hacking courses, and IT certification programs.");
            try {
                sectionRepository.save(mkSection(home, SectionLayoutType.SINGLE_COLUMN, 1));
            } catch (Exception e) {
                System.err.println("[CmsSeeder] Error seeding section for home: " + e.getMessage());
            }

            // 2. ABOUT PAGE
            Page about = page("about", "About Cyberlearnix",
                "Crafting futuristic engineering and trusted digital transformations since 2018.",
                "About Us — Cyberlearnix",
                "Learn about Cyberlearnix's mission, team, history and recognitions in cybersecurity education and IT consulting.");
            try {
                sectionRepository.save(mkSection(about, SectionLayoutType.SINGLE_COLUMN, 1));
            } catch (Exception e) {
                System.err.println("[CmsSeeder] Error seeding section for about: " + e.getMessage());
            }

            // 3. COURSES / ACADEMY PAGE
            page("courses", "Strategic Capabilities & Courses",
                "Core technological vectors driving organizational success. Industry-recognized cybersecurity certifications.",
                "Cybersecurity Courses & Training — Cyberlearnix",
                "Browse Cyberlearnix's catalog of cybersecurity, ethical hacking, cloud security, and IT courses.");

            // 4. SERVICES PAGE
            page("services", "Our Services",
                "Holistic consulting, custom programming, cloud modernization, and enterprise security.",
                "IT & Cybersecurity Services — Cyberlearnix",
                "Cyberlearnix provides technology consulting, software development, AI automation, cloud modernization, and digital transformation services.");

            // 5. CONTACT PAGE
            page("contact", "Get in Touch",
                "Have a question or project? Reach out to our team and we'll get back to you within 24 hours.",
                "Contact Cyberlearnix — Talk to Our Team",
                "Contact Cyberlearnix for course inquiries, partnership opportunities, or technology consulting engagements.");

            // 6. CAREERS PAGE
            page("careers", "Careers at Cyberlearnix",
                "Join our mission to build a safer digital world. We're always looking for passionate, skilled professionals.",
                "Careers — Join the Cyberlearnix Team",
                "Explore job openings at Cyberlearnix. We hire cybersecurity instructors, developers, mentors, and business professionals.");

            // 7. TECH CONSULTING
            Page techConsulting = page("tech-consulting", "Technology Consulting",
                "Strategic architecture formulation and system engineering advice for modern enterprises.",
                "Technology Consulting Services — Cyberlearnix",
                "Cyberlearnix technology consulting helps businesses modernize infrastructure, align scaling with business demands, and perform IT audits.");
            addGridSection(techConsulting, "Consulting Streams", List.of(
                item("🏛️", "Corporate Architecture", "Establishing modern, distributed microservices layouts and API models."),
                item("📈", "Transformation Strategy", "Clear timeline formulations for database upgrades and digital scale-ups."),
                item("⚖️", "IT Audits & Governance", "Conducting system analysis to guarantee alignment with ISO standards.")
            ));

            // 8. SOFTWARE DEVELOPMENT
            Page softwareDev = page("software-dev", "Software Development",
                "Custom applications engineered with a secure, performant foundation.",
                "Custom Software Development — Cyberlearnix",
                "Cyberlearnix builds robust, scalable software tailored to your business — from backend microservices to modern web frontends.");
            addGridSection(softwareDev, "Engineering Expertise", List.of(
                item("☕", "Backend Systems", "High-throughput microservices engineered down to the byte."),
                item("⚛️", "Web Frameworks", "Single-page web shells with instant interactions and fluid transitions."),
                item("🌉", "API Integration", "Secure web hooks and middleware linking CRM and ERP ecosystems.")
            ));

            // 9. AI & AUTOMATION
            Page aiAutomation = page("ai-automation", "AI & Automation Systems",
                "Cognitive automation and intelligent agents engineered securely.",
                "AI & Automation Services — Cyberlearnix",
                "Cyberlearnix builds custom AI agents, semantic databases, and safe inference pipelines for enterprise automation.");
            addGridSection(aiAutomation, "Cognitive Solutions", List.of(
                item("🤖", "Agentic Workflows", "Multi-agent coordination loops resolving multi-step business process tasks."),
                item("🧬", "Semantic Databases", "Retrieval-augmented generation pipelines backed by high-dimensional vector grids."),
                item("🛡️", "Safe Inference", "Self-hosted AI pipelines preventing private data leakage to external models.")
            ));

            // 10. CLOUD MODERNIZATION
            Page cloudInfra = page("cloud-infra", "Cloud Modernization",
                "High-performance cloud architectures with declarative DevSecOps configurations.",
                "Cloud Modernization & DevSecOps — Cyberlearnix",
                "Cyberlearnix migrates and modernizes cloud workloads on AWS, Azure, and private hypervisors using GitOps and zero-trust security.");
            addGridSection(cloudInfra, "Infrastructure Paradigms", List.of(
                item("🐳", "Orchestration & Meshes", "Kubernetes worker clusters managed declaratively via automated configuration loops."),
                item("♾️", "GitOps Pipelines", "Deployment tracks driven by immutable configuration changes and instant rollback triggers."),
                item("🔒", "Zero Trust Security", "Micro-segmented network policies and automatic rotate-key logistics.")
            ));

            // 11. DIGITAL TRANSFORMATION
            Page digitalTx = page("digital-transformation", "Digital Transformation",
                "Future-proofing workflows with modern, secure digital infrastructures.",
                "Digital Transformation Consulting — Cyberlearnix",
                "Cyberlearnix evaluates your existing operations and formulates scalable engineering frameworks to modernize enterprise delivery systems.");
            addGridSection(digitalTx, "Modernization Pillars", List.of(
                item("⚙️", "Process Automation", "Replacing manual spreadsheet chains with high-fidelity system automation tracks."),
                item("📊", "Telemetry & Observability", "Comprehensive database profiling dashboards displaying system execution logs in real time."),
                item("🎓", "Squad Acceleration", "Training client-side staff to implement modern GitOps and software engineering habits.")
            ));

            // 12. ENROLL PAGE
            page("enroll", "Enroll Now",
                "Take the next step in your cybersecurity career. Apply for your chosen course today.",
                "Enroll in Cybersecurity Courses — Cyberlearnix",
                "Apply to Cyberlearnix's cybersecurity and IT training programs. Enroll online and start your journey today.");

            // 13. STUDENT DASHBOARD (meta)
            page("student-dashboard", "Student Dashboard",
                "Your personal learning hub — track courses, assignments, and certificates.",
                "Student Dashboard — Cyberlearnix",
                "Access your Cyberlearnix student dashboard to view enrolled courses, track progress, and download certificates.");

            // 14. PRIVACY POLICY
            page("privacy", "Privacy Policy",
                "How we collect, use, and protect your personal information.",
                "Privacy Policy — Cyberlearnix",
                "Read Cyberlearnix's privacy policy to understand how your data is handled.");

            // 15. TERMS OF SERVICE
            page("terms", "Terms of Service",
                "The terms and conditions that govern your use of Cyberlearnix products and services.",
                "Terms of Service — Cyberlearnix",
                "Read the Terms of Service for using Cyberlearnix courses, platforms, and consulting services.");
        } catch (Exception e) {
            System.err.println("[CmsSeeder] Error during seedAllPages: " + e.getMessage());
        }
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================
    private Page page(String slug, String title, String subtitle, String metaTitle, String metaDesc) {
        Page p = new Page();
        p.setSlug(slug);
        p.setTitle(title);
        p.setSubtitle(subtitle);
        p.setMetaTitle(metaTitle);
        p.setMetaDescription(metaDesc);
        p.setTemplateName("default");
        p.setIsPublished(true);
        p.setPublishedAt(LocalDateTime.now());
        p.setShowInMenu(false);
        return pageRepository.save(p);
    }

    private PageSection mkSection(Page page, SectionLayoutType layout, int order) {
        PageSection sec = new PageSection();
        sec.setPage(page);
        sec.setLayoutType(layout);
        sec.setOrderIndex(order);
        return sec;
    }

    private void addGridSection(Page page, String title, List<Map<String, String>> items) {
        try {
            PageSection sec = sectionRepository.save(mkSection(page, SectionLayoutType.GRID, 1));
            Map<String, Object> data = new HashMap<>();
            data.put("type", "grid");
            data.put("title", title);
            data.put("items", items);
            PageComponent comp = new PageComponent();
            comp.setSection(sec);
            comp.setComponentType(ComponentType.TABS);
            comp.setComponentData(data);
            comp.setOrderIndex(1);
            componentRepository.save(comp);
        } catch (Exception e) {
            System.err.println("[CmsSeeder] Error seeding grid section for page " + page.getSlug() + ": " + e.getMessage());
        }
    }

    private Map<String, String> item(String icon, String title, String desc) {
        Map<String, String> m = new HashMap<>();
        m.put("icon", icon);
        m.put("title", title);
        m.put("desc", desc);
        return m;
    }

    // =========================================================================
    //  TESTIMONIALS
    // =========================================================================
    private void seedTestimonials() {
        saveTestimonial("Rahul Sharma", "Security Analyst, TCS",
            "The cybersecurity course at Cyberlearnix completely transformed my career. Practical labs and real-world scenarios made the learning exceptional.",
            "https://i.pravatar.cc/150?u=rahul");
        saveTestimonial("Priya Menon", "IT Manager, Infosys",
            "Incredible hands-on training. The instructors are industry veterans and the curriculum is aligned with CEH and CompTIA standards.",
            "https://i.pravatar.cc/150?u=priya");
        saveTestimonial("John Doe", "CTO, TechCorp Global",
            "Cyberlearnix delivered robust engineering pipelines, completely upgrading our hybrid environments to modern container orchestrations.",
            "https://i.pravatar.cc/150?u=john");
        saveTestimonial("Jane Smith", "VP Digital, FinStream",
            "Their technology advisory team restructured our database topology, raising our compliance posture across global operations.",
            "https://i.pravatar.cc/150?u=jane");
        saveTestimonial("Aditya Kumar", "Ethical Hacker, Wipro",
            "Best cybersecurity training institute I've attended. The penetration testing module alone is worth the entire program fee.",
            "https://i.pravatar.cc/150?u=aditya");
        saveTestimonial("Sunita Reddy", "CISO, HCL Technologies",
            "Cyberlearnix's team-focused training helped upskill our entire security team. Professional, structured, and highly effective.",
            "https://i.pravatar.cc/150?u=sunita");
    }

    private void saveTestimonial(String name, String role, String feedback, String imageUrl) {
        try {
            Testimonial t = new Testimonial();
            t.setName(name); t.setRole(role);
            t.setFeedback(feedback); t.setImageUrl(imageUrl);
            testimonialRepository.save(t);
        } catch (Exception e) {
            System.err.println("[CmsSeeder] Error saving testimonial: " + e.getMessage());
        }
    }

    // =========================================================================
    //  RECOGNITIONS / CERTIFICATIONS
    // =========================================================================
    private void seedRecognitions() {
        saveRecognition(
            "MSME Registered",
            "Registered under the Ministry of Micro, Small & Medium Enterprises, Government of India.",
            "Government of India",
            "UDYAM-TS-09-0168097",
            "Lifetime",
            "",
            ""
        );
        saveRecognition(
            "ISO 9001:2015 Certified",
            "Quality Management Systems certificate validating our delivery accuracy, operational governance, and customer retainment structures.",
            "ICV Assessments",
            "IN/76822750/4851",
            "21 Jan 2029",
            "",
            "https://www.iafcertsearch.org/certification/ZwBlDqRlP6fmzllyS3PtAkgy"
        );
        saveRecognition(
            "NASSCOM Member",
            "Recognised member of the National Association of Software and Service Companies — India's apex IT industry body.",
            "NASSCOM",
            "N/A",
            "Annual",
            "",
            "https://www.nasscom.in"
        );
    }

    private void saveRecognition(String title, String desc, String authority, String certNo, String validUntil, String logoUrl, String verifyUrl) {
        try {
            Recognition r = new Recognition();
            r.setTitle(title); r.setDescription(desc);
            r.setAuthority(authority); r.setCertificateNo(certNo);
            r.setValidUntil(validUntil); r.setLogoUrl(logoUrl); r.setVerifyUrl(verifyUrl);
            recognitionRepository.save(r);
        } catch (Exception e) {
            System.err.println("[CmsSeeder] Error saving recognition: " + e.getMessage());
        }
    }
}
