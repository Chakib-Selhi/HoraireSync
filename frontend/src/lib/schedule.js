/**
 * Pure functions on the schedule data. No React here, so all rules are unit tested directly.
 *
 * Paths in `missingFields` come from the backend: "instructor", "mode", "campus", "meetings"
 * (no schedule published) and "meetings[i].field".
 */
import { DAYS, toMinutes } from './format.js';

const MEETING_PATH = /^meetings\[(\d+)\]\.(\w+)$/;
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

/** Value at a missing-field path. */
export function getField(section, path) {
  const m = MEETING_PATH.exec(path);
  if (m) {
    return section.meetings[Number(m[1])]?.[m[2]] ?? null;
  }
  return section[path] ?? null;
}

/**
 * Returns a copy of the section with the value set. The path leaves missingFields when the value is
 * filled and comes back when it is cleared, so the badge always reflects the current value.
 */
export function setField(section, path, rawValue) {
  const value = isBlank(rawValue) ? null : rawValue;
  const m = MEETING_PATH.exec(path);
  let next;
  if (m) {
    const index = Number(m[1]);
    const meetings = section.meetings.map((meeting, i) => (i === index ? { ...meeting, [m[2]]: value } : meeting));
    next = { ...section, meetings };
  } else {
    next = { ...section, [path]: value };
  }
  const others = section.missingFields.filter((f) => f !== path);
  return { ...next, missingFields: value == null ? [...others, path] : others };
}

export function addMeeting(section, meeting = emptyMeeting()) {
  const missingFields = section.missingFields.filter((f) => f !== 'meetings');
  return { ...section, meetings: [...section.meetings, meeting], missingFields };
}

/** Removes a meeting and renumbers the meeting paths that follow it. */
export function removeMeeting(section, index) {
  const missingFields = section.missingFields
    .map((field) => {
      const m = MEETING_PATH.exec(field);
      if (!m) return field;
      const i = Number(m[1]);
      if (i === index) return null;
      return i > index ? `meetings[${i - 1}].${m[2]}` : field;
    })
    .filter(Boolean);
  return { ...section, meetings: section.meetings.filter((_, i) => i !== index), missingFields };
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

/** True when the path is required for the export (given the term dates). */
export function isEssentialPath(path, meeting, term) {
  if (path === 'meetings') return true;
  const m = MEETING_PATH.exec(path);
  if (!m) return false;
  if (ESSENTIAL_MEETING_FIELDS.includes(m[2])) return true;
  if (m[2] === 'firstDate') return !meeting?.onlyDate && isBlank(term?.startDate);
  if (m[2] === 'lastDate') return !meeting?.onlyDate && isBlank(term?.endDate);
  return false;
}

/** Missing paths that still have no value (a section may have been completed by the user). */
export function stillMissing(section) {
  return section.missingFields.filter((path) => {
    if (path === 'meetings') return section.meetings.length === 0;
    return isBlank(getField(section, path));
  });
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

/** Body of POST /schedule/export. */
export function exportPayload({ school, term, selections, excludeReadingWeek }) {
  const excluded = new Set(term?.excludedDates ?? []);
  if (excludeReadingWeek) {
    for (const d of term?.readingWeekDates ?? []) excluded.add(d);
  }
  return {
    schoolId: school?.id ?? 'manuel',
    termId: term?.id ?? 'session',
    termLabel: term?.label ?? null,
    termStart: term?.startDate ?? null,
    termEnd: term?.endDate ?? null,
    excludedDates: [...excluded].sort(),
    selections,
  };
}
