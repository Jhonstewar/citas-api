package com.fcv.citas.infrastructure.rest;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.S3TestData;
import com.fcv.citas.support.TestTokens;

/**
 * HU-022 CA-04 con reloj fijo: hoy a las 12:00 (America/Bogota) un bloque 10:00-14:00 solo ofrece
 * 12:30, 13:00 y 13:30. Falla si el filtro se reduce a {@code block_date > :today}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartiallyPastTodaySearchIntegrationTest {

    static final LocalDate TODAY = LocalDate.now(SystemZone.ZONE);

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedNoonClock() {
            return Clock.fixed(TODAY.atTime(LocalTime.NOON).atZone(SystemZone.ZONE).toInstant(), SystemZone.ZONE);
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private S3TestData data;

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    @Test
    void todayOffersOnlySlotsNotYetStarted() throws Exception {
        int hic = data.siteId("HIC");
        int sp = data.specialty("GENERAL", 30);
        S3TestData.Professional pro = data.professional("today", new int[] { sp }, hic);
        data.block(pro.id(), hic, TODAY, "10:00", "14:00");
        String token = new TestTokens(jwtEncoder).bearer(data.user("patient", "USER"), Role.USER);
        mvc.perform(get("/api/patient/availability?specialtyId=" + sp + "&date=" + TODAY)
                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].startTime", contains("12:30", "13:00", "13:30")));
    }
}
