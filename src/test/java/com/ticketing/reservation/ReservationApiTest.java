package com.ticketing.reservation;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 가입 → 로그인 → 공연/회차 등록 → 예매/취소까지 API 흐름 검증 (H2, Docker 불필요). */
@SpringBootTest
@AutoConfigureMockMvc
class ReservationApiTest {

    @Autowired MockMvc mockMvc;

    @Test
    void 재고만큼만_예매되고_중복_예매와_재고_초과는_409() throws Exception {
        long scheduleId = createSchedule(2, LocalDateTime.now().minusDays(1));
        String user1 = signupAndLogin();
        String user2 = signupAndLogin();
        String user3 = signupAndLogin();

        reserve(user1, scheduleId).andExpect(status().isOk());
        reserve(user1, scheduleId).andExpect(status().isConflict());   // 중복 예매
        reserve(user2, scheduleId).andExpect(status().isOk());
        reserve(user3, scheduleId).andExpect(status().isConflict());   // 재고 소진

        assertRemainingStock(scheduleId, 0);
    }

    @Test
    void 취소하면_재고가_복구되어_다른_회원이_예매할_수_있다() throws Exception {
        long scheduleId = createSchedule(1, LocalDateTime.now().minusDays(1));
        String user1 = signupAndLogin();
        String user2 = signupAndLogin();

        String reservationId = reserve(user1, scheduleId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        reserve(user2, scheduleId).andExpect(status().isConflict());

        mockMvc.perform(delete("/api/reservations/" + reservationId).header("Authorization", bearer(user1)))
                .andExpect(status().isOk());
        assertRemainingStock(scheduleId, 1);

        reserve(user2, scheduleId).andExpect(status().isOk());
    }

    @Test
    void 티켓_오픈_전에는_예매할_수_없다() throws Exception {
        long scheduleId = createSchedule(5, LocalDateTime.now().plusDays(1));
        reserve(signupAndLogin(), scheduleId).andExpect(status().isBadRequest());
    }

    @Test
    void 인증_없이_예매하면_401_일반회원이_관리자_API를_호출하면_403() throws Exception {
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleId\":1,\"quantity\":1}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/performances").header("Authorization", bearer(signupAndLogin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"venue\":\"v\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 예매하면_완료_알림이_비동기로_생성되고_읽음_처리할_수_있다() throws Exception {
        long scheduleId = createSchedule(5, LocalDateTime.now().minusDays(1));
        String user = signupAndLogin();

        reserve(user, scheduleId).andExpect(status().isOk());

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/notifications").header("Authorization", bearer(user)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.unreadCount").value(1))
                        .andExpect(jsonPath("$.items[0].type").value("RESERVATION_CONFIRMED")));

        mockMvc.perform(post("/api/notifications/read-all").header("Authorization", bearer(user)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void 관리자_통계는_확정_예매_기준으로_집계되고_일반_회원은_접근할_수_없다() throws Exception {
        long scheduleId = createSchedule(5, LocalDateTime.now().minusDays(1));
        String user = signupAndLogin();
        reserve(user, scheduleId).andExpect(status().isOk());

        String admin = login("admin@ticketing.com", "admin1234");
        mockMvc.perform(get("/api/admin/stats").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty())
                .andExpect(jsonPath("$.items[*].schedules[?(@.id==" + scheduleId + ")].sold").value(org.hamcrest.Matchers.contains(1)));

        mockMvc.perform(get("/api/admin/stats").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 관리자는_공연을_수정_삭제하고_티켓_오픈_시각을_바꿀_수_있다() throws Exception {
        String admin = login("admin@ticketing.com", "admin1234");
        String performanceId = mockMvc.perform(post("/api/admin/performances")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"수정 전\",\"venue\":\"장소\",\"description\":\"d\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String scheduleId = mockMvc.perform(post("/api/admin/performances/" + performanceId + "/schedules")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"" + LocalDateTime.now().plusDays(30).withNano(0) + "\","
                                + "\"ticketOpenAt\":\"" + LocalDateTime.now().plusDays(5).withNano(0) + "\",\"totalStock\":3}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        mockMvc.perform(put("/api/admin/performances/" + performanceId)
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"수정 후\",\"venue\":\"새 장소\",\"description\":\"d2\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/performances/" + performanceId))
                .andExpect(jsonPath("$.title").value("수정 후"));

        // 오픈 시각을 과거로 바꾸면 바로 예매할 수 있다.
        mockMvc.perform(patch("/api/admin/schedules/" + scheduleId + "/ticket-open-at")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketOpenAt\":\"" + LocalDateTime.now().minusDays(1).withNano(0) + "\"}"))
                .andExpect(status().isOk());
        reserve(signupAndLogin(), Long.parseLong(scheduleId)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/performances/" + performanceId).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/performances/" + performanceId)).andExpect(status().isNotFound());
    }

    @Test
    void 로그인_실패와_이메일_중복() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@test.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"n\"}";

        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private long createSchedule(int stock, LocalDateTime ticketOpenAt) throws Exception {
        String admin = login("admin@ticketing.com", "admin1234");
        String performanceId = mockMvc.perform(post("/api/admin/performances")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"콘서트\",\"venue\":\"올림픽홀\",\"description\":\"설명\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        String scheduleId = mockMvc.perform(post("/api/admin/performances/" + performanceId + "/schedules")
                        .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"" + LocalDateTime.now().plusDays(30).withNano(0) + "\","
                                + "\"ticketOpenAt\":\"" + ticketOpenAt.withNano(0) + "\","
                                + "\"totalStock\":" + stock + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(scheduleId);
    }

    private String signupAndLogin() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.com";
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"회원\"}"))
                .andExpect(status().isCreated());
        return login(email, "password123");
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions reserve(String token, long scheduleId) throws Exception {
        return mockMvc.perform(post("/api/reservations").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scheduleId\":" + scheduleId + ",\"quantity\":1}"));
    }

    private void assertRemainingStock(long scheduleId, int expected) throws Exception {
        String performanceId = null;
        // 회차 단독 조회 API 가 없으므로 공연 상세에서 확인한다.
        String list = mockMvc.perform(get("/api/performances?size=1000")).andReturn().getResponse().getContentAsString();
        for (Object id : (java.util.List<?>) JsonPath.read(list, "$.content[*].id")) {
            String detail = mockMvc.perform(get("/api/performances/" + id)).andReturn().getResponse().getContentAsString();
            java.util.List<Object> found = JsonPath.read(detail, "$.schedules[?(@.id==" + scheduleId + ")].remainingStock");
            if (!found.isEmpty()) {
                org.assertj.core.api.Assertions.assertThat(((Number) found.get(0)).intValue()).isEqualTo(expected);
                return;
            }
        }
        throw new AssertionError("회차를 찾을 수 없습니다: " + scheduleId);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
