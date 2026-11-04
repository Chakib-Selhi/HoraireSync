import { describe, expect, it } from 'vitest';
import { initialState, isStepAvailable, orderedSelections, reducer, STEPS } from './reducer.js';

const uqam = { id: 'uqam', name: 'UQAM', supported: true };
const other = { id: 'autre', name: 'Autre', supported: false };
const run = (state, ...actions) => actions.reduce(reducer, state);
const section = (code, number = '010') => ({ courseCode: code, sectionNumber: number, meetings: [], missingFields: [] });

describe('navigation', () => {
  it('goes forward and back within bounds', () => {
    expect(run(initialState, { type: 'back' }).step).toBe(0);
    expect(run(initialState, { type: 'next' }, { type: 'next' }).step).toBe(2);
    const last = { ...initialState, step: STEPS.length - 1 };
    expect(run(last, { type: 'next' }).step).toBe(STEPS.length - 1);
  });

  it('skips program, preferences and sections steps in manual mode', () => {
    const manual = run(initialState, { type: 'selectSchool', school: other });
    expect(manual.manual).toBe(true);
    const visited = [];
    let state = manual;
    for (let i = 0; i < 6; i += 1) {
      state = reducer(state, { type: 'next' });
      visited.push(STEPS[state.step]);
    }
    expect(visited).toEqual(['term', 'courses', 'missing', 'review', 'export', 'export']);
    expect(isStepAvailable(manual, STEPS.indexOf('sections'))).toBe(false);
    expect(reducer(manual, { type: 'goTo', step: STEPS.indexOf('program') }).step).toBe(manual.step);
  });
});

describe('school and term', () => {
  it('changing school resets the schedule', () => {
    const withCourse = run(initialState, { type: 'selectSchool', school: uqam },
      { type: 'addCourse', course: { code: 'INF1120', title: 'Prog', sections: [] } });
    expect(run(withCourse, { type: 'selectSchool', school: other }).courses).toEqual([]);
    expect(run(withCourse, { type: 'selectSchool', school: uqam }).courses).toHaveLength(1);
  });

  it('changing term keeps courses, reloads their sections and drops published choices', () => {
    let state = run(initialState, { type: 'selectSchool', school: uqam }, { type: 'selectTerm', term: { id: '20263' } },
      { type: 'addCourse', course: { code: 'INF1120', title: 'Prog', sections: [section('INF1120')] } },
      { type: 'chooseSection', section: section('INF1120') },
      { type: 'addManualCourse', section: section('GLO2004'), title: 'GL' });
    state = reducer(state, { type: 'selectTerm', term: { id: '20271' } });
    expect(state.courses.find((c) => c.code === 'INF1120').sections).toBeNull();
    expect(Object.keys(state.selections)).toEqual(['GLO2004']);
  });

  it('updates term fields', () => {
    const state = run(initialState, { type: 'selectTerm', term: { id: 'x', startDate: null } },
      { type: 'updateTerm', changes: { startDate: '2027-01-11' } });
    expect(state.term).toEqual({ id: 'x', startDate: '2027-01-11' });
  });
});

describe('courses and selections', () => {
  const withCourses = run(initialState,
    { type: 'addCourse', course: { code: 'INF1120', title: 'Programmation I', sections: [section('INF1120')] } },
    { type: 'addCourse', course: { code: 'MAT1060', title: 'Maths', sections: [section('MAT1060')] } });

  it('ignores a course added twice', () => {
    expect(reducer(withCourses, { type: 'addCourse', course: { code: 'INF1120' } }).courses).toHaveLength(2);
  });

  it('chooses, edits and unchooses a section', () => {
    let state = reducer(withCourses, { type: 'chooseSection', section: section('INF1120', '020') });
    expect(state.selections.INF1120).toEqual({ courseTitle: 'Programmation I', section: section('INF1120', '020') });
    state = reducer(state, { type: 'updateSelection', code: 'INF1120', section: { ...section('INF1120', '020'), instructor: 'Ada' } });
    expect(state.selections.INF1120.section.instructor).toBe('Ada');
    expect(reducer(state, { type: 'unchooseSection', code: 'INF1120' }).selections).toEqual({});
  });

  it('removing a course removes its selection', () => {
    const state = run(withCourses, { type: 'chooseSection', section: section('INF1120') }, { type: 'removeCourse', code: 'INF1120' });
    expect(state.courses.map((c) => c.code)).toEqual(['MAT1060']);
    expect(state.selections).toEqual({});
  });

  it('a manual course is added and selected at once', () => {
    const state = reducer(initialState, { type: 'addManualCourse', section: section('GLO2004', 'A'), title: 'Génie logiciel' });
    expect(state.courses[0]).toMatchObject({ code: 'GLO2004', manual: true });
    expect(orderedSelections(state)).toEqual([{ courseTitle: 'Génie logiciel', section: section('GLO2004', 'A') }]);
  });

  it('orders selections like the course list', () => {
    const state = run(withCourses, { type: 'chooseSection', section: section('MAT1060') },
      { type: 'chooseSection', section: section('INF1120') });
    expect(orderedSelections(state).map((s) => s.section.courseCode)).toEqual(['INF1120', 'MAT1060']);
  });

  it('stores sections loaded after a term change', () => {
    const state = reducer(withCourses, { type: 'setCourseSections', code: 'INF1120', course: { credits: 3 }, sections: [] });
    expect(state.courses[0]).toMatchObject({ credits: 3, sections: [] });
  });
});

describe('preferences', () => {
  it('sets and toggles values', () => {
    let state = reducer(initialState, { type: 'setPreference', key: 'timeOfDay', value: 'EVENING' });
    state = run(state, { type: 'togglePreference', key: 'freeDays', value: 'FRIDAY' },
      { type: 'togglePreference', key: 'freeDays', value: 'MONDAY' },
      { type: 'togglePreference', key: 'freeDays', value: 'FRIDAY' });
    expect(state.preferences).toMatchObject({ timeOfDay: 'EVENING', freeDays: ['MONDAY'] });
  });

  it('keeps the reading week choice', () => {
    expect(reducer(initialState, { type: 'setExcludeReadingWeek', value: false }).excludeReadingWeek).toBe(false);
  });

  it('rejects unknown actions', () => {
    expect(() => reducer(initialState, { type: 'nope' })).toThrow('Unknown action');
  });
});
