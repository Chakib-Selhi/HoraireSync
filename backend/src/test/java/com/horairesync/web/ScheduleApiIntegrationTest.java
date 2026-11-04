package com.horairesync.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horairesync.support.FixtureHtmlSource;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full round trip as the frontend will do it: read sections, send them back to the schedule routes as JSON.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScheduleApiIntegrationTest {

    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        FixtureHtmlSource fixtureHtmlSource() {
            return FixtureHtmlSource.uqam();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private JsonNode sections(String code) throws Exception {
        String body = mvc.perform(get("/api/v1/schools/uqam/terms/20271/courses/" + code + "/sections"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    private ObjectNode selection(JsonNode courseSections, int index) {
        ObjectNode selection = json.createObjectNode();
        selection.put("courseTitle", courseSections.path("course").path("title").asText());
        selection.set("section", courseSections.path("sections").get(index));
        return selection;
    }

    @Test
    void exportsTheSectionsReturnedByTheCatalog() throws Exception {
        JsonNode inf1120 = sections("INF1120");
        ObjectNode request = json.createObjectNode()
                .put("schoolId", "uqam").put("termId", "20271").put("termLabel", "Hiver 2027")
                .put("termStart", "2027-01-11");
        request.putArray("excludedDates").add("2027-03-02"); // a Tuesday
        request.putArray("selections").add(selection(inf1120, 2));

        String ics = mvc.perform(post("/api/v1/schedule/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"horaire-uqam-20271.ics\""))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(ics).contains("SUMMARY:INF1120 Programmation I (Gr. 20)\r\n",
                "EXDATE;TZID=America/Toronto:20270302T093000");
    }

    @Test
    void incompleteScheduleIsRejectedWithTheErrorFormat() throws Exception {
        String request = """
                {"schoolId":"uqam","termId":"20271","selections":[{"courseTitle":"X","section":
                  {"courseCode":"INF1120","sectionNumber":"020","meetings":[{"type":"LECTURE"}]}}]}
                """;

        mvc.perform(post("/api/v1/schedule/export").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCHEDULE"));
    }

    @Test
    void malformedJsonIsAnInvalidRequest() throws Exception {
        mvc.perform(post("/api/v1/schedule/conflicts").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void detectsConflictsBetweenRealSections() throws Exception {
        // INF1120 gr. 020 (Tue 09:30-12:30) and ORH1163 gr. 020 (Tue 09:30-12:30) overlap
        JsonNode inf1120 = sections("INF1120");
        JsonNode orh1163 = sections("ORH1163");
        int orhIndex = indexOf(orh1163, "020");
        ObjectNode request = json.createObjectNode().put("schoolId", "uqam").put("termId", "20271");
        request.putArray("selections").add(selection(inf1120, 2)).add(selection(orh1163, orhIndex));

        mvc.perform(post("/api/v1/schedule/conflicts")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].day").value("TUESDAY"))
                .andExpect(jsonPath("$[0].overlapStart").value("09:30:00"))
                .andExpect(jsonPath("$[0].first.courseCode").value("INF1120"))
                .andExpect(jsonPath("$[0].second.courseCode").value("ORH1163"));
    }

    @Test
    void ranksSectionsWithPreferencesAndChosenSections() throws Exception {
        JsonNode inf1120 = sections("INF1120");
        ObjectNode request = json.createObjectNode();
        request.set("sections", inf1120.path("sections"));
        ObjectNode preferences = request.putObject("preferences");
        preferences.put("timeOfDay", "EVENING");
        request.putArray("chosen");

        mvc.perform(post("/api/v1/schedule/rank")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].sectionNumber").value("010"))
                .andExpect(jsonPath("$[0].score").value(100))
                .andExpect(jsonPath("$[0].compatible").value(true))
                .andExpect(jsonPath("$[1].compatible").value(false))
                .andExpect(jsonPath("$[1].criteria[0].criterion").value("TIME_OF_DAY"))
                .andExpect(jsonPath("$[1].criteria[0].outcome").value("MISMATCH"));
    }

    private static int indexOf(JsonNode courseSections, String number) {
        ArrayNode sections = (ArrayNode) courseSections.path("sections");
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).path("sectionNumber").asText().equals(number)) {
                return i;
            }
        }
        throw new AssertionError("No section " + number);
    }
}
