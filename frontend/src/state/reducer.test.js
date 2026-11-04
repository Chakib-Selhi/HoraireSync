import { describe, expect, it } from 'vitest';
import { initialState, orderedSelections, reducer, SCHOOL, STEPS } from './reducer.js';

const run = (state, ...actions) => actions.reduce(reducer, state);
const section = (code, number = '010') => ({ courseCode: code, sectionNumber: number, meetings: [], missingFields: [] });

describe('navigation', () => {
  it('starts at the term step with UQAM as the only school', () => {
    expect(STEPS[initialState.step]).toBe('term');
    expect(initialState.school).toEqual(SCHOOL);
    expect(STEPS).not.toContain('school');
    expect(STEPS).not.toContain('missing');
  });

  it('goes forward and back within bounds', () => {
    expect(run(initialState, { type: 'back' }).step).toBe(0);
    expect(run(initialState, { type: 'next' }, { type: 'next' }).step).toBe(2);
    const last = { ...initialState, step: STEPS.length - 1 };
    expect(run(last, { type: 'next' }).step).toBe(STEPS.length - 1);
    expect(reducer(initialState, { type: 'goTo', step: 99 }).step).toBe(STEPS.length - 1);
  });
});

describe('term', () => {
  it('changing term keeps courses, reloads their sections and drops published choices', () => {
    let state = run(initialState, { type: 'selectTerm', term: { id: '20263' } },
      { type: 'addCourse', code: 'INF1120' },
      { type: 'courseLoaded', code: 'INF1120', course: { title: 'Prog' }, sections: [section('INF1120')] },
      { type: 'chooseSection', section: section('INF1120') },
      { type: 'addManualCourse', section: section('GLO2004'), title: 'GL' });
    state = reducer(state, { type: 'selectTerm', term: { id: '20271' } });
    expect(state.courses.find((c) => c.code === 'INF1120')).toMatchObject({ sections: null, status: 'loading' });
    expect(Object.keys(state.selections)).toEqual(['GLO2004']);
    expect(reducer(state, { type: 'selectTerm', term: { id: '20271', label: 'x' } }).term.label).toBe('x');
  });
});

describe('courses', () => {
  const added = run(initialState, { type: 'addCourse', code: ' inf1120 ', title: 'Programmation I' },
    { type: 'addCourse', code: 'MAT1060' });

  it('adds a course at once in the loading state', () => {
    expect(added.courses.map((c) => [c.code, c.status, c.title])).toEqual([
      ['INF1120', 'loading', 'Programmation I'], ['MAT1060', 'loading', null]]);
    expect(reducer(added, { type: 'addCourse', code: 'INF1120' }).courses).toHaveLength(2);
    expect(reducer(added, { type: 'addCourse', code: '  ' })).toBe(added);
  });

  it('stores loaded sections and the title from the course page', () => {
    const state = reducer(added, { type: 'courseLoaded', code: 'MAT1060',
      course: { title: 'Mathématiques', credits: 3 }, sections: [section('MAT1060')] });
    expect(state.courses[1]).toMatchObject({ title: 'Mathématiques', credits: 3, status: 'ready', sections: [section('MAT1060')] });
  });

  it('keeps the error of a course that could not be loaded', () => {
    const state = reducer(added, { type: 'courseFailed', code: 'INF1120', error: { code: 'COURSE_NOT_FOUND' } });
    expect(state.courses[0]).toMatchObject({ status: 'error', sections: [], error: { code: 'COURSE_NOT_FOUND' } });
  });

  it('chooses and unchooses a section, removing a course removes its choice', () => {
    let state = reducer(added, { type: 'chooseSection', section: section('INF1120', '020') });
    expect(state.selections.INF1120).toEqual({ courseTitle: 'Programmation I', section: section('INF1120', '020') });
    expect(reducer(state, { type: 'unchooseSection', code: 'INF1120' }).selections).toEqual({});
    state = reducer(state, { type: 'removeCourse', code: 'INF1120' });
    expect(state.courses.map((c) => c.code)).toEqual(['MAT1060']);
    expect(state.selections).toEqual({});
  });

  it('a manual course is ready and selected at once', () => {
    const state = reducer(initialState, { type: 'addManualCourse', section: section('GLO2004', 'A'), title: 'Génie logiciel' });
    expect(state.courses[0]).toMatchObject({ code: 'GLO2004', manual: true, status: 'ready' });
    expect(orderedSelections(state)).toEqual([{ courseTitle: 'Génie logiciel', section: section('GLO2004', 'A') }]);
    expect(reducer(state, { type: 'addManualCourse', section: section('GLO2004'), title: 'x' })).toBe(state);
  });

  it('orders selections like the course list', () => {
    const state = run(added, { type: 'chooseSection', section: section('MAT1060') },
      { type: 'chooseSection', section: section('INF1120') });
    expect(orderedSelections(state).map((s) => s.section.courseCode)).toEqual(['INF1120', 'MAT1060']);
  });
});

describe('preferences and export options', () => {
  it('sets and toggles values', () => {
    let state = reducer(initialState, { type: 'setPreference', key: 'timeOfDay', value: 'EVENING' });
    state = run(state, { type: 'togglePreference', key: 'freeDays', value: 'FRIDAY' },
      { type: 'togglePreference', key: 'freeDays', value: 'MONDAY' },
      { type: 'togglePreference', key: 'freeDays', value: 'FRIDAY' });
    expect(state.preferences).toMatchObject({ timeOfDay: 'EVENING', freeDays: ['MONDAY'] });
    expect(reducer(state, { type: 'selectProgram', program: { code: '7416' } }).program.code).toBe('7416');
  });

  it('keeps the reading week choice and rejects unknown actions', () => {
    expect(reducer(initialState, { type: 'setExcludeReadingWeek', value: false }).excludeReadingWeek).toBe(false);
    expect(() => reducer(initialState, { type: 'nope' })).toThrow('Unknown action');
  });
});
