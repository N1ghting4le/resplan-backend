package com.resplan.config;

import com.resplan.domain.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.time.DayOfWeek.*;

/** Демонстрационные данные – тот же набор, что и в прототипе интерфейса (ЛР 6) и тестовых данных БД (ЛР 7) */
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoData implements ApplicationRunner {

    /** Пароль всех демонстрационных учетных записей */
    public static final String DEMO_PASSWORD = "resplan";

    private final EntityManager em;
    private final PasswordEncoder passwordEncoder;
    private final Map<String, Grade> grades = new HashMap<>();
    private final Map<String, Location> locations = new HashMap<>();
    private final Map<String, Skill> skills = new HashMap<>();

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        location("Минск", "Беларусь", Set.of(SATURDAY, SUNDAY), "2026-11-07", "2026-12-25");
        location("Варшава", "Польша", Set.of(SATURDAY, SUNDAY), "2026-11-11", "2026-12-25");
        location("Ташкент", "Узбекистан", Set.of(SATURDAY, SUNDAY), "2026-10-01", "2026-12-08");
        location("Эр-Рияд", "Саудовская Аравия", Set.of(FRIDAY, SATURDAY), "2026-09-23");
        String[] gradeNames = {"Junior", "Middle", "Senior", "Lead"};
        for (int i = 0; i < gradeNames.length; i++) grades.put(gradeNames[i], persist(new Grade(gradeNames[i], i + 1)));

        Employee e1 = employee("Ковалёв", "Алексей", "Middle", "Минск", 22, "Java:4", "Spring:4", "PostgreSQL:3", "Docker:3");
        Employee e2 = employee("Чен", "Вэй", "Lead", "Варшава", 38, "Java:5", "Spring:5", "Kubernetes:4", "System design:5");
        Employee e3 = employee("Белова", "Ирина", "Senior", "Минск", 30, "Python:5", "LLM:4", "RAG:4", "PyTorch:4");
        Employee e4 = employee("Алиев", "Тимур", "Middle", "Ташкент", 20, "Python:4", "LLM:3", "NLP:3", "SQL:4");
        Employee e5 = employee("Хасан", "Омар", "Senior", "Эр-Рияд", 32, "Python:5", "LLM:5", "RAG:3", "MLOps:4");
        Employee e6 = employee("Новик", "Павел", "Senior", "Минск", 26, "React:5", "TypeScript:5", "MUI:4");
        Employee e7 = employee("Ткач", "Анна", "Middle", "Варшава", 18, "Test automation:4", "Playwright:4", "Java:3");
        Employee e8 = employee("Лис", "Сергей", "Senior", "Минск", 24, "Test automation:5", "Selenium:4", "Performance testing:3");
        Employee e9 = employee("Юсупова", "Карина", "Junior", "Ташкент", 12, "Manual testing:3", "Playwright:2", "Test automation:2");
        Employee e10 = employee("Зелински", "Марек", "Senior", "Варшава", 28, "Kubernetes:5", "AWS:4", "Terraform:4");
        Employee e11 = employee("Козлова", "Дарья", "Middle", "Минск", 20, "BPMN:4", "UML:4", "SQL:3");
        Employee e12 = employee("Ибраев", "Нурлан", "Senior", "Ташкент", 25, "Java:5", "Spring:5", "Kafka:4");

        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        AppUser pm = persist(new AppUser("pm", hash, UserRole.PM, employee("Иванов", "Дмитрий", "Lead", "Минск", 35)));
        AppUser pm2 = persist(new AppUser("pm2", hash, UserRole.PM, employee("Петров", "Олег", "Lead", "Ташкент", 30)));
        AppUser rm = persist(new AppUser("rm", hash, UserRole.RM, employee("Санчес", "Мария", "Senior", "Варшава", 30)));
        AppUser ld = persist(new AppUser("ld", hash, UserRole.LD, employee("Дженкинс", "Сара", "Senior", "Варшава", 25)));
        persist(new AppUser("emp", hash, UserRole.EMP, e1));
        persist(new AppUser("emp2", hash, UserRole.EMP, e9));

        Project atlas = persist(new Project("ATLAS", "GenAI-ассистент для банковского контакт-центра", pm,
                locations.get("Минск"), 1, LocalDate.parse("2026-10-05")));
        Project orion = persist(new Project("ORION", "Миграция e-commerce платформы в облако", pm,
                locations.get("Варшава"), 2, LocalDate.parse("2026-10-05")));
        Project helix = persist(new Project("HELIX", "Аналитическая платформа для сети клиник", pm2,
                locations.get("Ташкент"), 2, LocalDate.parse("2026-10-05")));

        Task t1 = task(atlas, "Анализ требований", 5, e11);
        Task t2 = task(atlas, "Проектирование архитектуры", 4, e2, t1);
        Task t3 = task(atlas, "Подготовка датасетов", 6, e4, t1);
        Task t4 = task(atlas, "Backend API", 10, e1, t2);
        Task t5 = task(atlas, "RAG-модуль GenAI", 12, null, t2, t3);
        Task t6 = task(atlas, "Web-интерфейс оператора", 8, e6, t2);
        Task t7 = task(atlas, "Интеграция модулей", 4, e1, t4, t5, t6);
        Task t8 = task(atlas, "Системное тестирование", 5, null, t7);
        task(atlas, "Развертывание у заказчика", 2, e10, t8);
        task(atlas, "Пользовательская документация", 3, null, t6);
        Task t11 = task(orion, "Аудит инфраструктуры", 5, e10);
        Task t12 = task(orion, "Перенос сервисов каталога", 15, e1, t11);
        Task t13 = task(orion, "Регрессионное тестирование", 10, e7, t12);
        task(orion, "Переключение трафика", 2, e10, t13);
        Task h1 = task(helix, "CI/CD и облачная инфраструктура", 35, null);

        ProjectBooking kovalevAtlas = hard(e1, atlas, "2026-10-14", "2026-10-27", 100, rm);
        ProjectBooking kovalevOrion = hard(e1, orion, "2026-10-19", "2026-11-06", 50, rm);
        hard(e2, atlas, "2026-10-12", "2026-10-15", 50, rm);
        hard(e2, helix, "2026-10-05", "2026-12-18", 50, rm);
        hard(e3, helix, "2026-10-05", "2026-10-30", 100, rm);
        training(e3, "AWS Certified ML Specialty", "2026-11-02", "2026-11-06", 100, ld);
        persist(new ProjectBooking(e4, orion, BookingKind.SOFT, LocalDate.parse("2026-10-05"),
                LocalDate.parse("2026-10-16"), 50, rm));
        training(e5, "Сертификация Azure AI Engineer", "2026-10-26", "2026-10-30", 100, ld);
        hard(e6, atlas, "2026-10-15", "2026-10-26", 100, rm);
        hard(e7, orion, "2026-10-05", "2026-11-13", 100, rm);
        hard(e8, helix, "2026-10-05", "2026-11-06", 50, rm);
        ProjectBooking zelinskiOrion = hard(e10, orion, "2026-10-05", "2026-12-18", 80, rm);
        ProjectBooking zelinskiHelix = hard(e10, helix, "2026-11-02", "2026-11-20", 40, rm);
        hard(e11, atlas, "2026-10-05", "2026-10-09", 100, rm);
        hard(e12, helix, "2026-10-05", "2026-12-18", 100, rm);
        training(e1, "Курс «Spring AI: основы»", "2026-11-16", "2026-11-18", 50, ld);

        // Ресурсные конфликты (BR-08), выявленные до запуска демонстрационного стенда (UC-6)
        persist(new ResourceConflict(kovalevAtlas, kovalevOrion));
        persist(new ResourceConflict(zelinskiOrion, zelinskiHelix));

        ResourceRequest r1 = request(t5, "ML-инженер (GenAI)", "Senior", 100, "2026-10-20", "2026-11-04", pm,
                "Python:4", "LLM:4", "RAG:3");
        r1.proposeCandidate(persist(new ProjectBooking(e4, atlas, BookingKind.SOFT, r1.getStartDate(),
                r1.getEndDate(), 100, rm)));
        request(t8, "QA-инженер", "Middle", 100, "2026-11-11", "2026-11-17", pm, "Test automation:3", "Playwright:3");
        request(h1, "DevOps-инженер", "Senior", 50, "2026-10-12", "2026-11-30", pm2, "Kubernetes:4", "AWS:3");
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private void location(String city, String calendarName, Set<DayOfWeek> weekend, String... holidays) {
        Set<LocalDate> dates = new java.util.HashSet<>();
        for (String h : holidays) dates.add(LocalDate.parse(h));
        WorkCalendar calendar = persist(new WorkCalendar(calendarName, weekend, dates));
        locations.put(city, persist(new Location(city, calendar)));
    }

    private Skill skill(String name) {
        return skills.computeIfAbsent(name, n -> persist(new Skill(n, Set.of("LLM", "RAG", "NLP", "PyTorch").contains(n))));
    }

    private Employee employee(String last, String first, String grade, String city, int rate, String... skillLevels) {
        Employee e = new Employee(last, first, grades.get(grade), locations.get(city), BigDecimal.valueOf(rate));
        for (String s : skillLevels) {
            String[] p = s.split(":");
            e.addSkill(skill(p[0]), Integer.parseInt(p[1]));
        }
        return persist(e);
    }

    private Task task(Project project, String name, int duration, Employee assignee, Task... predecessors) {
        Task t = new Task(project, name, duration, predecessors);
        if (assignee != null) t.assign(assignee);
        return persist(t);
    }

    private ProjectBooking hard(Employee e, Project p, String from, String to, int load, AppUser by) {
        return persist(new ProjectBooking(e, p, BookingKind.HARD, LocalDate.parse(from), LocalDate.parse(to), load, by));
    }

    private void training(Employee e, String course, String from, String to, int load, AppUser by) {
        TrainingCourse c = persist(new TrainingCourse(course, "EPAM University"));
        persist(new TrainingBooking(e, c, LocalDate.parse(from), LocalDate.parse(to), load, true, by));
    }

    private ResourceRequest request(Task task, String role, String grade, int load, String from, String to,
                                    AppUser pm, String... skillLevels) {
        ResourceRequest r = new ResourceRequest(task, role, grades.get(grade), load,
                LocalDate.parse(from), LocalDate.parse(to), pm);
        for (String s : skillLevels) {
            String[] p = s.split(":");
            r.requireSkill(skill(p[0]), Integer.parseInt(p[1]));
        }
        return persist(r);
    }
}
