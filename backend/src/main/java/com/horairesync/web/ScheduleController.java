package com.horairesync.web;

import com.horairesync.model.Conflict;
import com.horairesync.model.ExportRequest;
import com.horairesync.model.RankRequest;
import com.horairesync.model.ScheduleRequest;
import com.horairesync.model.SectionScore;
import com.horairesync.service.ConflictService;
import com.horairesync.service.IcsExportService;
import com.horairesync.service.RankingService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schedule logic routes. They are stateless: the frontend sends the sections it holds, including values typed by
 * the user, so they also work in manual mode for schools without adapter.
 */
@RestController
@RequestMapping("/api/v1/schedule")
public class ScheduleController {

    static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final ConflictService conflicts;
    private final RankingService ranking;
    private final IcsExportService export;

    public ScheduleController(ConflictService conflicts, RankingService ranking, IcsExportService export) {
        this.conflicts = conflicts;
        this.ranking = ranking;
        this.export = export;
    }

    @PostMapping("/conflicts")
    public List<Conflict> conflicts(@RequestBody ScheduleRequest request) {
        return conflicts.findConflicts(request.selections());
    }

    @PostMapping("/rank")
    public List<SectionScore> rank(@RequestBody RankRequest request) {
        return ranking.rank(request);
    }

    @PostMapping(value = "/export", produces = "text/calendar")
    public ResponseEntity<String> export(@RequestBody ExportRequest request) {
        IcsExportService.IcsFile file = export.export(request);
        return ResponseEntity.ok()
                .contentType(TEXT_CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.content());
    }
}
