package com.cyberlearnix.cms.config;

import com.cyberlearnix.shared.entity.cms.*;
import com.cyberlearnix.shared.repository.cms.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class CmsDatabaseSeeder implements CommandLineRunner {

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private PageSectionRepository sectionRepository;

    @Autowired
    private PageComponentRepository componentRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (pageRepository.count() > 0) {
            System.out.println("Pages already present in database. Skipping CMS seeding.");
            return;
        }

        System.out.println("Starting CMS database seeding...");

        // 1. Home Page
        createSimplePage("home", "Enterprise Digital Resilience & Innovation", 
            "Pioneering global technology consulting and premium cyber services.");

        // 2. About Page
        createSimplePage("about", "About Us", 
            "Crafting futuristic engineering and trusted digital transformations.");

        // 3. Services Page
        createSimplePage("services", "Our Services", 
            "Holistic consulting, custom programming, cloud modernization, and security.");

        // 4. Courses Page
        createSimplePage("courses", "Strategic Capabilities", 
            "Core technological vectors driving organizational success.");

        // 5. Tech Consulting Page
        createTechConsultingPage();

        // 6. Software Development Page
        createSoftwareDevPage();

        // 7. AI & Automation Systems Page
        createAiAutomationPage();

        // 8. Cloud Modernization Page
        createCloudInfraPage();

        // 9. Digital Transformation Page
        createDigitalTransformationPage();

        System.out.println("CMS database seeding completed successfully!");
    }

    private void createSimplePage(String slug, String title, String subtitle) {
        Page page = new Page();
        page.setSlug(slug);
        page.setTitle(title);
        page.setSubtitle(subtitle);
        page.setTemplateName("default");
        page.setIsPublished(true);
        page.setPublishedAt(LocalDateTime.now());
        pageRepository.save(page);
    }

    private void createTechConsultingPage() {
        Page page = new Page();
        page.setSlug("tech-consulting");
        page.setTitle("Technology Consulting");
        page.setSubtitle("Strategic architecture formulation and system engineering advice.");
        page.setTemplateName("default");
        page.setIsPublished(true);
        page.setPublishedAt(LocalDateTime.now());
        page = pageRepository.save(page);

        // Section 1: Introduction text
        PageSection sec1 = createSection(page, SectionLayoutType.SINGLE_COLUMN, 1);
        Map<String, Object> textData = new HashMap<>();
        textData.put("type", "text");
        textData.put("content", "Our technical consulting cohort helps corporations modernize their infrastructure, align application scaling parameters with real business demands, and conduct comprehensive technical threat model analyses.");
        createComponent(sec1, ComponentType.TEXT, textData, 1);

        // Section 2: Grid items
        PageSection sec2 = createSection(page, SectionLayoutType.GRID, 2);
        Map<String, Object> gridData = new HashMap<>();
        gridData.put("type", "grid");
        gridData.put("title", "Consulting Streams");
        List<Map<String, String>> items = new ArrayList<>();
        items.add(createItem("🏛️", "Corporate Architecture", "Establishing modern, distributed microservices layouts and API models."));
        items.add(createItem("📈", "Transformation Strategy", "Clear timeline formulations for database upgrades and digital scale-ups."));
        items.add(createItem("⚖️", "IT Audits & Governance", "Conducting system analysis to guarantee absolute alignment with ISO standards."));
        gridData.put("items", items);
        createComponent(sec2, ComponentType.TABS, gridData, 1);

        // Section 3: CTA
        PageSection sec3 = createSection(page, SectionLayoutType.SINGLE_COLUMN, 3);
        Map<String, Object> ctaData = new HashMap<>();
        ctaData.put("type", "cta");
        ctaData.put("text", "Engage with our Advisory Cohort today.");
        ctaData.put("btn", "Schedule Consultation");
        ctaData.put("target", "contact");
        createComponent(sec3, ComponentType.BANNER, ctaData, 1);
    }

    private void createSoftwareDevPage() {
        Page page = new Page();
        page.setSlug("software-dev");
        page.setTitle("Software Development");
        page.setSubtitle("Custom applications engineered with a secure, performant foundation.");
        page.setTemplateName("default");
        page.setIsPublished(true);
        page.setPublishedAt(LocalDateTime.now());
        page = pageRepository.save(page);

        // Section 1
        PageSection sec1 = createSection(page, SectionLayoutType.SINGLE_COLUMN, 1);
        Map<String, Object> textData = new HashMap<>();
        textData.put("type", "text");
        textData.put("content", "We write robust software tailored for your business domains. From heavy transactional backend services in Spring Boot/Node to sleek, modern React workflows, our code is tested to perform unconditionally.");
        createComponent(sec1, ComponentType.TEXT, textData, 1);

        // Section 2
        PageSection sec2 = createSection(page, SectionLayoutType.GRID, 2);
        Map<String, Object> gridData = new HashMap<>();
        gridData.put("type", "grid");
        gridData.put("title", "Engineering Expertise");
        List<Map<String, String>> items = new ArrayList<>();
        items.add(createItem("☕", "Backend Systems", "High-throughput microservices engineered down to the byte."));
        items.add(createItem("⚛️", "Web Frameworks", "Single-page web shells with instant interactions and fluid transitions."));
        items.add(createItem("🌉", "API Integration", "Secure web hooks and middleware linking CRM and ERP ecosystems."));
        gridData.put("items", items);
        createComponent(sec2, ComponentType.TABS, gridData, 1);

        // Section 3
        PageSection sec3 = createSection(page, SectionLayoutType.SINGLE_COLUMN, 3);
        Map<String, Object> ctaData = new HashMap<>();
        ctaData.put("type", "cta");
        ctaData.put("text", "Accelerate your software roadmap.");
        ctaData.put("btn", "Talk to an Engineer");
        ctaData.put("target", "contact");
        createComponent(sec3, ComponentType.BANNER, ctaData, 1);
    }

    private void createAiAutomationPage() {
        Page page = new Page();
        page.setSlug("ai-automation");
        page.setTitle("AI & Automation Systems");
        page.setSubtitle("Cognitive automation and intelligent agents engineered securely.");
        page.setTemplateName("default");
        page.setIsPublished(true);
        page.setPublishedAt(LocalDateTime.now());
        page = pageRepository.save(page);

        // Section 1
        PageSection sec1 = createSection(page, SectionLayoutType.SINGLE_COLUMN, 1);
        Map<String, Object> textData = new HashMap<>();
        textData.put("type", "text");
        textData.put("content", "Leverage custom large language models, structured knowledge graphs, and agentic workflows to automate repetitive cognitive operations while maintaining strict data boundary constraints.");
        createComponent(sec1, ComponentType.TEXT, textData, 1);

        // Section 2
        PageSection sec2 = createSection(page, SectionLayoutType.GRID, 2);
        Map<String, Object> gridData = new HashMap<>();
        gridData.put("type", "grid");
        gridData.put("title", "Cognitive Solutions");
        List<Map<String, String>> items = new ArrayList<>();
        items.add(createItem("🤖", "Agentic Workflows", "Multi-agent coordination loops resolving multi-step business process tasks."));
        items.add(createItem("🧬", "Semantic Databases", "Retrieval-augmented generation pipelines backed by high-dimensional vector grids."));
        items.add(createItem("🛡️", "Safe Inference", "Self-hosted AI pipelines preventing private data leakage to external models."));
        gridData.put("items", items);
        createComponent(sec2, ComponentType.TABS, gridData, 1);

        // Section 3
        PageSection sec3 = createSection(page, SectionLayoutType.SINGLE_COLUMN, 3);
        Map<String, Object> ctaData = new HashMap<>();
        ctaData.put("type", "cta");
        ctaData.put("text", "Design an intelligent agent pilot.");
        ctaData.put("btn", "Request AI Audit");
        ctaData.put("target", "contact");
        createComponent(sec3, ComponentType.BANNER, ctaData, 1);
    }

    private void createCloudInfraPage() {
        Page page = new Page();
        page.setSlug("cloud-infra");
        page.setTitle("Cloud Modernization");
        page.setSubtitle("High-performance cloud architectures with declarative DevSecOps configurations.");
        page.setTemplateName("default");
        page.setIsPublished(true);
        page.setPublishedAt(LocalDateTime.now());
        page = pageRepository.save(page);

        // Section 1
        PageSection sec1 = createSection(page, SectionLayoutType.SINGLE_COLUMN, 1);
        Map<String, Object> textData = new HashMap<>();
        textData.put("type", "text");
        textData.put("content", "Transition workloads seamlessly using GitOps pipelines, zero-downtime container meshes, and micro-segmented security perimeters across AWS, Azure, or private hypervisors.");
        createComponent(sec1, ComponentType.TEXT, textData, 1);

        // Section 2
        PageSection sec2 = createSection(page, SectionLayoutType.GRID, 2);
        Map<String, Object> gridData = new HashMap<>();
        gridData.put("type", "grid");
        gridData.put("title", "Infrastructure Paradigms");
        List<Map<String, String>> items = new ArrayList<>();
        items.add(createItem("🐳", "Orchestration & Meshes", "Kubernetes worker clusters managed declaratively via automated configuration loops."));
        items.add(createItem("♾️", "GitOps Pipelines", "Deployment tracks driven by immutable configuration changes and instant rollback triggers."));
        items.add(createItem("🔒", "Zero Trust Security", "Micro-segmented network policies and automatic rotate-key logistics."));
        gridData.put("items", items);
        createComponent(sec2, ComponentType.TABS, gridData, 1);

        // Section 3
        PageSection sec3 = createSection(page, SectionLayoutType.SINGLE_COLUMN, 3);
        Map<String, Object> ctaData = new HashMap<>();
        ctaData.put("type", "cta");
        ctaData.put("text", "Evaluate your cloud efficiency.");
        ctaData.put("btn", "Talk to Cloud Architect");
        ctaData.put("target", "contact");
        createComponent(sec3, ComponentType.BANNER, ctaData, 1);
    }

    private void createDigitalTransformationPage() {
        Page page = new Page();
        page.setSlug("digital-transformation");
        page.setTitle("Digital Transformation");
        page.setSubtitle("Future-proofing workflows with modern, secure digital infrastructures.");
        page.setTemplateName("default");
        page.setIsPublished(true);
        page.setPublishedAt(LocalDateTime.now());
        page = pageRepository.save(page);

        // Section 1
        PageSection sec1 = createSection(page, SectionLayoutType.SINGLE_COLUMN, 1);
        Map<String, Object> textData = new HashMap<>();
        textData.put("type", "text");
        textData.put("content", "We evaluate existing operational footprints, identify structural execution bottlenecks, and formulate scalable engineering frameworks to modernize enterprise delivery systems.");
        createComponent(sec1, ComponentType.TEXT, textData, 1);

        // Section 2
        PageSection sec2 = createSection(page, SectionLayoutType.GRID, 2);
        Map<String, Object> gridData = new HashMap<>();
        gridData.put("type", "grid");
        gridData.put("title", "Modernization Pillars");
        List<Map<String, String>> items = new ArrayList<>();
        items.add(createItem("⚙️", "Process Automation", "Replacing manual spreadsheet chains with high-fidelity system automation tracks."));
        items.add(createItem("📊", "Telemetry & Observability", "Comprehensive database profiling dashboards displaying system execution logs in real time."));
        items.add(createItem("🎓", "Squad Acceleration", "Training client-side staff to implement modern GitOps and software engineering habits."));
        gridData.put("items", items);
        createComponent(sec2, ComponentType.TABS, gridData, 1);

        // Section 3
        PageSection sec3 = createSection(page, SectionLayoutType.SINGLE_COLUMN, 3);
        Map<String, Object> ctaData = new HashMap<>();
        ctaData.put("type", "cta");
        ctaData.put("text", "Map your technological shift.");
        ctaData.put("btn", "Consult Advisory Cohort");
        ctaData.put("target", "contact");
        createComponent(sec3, ComponentType.BANNER, ctaData, 1);
    }

    private PageSection createSection(Page page, SectionLayoutType layout, int orderIndex) {
        PageSection sec = new PageSection();
        sec.setPage(page);
        sec.setLayoutType(layout);
        sec.setOrderIndex(orderIndex);
        return sectionRepository.save(sec);
    }

    private void createComponent(PageSection sec, ComponentType type, Map<String, Object> data, int orderIndex) {
        PageComponent comp = new PageComponent();
        comp.setSection(sec);
        comp.setComponentType(type);
        comp.setComponentData(data);
        comp.setOrderIndex(orderIndex);
        componentRepository.save(comp);
    }

    private Map<String, String> createItem(String icon, String title, String desc) {
        Map<String, String> item = new HashMap<>();
        item.put("icon", icon);
        item.put("title", title);
        item.put("desc", desc);
        return item;
    }
}
