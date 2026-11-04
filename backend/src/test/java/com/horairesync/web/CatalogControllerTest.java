package com.horairesync.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import com.horairesync.model.School;
import com.horairesync.service.CatalogService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Checks routing and the single error format; the service is mocked. */
@WebMvcTest(CatalogController.class)
@org.springframework.context.annotation.Import({com.horairesync.service.TermPolicy.class, com.horairesync.config.ClockConfig.class})
class CatalogControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CatalogService catalog;

    @Test
    void listsSchools() throws Exception {
        when(catalog.schools()).thenReturn(List.of(new School("uqam", "UQAM", true)));

        mvc.perform(get("/api/v1/schools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("uqam"))
                .andExpect(jsonPath("$[0].supported").value(true));
    }

    @Test
    void businessErrorsUseTheSingleFormatAndTheirStatus() throws Exception {
        when(catalog.terms("mcgill")).thenThrow(
                new HoraireSyncException(ErrorCode.SCHOOL_NOT_SUPPORTED, "non prise en charge"));
        when(catalog.terms("uqam")).thenThrow(
                new HoraireSyncException(ErrorCode.SOURCE_UNAVAILABLE, "site muet"));

        mvc.perform(get("/api/v1/schools/mcgill/terms"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHOOL_NOT_SUPPORTED"))
                .andExpect(jsonPath("$.message").value("non prise en charge"));
        mvc.perform(get("/api/v1/schools/uqam/terms"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SOURCE_UNAVAILABLE"));
    }

    @Test
    void parsingFailureIsBadGateway() throws Exception {
        when(catalog.programCourses("uqam", "7416")).thenThrow(
                new HoraireSyncException(ErrorCode.PARSING_FAILED, "structure"));

        mvc.perform(get("/api/v1/schools/uqam/programs/7416/courses"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PARSING_FAILED"));
    }

    @Test
    void unexpectedErrorsAreHidden() throws Exception {
        when(catalog.searchPrograms("uqam", "")).thenThrow(new IllegalStateException("secret detail"));

        mvc.perform(get("/api/v1/schools/uqam/programs"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Erreur interne inattendue."));
    }

    @Test
    void unknownRouteAndWrongMethodUseTheSameFormat() throws Exception {
        mvc.perform(get("/api/v1/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/schools"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
