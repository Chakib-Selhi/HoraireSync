package com.horairesync.adapter.uqam;

import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import com.horairesync.model.Course;
import com.horairesync.model.Program;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Parses the program list {@code /programmes} and a program page {@code /programme?code=0000}.
 *
 * <p>The program page does not publish credits per course, and the "offered in term" markers are added by
 * JavaScript, so {@code credits} is {@code null} and {@code offeredTermIds} is empty: they come from the course page.
 */
final class UqamProgramParser {

    private static final Pattern PROGRAM_TITLE = Pattern.compile("^(.+?)\\s*\\((\\d{4})\\)\\s*\\|");
    private static final Pattern COURSE_LABEL = Pattern.compile("^([A-Z]{3}\\d{3}[0-9A-Z])\\s*-\\s*(.+)$");
    private static final Pattern CODE_IN_HREF = Pattern.compile("code=(\\d{4})");

    private UqamProgramParser() {}

    /** Every program of the list page, one entry per code. */
    static List<Program> parseProgramList(Document doc, String schoolId) {
        Element table = doc.getElementById("tableProgrammes");
        if (table == null) {
            throw new HoraireSyncException(ErrorCode.PARSING_FAILED,
                    "La liste des programmes de l'UQAM a une structure inattendue (table#tableProgrammes absente).");
        }
        Map<String, Program> byCode = new LinkedHashMap<>();
        for (Element row : table.select("tbody tr")) {
            Element link = row.selectFirst("a[href*=programme?code=]");
            if (link == null) {
                continue;
            }
            Element codeCell = row.selectFirst("td.code");
            String code = codeCell != null ? UqamText.clean(codeCell.text()) : null;
            if (code == null || code.isEmpty()) {
                Matcher m = CODE_IN_HREF.matcher(link.attr("href"));
                code = m.find() ? m.group(1) : null;
            }
            String name = UqamText.emptyToNull(link.text());
            if (code == null || name == null) {
                continue;
            }
            Element typeCell = row.selectFirst("td.types:not(.invisible)");
            String type = typeCell == null ? null : UqamText.emptyToNull(typeCell.text());
            byCode.putIfAbsent(code, new Program(code, name, type, schoolId));
        }
        if (byCode.isEmpty()) {
            throw new HoraireSyncException(ErrorCode.PARSING_FAILED,
                    "La liste des programmes de l'UQAM est vide ou illisible.");
        }
        return List.copyOf(byCode.values());
    }

    /** Courses of a program; throws {@code PROGRAM_NOT_FOUND} for an unknown code. */
    static List<Course> parseProgramCourses(Document doc, String programCode) {
        Matcher title = PROGRAM_TITLE.matcher(UqamText.clean(doc.title()));
        if (!title.find() || !title.group(2).equals(programCode)) {
            throw new HoraireSyncException(ErrorCode.PROGRAM_NOT_FOUND,
                    "Le programme " + programCode + " est introuvable sur le site de l'UQAM.");
        }
        Map<String, Course> byCode = new LinkedHashMap<>();
        for (Element line : doc.select("div.ligne_cours[data-sigle]")) {
            Element label = line.selectFirst("span.texte");
            Matcher m = label == null ? null : COURSE_LABEL.matcher(UqamText.clean(label.text()));
            if (m == null || !m.matches()) {
                continue;
            }
            byCode.putIfAbsent(m.group(1), new Course(m.group(1), m.group(2), null, null, List.of()));
        }
        if (byCode.isEmpty() && doc.selectFirst("div.node-programme") == null) {
            throw new HoraireSyncException(ErrorCode.PARSING_FAILED,
                    "La page du programme " + programCode + " a une structure inattendue.");
        }
        return List.copyOf(byCode.values());
    }
}
