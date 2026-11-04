package com.horairesync.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.horairesync.http.HtmlSource;
import com.horairesync.support.FixtureHtmlSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

/** Full application context; only the network is replaced by the phase 0 fixtures. */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogApiIntegrationTest {

    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        FixtureHtmlSource fixtureHtmlSource() {
            return FixtureHtmlSource.uqam();
        }

        /** The fixtures were recorded on 2026-11-04, during the fall term. */
        @Bean
        @Primary
        java.time.Clock fixedClock() {
            java.time.ZoneId zone = java.time.ZoneId.of("America/Toronto");
            return java.time.Clock.fixed(java.time.Instant.parse("2026-11-04T17:00:00Z"), zone);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private HtmlSource html;

    @Test
    void schoolsListOnlyUqam() throws Exception {
        mvc.perform(get("/api/v1/schools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("uqam"))
                .andExpect(jsonPath("$[0].supported").value(true));
    }

    @Test
    void sectionsEndpointReturnsCourseAndSectionsWithIsoDates() throws Exception {
        mvc.perform(get("/api/v1/schools/uqam/terms/20271/courses/INF1120/sections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.course.title").value("Programmation I"))
                .andExpect(jsonPath("$.course.credits").value(3))
                .andExpect(jsonPath("$.termId").value("20271"))
                .andExpect(jsonPath("$.sections.length()").value(4))
                .andExpect(jsonPath("$.sections[0].sectionNumber").value("001"))
                .andExpect(jsonPath("$.sections[0].instructor").isEmpty())
                .andExpect(jsonPath("$.sections[0].missingFields[0]").value("instructor"))
                .andExpect(jsonPath("$.sections[0].meetings[0].day").value("MONDAY"))
                .andExpect(jsonPath("$.sections[0].meetings[0].start").value("09:30:00"))
                .andExpect(jsonPath("$.sections[0].meetings[0].firstDate").value("2027-01-11"));
    }

    @Test
    void repeatedCallsAreServedFromCache() throws Exception {
        FixtureHtmlSource fixtures = (FixtureHtmlSource) html;
        long before = fixtures.requestedUrls().size();

        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/schools/uqam/terms/20263/courses/orh1163/sections"))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/schools/uqam/terms/20263/courses/ORH1163/sections")).andExpect(status().isOk());

        // fetchCourse and fetchSections read the page once each, then the parsed result is cached
        assertThat(fixtures.requestedUrls().size() - before).isEqualTo(2);
    }

    @Test
    void termsProgramsAndProgramCourses() throws Exception {
        mvc.perform(get("/api/v1/schools/uqam/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("20271"))
                .andExpect(jsonPath("$[0].startDate").value("2027-01-11"));
        mvc.perform(get("/api/v1/schools/uqam/programs").param("q", "génie logiciel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == '7416')].type").value("Baccalauréat"));
        mvc.perform(get("/api/v1/schools/uqam/programs/7416/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(47));
    }

    @Test
    void notFoundCases() throws Exception {
        mvc.perform(get("/api/v1/schools/uqam/terms/20271/courses/ZZZ9999/sections"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
        mvc.perform(get("/api/v1/schools/uqam/programs/7084/courses"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROGRAM_NOT_FOUND"));
        mvc.perform(get("/api/v1/schools/mcgill/terms"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHOOL_NOT_SUPPORTED"));
        mvc.perform(get("/api/v1/schools/uqam/terms/H27/courses/INF1120/sections"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void corsAllowsTheViteDevServer() throws Exception {
        mvc.perform(options("/api/v1/schools")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
