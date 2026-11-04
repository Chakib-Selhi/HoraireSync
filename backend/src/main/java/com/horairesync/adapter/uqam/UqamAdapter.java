package com.horairesync.adapter.uqam;

import com.horairesync.adapter.SchoolAdapter;
import com.horairesync.config.HoraireSyncProperties;
import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import com.horairesync.http.HtmlSource;
import com.horairesync.model.Course;
import com.horairesync.model.Program;
import com.horairesync.model.School;
import com.horairesync.model.Section;
import com.horairesync.model.Term;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Reads the public pages of etudier.uqam.ca.
 *
 * <p>Inputs are validated before any request so that an invalid code never reaches the site. Page-level caching and
 * crawl delay are handled by {@link HtmlSource}.
 */
@Component
public class UqamAdapter implements SchoolAdapter {

    public static final String SCHOOL_ID = "uqam";
    static final int MAX_PROGRAM_RESULTS = 50;

    private static final School SCHOOL = new School(SCHOOL_ID, "Université du Québec à Montréal (UQAM)", true);
    private static final Pattern COURSE_CODE = Pattern.compile("[A-Z]{3}\\d{3}[0-9A-Z]");
    private static final Pattern PROGRAM_CODE = Pattern.compile("\\d{4}");

    private final HtmlSource html;
    private final String baseUrl;

    public UqamAdapter(HtmlSource html, HoraireSyncProperties properties) {
        this.html = html;
        this.baseUrl = properties.uqam().baseUrl().replaceAll("/+$", "");
    }

    @Override
    public School school() {
        return SCHOOL;
    }

    @Override
    public List<Term> fetchTerms() {
        return UqamCalendarParser.parse(html.get(baseUrl + "/calendriers"));
    }

    @Override
    public List<Program> searchPrograms(String query) {
        List<Program> all = UqamProgramParser.parseProgramList(html.get(baseUrl + "/programmes"), SCHOOL_ID);
        String q = UqamText.fold(query);
        if (q.isEmpty()) {
            return all.stream().limit(MAX_PROGRAM_RESULTS).toList();
        }
        String[] words = q.split(" ");
        return all.stream()
                .filter(p -> matches(p, q, words))
                .sorted(Comparator.comparingInt((Program p) -> relevance(p, q)).thenComparing(Program::name))
                .limit(MAX_PROGRAM_RESULTS)
                .toList();
    }

    private static boolean matches(Program program, String query, String[] words) {
        if (program.code().startsWith(query)) {
            return true;
        }
        String haystack = UqamText.fold(program.name() + " " + (program.type() == null ? "" : program.type()));
        for (String word : words) {
            if (!haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** Lower is better: exact code, then title starting with the query, then the rest. */
    private static int relevance(Program program, String query) {
        if (program.code().equals(query)) {
            return 0;
        }
        return UqamText.fold(program.name()).startsWith(query) ? 1 : 2;
    }

    @Override
    public List<Course> fetchProgramCourses(String programCode) {
        String code = programCode == null ? "" : programCode.trim();
        if (!PROGRAM_CODE.matcher(code).matches()) {
            throw new HoraireSyncException(ErrorCode.PROGRAM_NOT_FOUND,
                    "Le code de programme « " + programCode + " » n'est pas valide (4 chiffres attendus).");
        }
        return UqamProgramParser.parseProgramCourses(html.get(baseUrl + "/programme?code=" + code), code);
    }

    /** Course title, credits and the terms with a published schedule. */
    @Override
    public Course fetchCourse(String courseCode) {
        String code = normalizeCourseCode(courseCode);
        return UqamCourseParser.parseCourse(html.get(courseUrl(code)), code);
    }

    @Override
    public List<Section> fetchSections(String termId, String courseCode) {
        UqamTermId term = UqamTermId.parse(termId).orElseThrow(() -> new HoraireSyncException(
                ErrorCode.INVALID_REQUEST, "La session « " + termId + " » n'est pas valide (format AAAAT attendu)."));
        String code = normalizeCourseCode(courseCode);
        String url = courseUrl(code);
        return UqamCourseParser.parseSections(html.get(url), code, term, url);
    }

    private String courseUrl(String code) {
        return baseUrl + "/cours?sigle=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
    }

    private static String normalizeCourseCode(String courseCode) {
        String code = courseCode == null ? "" : courseCode.trim().toUpperCase(Locale.ROOT);
        if (!COURSE_CODE.matcher(code).matches()) {
            throw new HoraireSyncException(ErrorCode.COURSE_NOT_FOUND,
                    "Le sigle « " + courseCode + " » n'est pas valide (ex. INF1120).");
        }
        return code;
    }
}
