/**
 * Pure functions on the schedule data. No React here, so all rules are unit tested directly.
 *
 * Problem paths follow the backend's missingFields: "meetings" (no schedule) and "meetings[i].field".
 */
import { DAYS, toMinutes } from './format.js';

const ESSENTIAL_MEETING_FIELDS = ['day', 'start', 'end'];

export const isBlank = (value) => value == null || (typeof value === 'string' && value.trim() === '');

export function emptyMeeting() {
  return {
    day: null,
    start: null,
    end: null,
    room: null,
    campus: null,
    type: 'LECTURE',
    onlyDate: null,
    firstDate: null,
    lastDate: null,
  };
}

/** Section typed by the user in manual mode. Nothing is marked missing: the user owns every value. */
export function manualSection({ courseCode, sectionNumber, instructor, mode, meetings }) {
  return {
    courseCode: courseCode.trim().toUpperCase(),
    sectionNumber: isBlank(sectionNumber) ? null : sectionNumber.trim(),
    instructor: isBlank(instructor) ? null : instructor.trim(),
    mode: mode ?? 'UNKNOWN',
    campus: null,
    meetings: meetings ?? [],
    notes: [],
    missingFields: [],
    sourceUrl: null,
  };
}

/**
 * Values the export cannot do without, mirroring the backend validation:
 * day, start and end of each meeting, an end after the start, and dates for weekly meetings
 * (their own, or the term's).
 * Returns [{ courseCode, sectionNumber, path }] where path is "meetings", "meetings[i].field" or "term.startDate".
 */
export function essentialProblems(selections, term) {
  const problems = [];
  for (const { section } of selections) {
    const at = (path) => problems.push({ courseCode: section.courseCode, sectionNumber: section.sectionNumber, path });
    if (section.meetings.length === 0) {
      at('meetings');
    }
    section.meetings.forEach((meeting, i) => {
      for (const field of ESSENTIAL_MEETING_FIELDS) {
        if (isBlank(meeting[field])) at(`meetings[${i}].${field}`);
      }
      if (meeting.start && meeting.end && toMinutes(meeting.start) >= toMinutes(meeting.end)) {
        at(`meetings[${i}].end`);
      }
      if (!meeting.onlyDate) {
        if (isBlank(meeting.firstDate) && isBlank(term?.startDate)) at(`meetings[${i}].firstDate`);
        if (isBlank(meeting.lastDate) && isBlank(term?.endDate)) at(`meetings[${i}].lastDate`);
      }
    });
  }
  return problems;
}

/** Distinct campuses of a list of courses (for the preferences screen), sorted. */
export function campusesOf(courses) {
  const set = new Set();
  for (const course of courses) {
    for (const section of course.sections ?? []) {
      for (const meeting of section.meetings) {
        if (meeting.campus) set.add(meeting.campus);
      }
    }
  }
  return [...set].sort((a, b) => a.localeCompare(b, 'fr'));
}

/** Key of the selections with a conflict, e.g. "INF1120|020|1". */
const refKey = (ref) => `${ref.courseCode}|${ref.sectionNumber}|${ref.meetingIndex}`;

/**
 * Blocks for the week view: one per weekly meeting with day and times. Overlapping blocks of a day
 * are placed side by side in lanes. Blocks involved in a conflict are flagged.
 */
export function weekLayout(selections, conflicts = []) {
  const inConflict = new Set(conflicts.flatMap((c) => (c.date ? [] : [refKey(c.first), refKey(c.second)])));
  const blocks = [];
  for (const { courseTitle, section } of selections) {
    section.meetings.forEach((meeting, meetingIndex) => {
      if (meeting.onlyDate || !meeting.day || !meeting.start || !meeting.end) return;
      const ref = { courseCode: section.courseCode, sectionNumber: section.sectionNumber, meetingIndex };
      blocks.push({
        key: refKey(ref),
        day: meeting.day,
        start: toMinutes(meeting.start),
        end: toMinutes(meeting.end),
        courseCode: section.courseCode,
        courseTitle,
        sectionNumber: section.sectionNumber,
        type: meeting.type,
        room: meeting.room,
        campus: meeting.campus ?? section.campus,
        conflict: inConflict.has(refKey(ref)),
      });
    });
  }
  for (const day of DAYS) {
    const ofDay = blocks.filter((b) => b.day === day).sort((a, b) => a.start - b.start || a.end - b.end);
    const laneEnds = [];
    for (const block of ofDay) {
      let lane = laneEnds.findIndex((end) => end <= block.start);
      if (lane === -1) {
        lane = laneEnds.length;
        laneEnds.push(block.end);
      } else {
        laneEnds[lane] = block.end;
      }
      block.lane = lane;
    }
    for (const block of ofDay) block.lanes = laneEnds.length;
  }
  return blocks;
}

/** Days to show in the week view: Monday to Friday, plus Saturday/Sunday when used. */
export function visibleDays(blocks) {
  const days = DAYS.slice(0, 5);
  for (const extra of ['SATURDAY', 'SUNDAY']) {
    if (blocks.some((b) => b.day === extra)) days.push(extra);
  }
  return days;
}

/** One-off meetings (exams) sorted by date. */
export function oneOffMeetings(selections) {
  return selections
    .flatMap(({ courseTitle, section }) =>
      section.meetings
        .filter((m) => m.onlyDate)
        .map((meeting) => ({ courseCode: section.courseCode, courseTitle, sectionNumber: section.sectionNumber, meeting })),
    )
    .sort((a, b) => a.meeting.onlyDate.localeCompare(b.meeting.onlyDate) || (a.meeting.start ?? '').localeCompare(b.meeting.start ?? ''));
}

/** Most frequent value of a list, or null. Ties go to the latest value. */
function mostFrequent(values) {
  const counts = new Map();
  for (const v of values) counts.set(v, (counts.get(v) ?? 0) + 1);
  let best = null;
  for (const [value, count] of counts) {
    if (best == null || count > counts.get(best) || (count === counts.get(best) && value > best)) best = value;
  }
  return best;
}

/**
 * Term bounds for meetings typed by hand, which have no dates of their own. The school's calendar may not state
 * the end of term clearly ("23 avril ou 30 avril ou 2 mai"), so the dates come from the published sections of the
 * loaded courses: their most common "Du ... au ..." range is the regular term (e.g. 11 janvier to 2 mai 2027).
 * Nothing is guessed: with no published section, the term's own dates (possibly null) are kept.
 */
export function termBounds(term, courses) {
  const weekly = courses
    .filter((c) => !c.manual)
    .flatMap((c) => c.sections ?? [])
    .flatMap((s) => s.meetings)
    .filter((m) => !m.onlyDate);
  return {
    start: term?.startDate ?? mostFrequent(weekly.map((m) => m.firstDate).filter(Boolean)),
    end: term?.endDate ?? mostFrequent(weekly.map((m) => m.lastDate).filter(Boolean)),
  };
}

/** Body of POST /schedule/export. */
export function exportPayload({ school, term, courses = [], selections, excludeReadingWeek }) {
  const excluded = new Set(term?.excludedDates ?? []);
  if (excludeReadingWeek) {
    for (const d of term?.readingWeekDates ?? []) excluded.add(d);
  }
  const bounds = termBounds(term, courses);
  return {
    schoolId: school?.id ?? 'uqam',
    termId: term?.id ?? 'session',
    termLabel: term?.label ?? null,
    termStart: bounds.start,
    termEnd: bounds.end,
    excludedDates: [...excluded].sort(),
    selections,
  };
}
