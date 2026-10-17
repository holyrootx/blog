package me.jsjlog.blog.admin.service;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import me.jsjlog.blog.admin.domain.Menu;
import me.jsjlog.blog.admin.domain.MenuType;
import me.jsjlog.blog.admin.dto.AdminCategoryRequest;
import me.jsjlog.blog.admin.dto.MenuRequest;
import me.jsjlog.blog.admin.repository.MenuRepository;
import me.jsjlog.blog.common.exception.BlogException;
import me.jsjlog.blog.common.exception.ErrorCode;
import me.jsjlog.blog.common.security.MemberPrincipal;
import me.jsjlog.blog.member.domain.Member;
import me.jsjlog.blog.member.repository.MemberRepository;
import me.jsjlog.blog.post.domain.Category;
import me.jsjlog.blog.post.repository.CategoryRepository;
import me.jsjlog.blog.support.MySqlTestDatabase;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 조회 시각은 같지만 커밋 순서가 겹치는 실제 트랜잭션과 HTTP 충돌 응답을 검사한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MenuCategoryConcurrencyTest.ProbeConfiguration.class)
class MenuCategoryConcurrencyTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        MySqlTestDatabase.configure(properties, "menu_category_concurrency");
    }
    @Autowired MenuService menuService;
    @Autowired AdminCategoryService categoryService;
    @Autowired MenuRepository menus;
    @Autowired CategoryRepository categories;
    @Autowired MemberRepository members;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ReadProbe probe;
    TransactionTemplate tx;
    Menu menu;
    Category category;
    Member admin;

    @BeforeEach void fixtures() {
        tx = new TransactionTemplate(transactions);
        String key = UUID.randomUUID().toString();
        tx.executeWithoutResult(status -> {
            menu = menus.save(new Menu("기존 메뉴", "설명", MenuType.GROUP, null, 1L, true));
            category = categories.save(new Category("분류-" + key, 1L));
            admin = members.save(Member.ofLocalAdmin("admin-" + key, "unused", "관리자"));
        });
        // 응답에 쓰는 DB 정밀도의 시각을 그대로 사용한다.
        menu = menus.findById(menu.getId()).orElseThrow();
        category = categories.findById(category.getId()).orElseThrow();
    }

    @Test void simultaneousMenuUpdatesRejectTheStaleRequest() throws Exception {
        race(() -> menuService.updateMenu(menu.getId(), menuRequest(menu.getUpdatedAt(), "먼저 저장", 2L, false)),
                () -> menuService.updateMenu(menu.getId(), menuRequest(menu.getUpdatedAt(), "나중 저장", 3L, true)), false);
        Menu saved = menus.findById(menu.getId()).orElseThrow();
        assertThat(saved.getMenuName()).isEqualTo("먼저 저장");
        assertThat(saved.getSortOrder()).isEqualTo(2L);
        assertThat(saved.getVisible()).isFalse();
    }

    @Test void simultaneousCategoryUpdatesRejectTheStaleRequest() throws Exception {
        String first = "첫-" + category.getId();
        race(() -> categoryService.updateCategory(category.getId(), new AdminCategoryRequest(category.getUpdatedAt(), first, 2L)),
                () -> categoryService.updateCategory(category.getId(), new AdminCategoryRequest(category.getUpdatedAt(), "둘-" + category.getId(), 3L)), false);
        Category saved = categories.findById(category.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo(first);
        assertThat(saved.getSortOrder()).isEqualTo(2L);
    }

    @Test void rolledBackMenuUpdateDoesNotRejectTheWaitingRequest() throws Exception {
        race(() -> menuService.updateMenu(menu.getId(), menuRequest(menu.getUpdatedAt(), "취소", 2L, false)),
                () -> menuService.updateMenu(menu.getId(), menuRequest(menu.getUpdatedAt(), "유효", 3L, true)), true);
        assertThat(menus.findById(menu.getId()).orElseThrow().getMenuName()).isEqualTo("유효");
    }

    @Test void rolledBackCategoryUpdateDoesNotRejectTheWaitingRequest() throws Exception {
        String valid = "유효-" + category.getId();
        race(() -> categoryService.updateCategory(category.getId(), new AdminCategoryRequest(category.getUpdatedAt(), "취소-" + category.getId(), 2L)),
                () -> categoryService.updateCategory(category.getId(), new AdminCategoryRequest(category.getUpdatedAt(), valid, 3L)), true);
        assertThat(categories.findById(category.getId()).orElseThrow().getName()).isEqualTo(valid);
    }

    @Test void staleMenuRequestReturnsConflictAndLatestVersionCanSave() throws Exception {
        menuService.updateMenu(menu.getId(), menuRequest(menu.getUpdatedAt(), "첫 저장", 2L, false));
        mvc.perform(put("/api/v1/admin/blog/menus/" + menu.getId())
                .with(user(MemberPrincipal.ofLocal(admin))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(menuRequest(menu.getUpdatedAt(), "덮어쓰기", 3L, true))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODIFIED_BY_OTHERS"));
        Menu latest = menus.findById(menu.getId()).orElseThrow();
        menuService.updateMenu(menu.getId(), menuRequest(latest.getUpdatedAt(), "새 조회 후 저장", 4L, true));
        assertThat(menus.findById(menu.getId()).orElseThrow().getMenuName()).isEqualTo("새 조회 후 저장");
    }

    @Test void staleCategoryRequestReturnsConflictAndLatestVersionCanSave() throws Exception {
        categoryService.updateCategory(category.getId(), new AdminCategoryRequest(category.getUpdatedAt(), "변경-" + category.getId(), 2L));
        mvc.perform(put("/api/v1/admin/blog/categories/" + category.getId())
                .with(user(MemberPrincipal.ofLocal(admin))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AdminCategoryRequest(category.getUpdatedAt(), "덮기-" + category.getId(), 3L))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODIFIED_BY_OTHERS"));
        Category latest = categories.findById(category.getId()).orElseThrow();
        categoryService.updateCategory(category.getId(), new AdminCategoryRequest(latest.getUpdatedAt(), "재조회-" + category.getId(), 4L));
        assertThat(categories.findById(category.getId()).orElseThrow().getSortOrder()).isEqualTo(4L);
    }

    private MenuRequest menuRequest(LocalDateTime version, String name, Long order, boolean visible) {
        return new MenuRequest(version, null, name, "수정 설명", null, order, visible);
    }

    @Test void menuItemCanMoveGroupsWhileKeepingUnspecifiedOrderAndVisibility() {
        Menu item = menus.saveAndFlush(new Menu(menu, "항목", "설명", MenuType.ITEM, "/before", 5L, false));
        Menu otherGroup = menus.saveAndFlush(new Menu("다른 그룹", null, MenuType.GROUP, null, 2L, true));
        item = menus.findById(item.getId()).orElseThrow();
        menuService.updateMenu(item.getId(), new MenuRequest(item.getUpdatedAt(), otherGroup.getId(),
                "수정 항목", "수정 설명", "/after", null, null));
        Menu saved = menus.findById(item.getId()).orElseThrow();
        assertThat(saved.getParentId()).isEqualTo(otherGroup.getId());
        assertThat(saved.getRoutePath()).isEqualTo("/after");
        assertThat(saved.getMenuType()).isEqualTo(MenuType.ITEM);
        assertThat(saved.getSortOrder()).isEqualTo(5L);
        assertThat(saved.getVisible()).isFalse();
    }

    @Test void categoryKeepsUnspecifiedOrderAndStillRejectsDuplicateNames() {
        Category other = categories.saveAndFlush(new Category("중복-" + category.getId(), 9L));
        assertThatThrownBy(() -> categoryService.updateCategory(category.getId(),
                new AdminCategoryRequest(category.getUpdatedAt(), other.getName(), 2L)))
                .isInstanceOfSatisfying(BlogException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NAME_DUPLICATED));
        categoryService.updateCategory(category.getId(),
                new AdminCategoryRequest(category.getUpdatedAt(), "이름만-" + category.getId(), null));
        assertThat(categories.findById(category.getId()).orElseThrow().getSortOrder()).isEqualTo(1L);
    }

    private void race(Runnable first, Runnable second, boolean rollback) throws Exception {
        CountDownLatch flushed = new CountDownLatch(1), release = new CountDownLatch(1);
        Gate gate = new Gate();
        probe.gate = gate;
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> winner = executor.submit(() -> tx.executeWithoutResult(status -> {
                first.run();
                em.flush();
                flushed.countDown();
                await(release);
                if (rollback) status.setRollbackOnly();
            }));
            try {
                await(flushed);
                Future<Throwable> loser = executor.submit(() -> {
                    gate.thread = Thread.currentThread().threadId();
                    try { second.run(); return null; }
                    catch (Throwable error) { return error; }
                });
                await(gate.started);
                // 수정 전 일반 조회는 즉시 오래된 행을 읽는다. 잠금 조회는 커밋까지 기다린다.
                gate.loaded.await(500, TimeUnit.MILLISECONDS);
                release.countDown();
                winner.get(15, TimeUnit.SECONDS);
                Throwable error = loser.get(15, TimeUnit.SECONDS);
                if (rollback) assertThat(error).isNull();
                else assertThat(error).isInstanceOfSatisfying(BlogException.class,
                        conflict -> assertThat(conflict.getErrorCode()).isEqualTo(ErrorCode.MODIFIED_BY_OTHERS));
            } finally { release.countDown(); }
        } finally { probe.gate = null; }
    }

    static void await(CountDownLatch latch) {
        try { assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }

    static class Gate {
        volatile long thread;
        final CountDownLatch started = new CountDownLatch(1), loaded = new CountDownLatch(1);
    }
    @Aspect static class ReadProbe {
        volatile Gate gate;
        @Around("(bean(menuRepository) || bean(categoryRepository)) && execution(* find*ById(..))")
        Object read(ProceedingJoinPoint call) throws Throwable {
            Gate active = gate;
            if (active == null || active.thread != Thread.currentThread().threadId()) return call.proceed();
            active.started.countDown();
            Object result = call.proceed();
            active.loaded.countDown();
            return result;
        }
    }
    @TestConfiguration static class ProbeConfiguration {
        @Bean ReadProbe readProbe() { return new ReadProbe(); }
    }
}
