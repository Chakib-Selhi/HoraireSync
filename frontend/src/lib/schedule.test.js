import { describe, expect, it } from 'vitest';
import {
  campusesOf,
  emptyMeeting,
  essentialProblems,
  exportPayload,
  manualSection,
  oneOffMeetings,
  termBounds,
  visibleDays,
  weekLayout,
} from './schedule.js';

const meeting = (over = {}) => ({
  day: 'MONDAY', start: '09:30:00', end: '12:30:00', room: null, campus: 'Campus de Montréal', type: 'LECTURE',
  onlyDate: null, firstDate: '2027-01-11', lastDate: '2027-05-02', ...over,
});

const section = (over = {}) => ({
  courseCode: 'INF1120', sectionNumber: '020', instructor: null, mode: 'IN_PERSON', campus: null,
  meetings: [meeting(), meeting({ day: 'WEDNESDAY', type: 'LAB', end: '11:30:00' })],
  notes: [], missingFields: ['instructor', 'meetings[0].room', 'meetings[1].room'], sourceUrl: 'https://x', ...over,
});

const term = { id: '20271', label: 'Hiver 2027', startDate: '2027-01-11', endDate: null,
  excludedDates: ['2027-03-29'], readingWeekDates: ['2027-03-01', '2027-03-02'] };

describe('essentialProblems', () => {
  it('accepts a complete schedule', () => {
    expect(essentialProblems([{ section: section() }], term)).toEqual([]);
  });

  it('reports missing day and times, and an end before the start', () => {
    const s = section({ meetings: [meeting({ day: null, start: null }), meeting({ start: '12:00', end: '10:00' })] });
    const paths = essentialProblems([{ section: s }], term).map((p) => p.path);
    expect(paths).toEqual(['meetings[0].day', 'meetings[0].start', 'meetings[1].end']);
  });

  it('uses the term dates for meetings without dates', () => {
    const s = section({ meetings: [meeting({ firstDate: null, lastDate: null })] });
    expect(essentialProblems([{ section: s }], term).map((p) => p.path)).toEqual(['meetings[0].lastDate']);
    expect(essentialProblems([{ section: s }], { ...term, endDate: '2027-04-30' })).toEqual([]);
  });

  it('requires at least one meeting', () => {
    const problems = essentialProblems([{ section: section({ meetings: [] }) }], term);
    expect(problems).toEqual([{ courseCode: 'INF1120', sectionNumber: '020', path: 'meetings' }]);
  });

  it('does not ask dates for one-off meetings', () => {
    const exam = meeting({ onlyDate: '2027-02-27', day: 'SATURDAY', firstDate: null, lastDate: null, type: 'EXAM' });
    expect(essentialProblems([{ section: section({ meetings: [exam] }) }], { startDate: null, endDate: null })).toEqual([]);
  });
});

describe('weekLayout', () => {
  const selections = [
    { courseTitle: 'Programmation I', section: section() },
    { courseTitle: 'Maths', section: section({ courseCode: 'MAT1060', sectionNumber: '010',
      meetings: [meeting({ start: '11:00', end: '14:00' }), meeting({ onlyDate: '2027-02-27', day: 'SATURDAY', type: 'EXAM' })] }) },
  ];

  it('creates one block per weekly meeting and puts overlaps side by side', () => {
    const blocks = weekLayout(selections, []);
    expect(blocks).toHaveLength(3);
    const monday = blocks.filter((b) => b.day === 'MONDAY');
    expect(monday.map((b) => b.lane)).toEqual([0, 1]);
    expect(monday.every((b) => b.lanes === 2)).toBe(true);
    expect(blocks.find((b) => b.day === 'WEDNESDAY')).toMatchObject({ lane: 0, lanes: 1, start: 570, end: 690 });
  });

  it('flags blocks involved in a weekly conflict', () => {
    const conflict = {
      first: { courseCode: 'INF1120', sectionNumber: '020', meetingIndex: 0 },
      second: { courseCode: 'MAT1060', sectionNumber: '010', meetingIndex: 0 },
      day: 'MONDAY', date: null,
    };
    const blocks = weekLayout(selections, [conflict]);
    expect(blocks.filter((b) => b.conflict).map((b) => b.courseCode)).toEqual(['INF1120', 'MAT1060']);
  });

  it('shows weekend days only when used', () => {
    expect(visibleDays([])).toHaveLength(5);
    expect(visibleDays([{ day: 'SATURDAY' }])).toContain('SATURDAY');
  });

  it('lists one-off meetings separately', () => {
    expect(oneOffMeetings(selections)).toHaveLength(1);
    expect(oneOffMeetings(selections)[0].courseCode).toBe('MAT1060');
  });
});

describe('termBounds', () => {
  const published = { sections: [section(), section({ meetings: [meeting({ lastDate: '2027-04-23' })] }),
    section({ meetings: [meeting()] })] };

  it('uses the most common published range when the calendar end is ambiguous', () => {
    expect(termBounds(term, [published])).toEqual({ start: '2027-01-11', end: '2027-05-02' });
  });

  it('keeps the calendar dates when they are known', () => {
    expect(termBounds({ ...term, endDate: '2027-04-30' }, [published]).end).toBe('2027-04-30');
  });

  it('ignores manual courses and exams, and stays empty without published sections', () => {
    const manual = { manual: true, sections: [section({ meetings: [meeting({ lastDate: '2030-01-01' })] })] };
    expect(termBounds({ startDate: null, endDate: null }, [manual])).toEqual({ start: null, end: null });
  });
});

describe('exportPayload', () => {
  const base = { school: { id: 'uqam' }, term, selections: [{ courseTitle: 'X', section: section() }] };

  it('adds the reading week only when the user keeps it excluded', () => {
    expect(exportPayload({ ...base, excludeReadingWeek: true }).excludedDates)
      .toEqual(['2027-03-01', '2027-03-02', '2027-03-29']);
    expect(exportPayload({ ...base, excludeReadingWeek: false }).excludedDates).toEqual(['2027-03-29']);
  });

  it('carries the term labels and dates, completed from the published sections', () => {
    expect(exportPayload({ ...base, excludeReadingWeek: false })).toMatchObject({
      schoolId: 'uqam', termId: '20271', termLabel: 'Hiver 2027', termStart: '2027-01-11', termEnd: null,
    });
    expect(exportPayload({ ...base, courses: [{ sections: [section()] }], excludeReadingWeek: false }).termEnd)
      .toBe('2027-05-02');
  });
});

describe('helpers', () => {
  it('builds a manual section owned by the user', () => {
    const s = manualSection({ courseCode: ' glo2004 ', sectionNumber: '', instructor: ' ', mode: 'HYBRID', meetings: [emptyMeeting()] });
    expect(s).toMatchObject({ courseCode: 'GLO2004', sectionNumber: null, instructor: null, mode: 'HYBRID', missingFields: [] });
  });

  it('lists the campuses of the loaded courses', () => {
    const courses = [{ sections: [section(), section({ meetings: [meeting({ campus: 'UQAM Campus de Laval' })] })] }, { sections: null }];
    expect(campusesOf(courses)).toEqual(['Campus de Montréal', 'UQAM Campus de Laval']);
  });
});
