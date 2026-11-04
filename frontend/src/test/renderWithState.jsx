import { render } from '@testing-library/react';
import { initialState } from '../state/reducer.js';
import { ScheduleProvider, useSchedule } from '../state/ScheduleContext.jsx';

/** Renders a screen with a given wizard state and exposes the live state for assertions. */
export function renderWithState(ui, overrides = {}) {
  const probe = { current: null };
  function Probe() {
    probe.current = useSchedule().state;
    return null;
  }
  const result = render(
    <ScheduleProvider initial={{ ...initialState, ...overrides }}>
      {ui}
      <Probe />
    </ScheduleProvider>,
  );
  return { ...result, state: () => probe.current };
}

export const meeting = (over = {}) => ({
  day: 'MONDAY', start: '09:30:00', end: '12:30:00', room: null, campus: 'Campus de Montréal', type: 'LECTURE',
  onlyDate: null, firstDate: '2027-01-11', lastDate: '2027-05-02', ...over,
});

export const section = (over = {}) => ({
  courseCode: 'INF1120', sectionNumber: '020', instructor: null, mode: 'IN_PERSON', campus: null,
  meetings: [meeting()], notes: [], missingFields: ['instructor', 'meetings[0].room'],
  sourceUrl: 'https://etudier.uqam.ca/cours?sigle=INF1120', ...over,
});

export const term = { id: '20271', label: 'Hiver 2027', startDate: '2027-01-11', endDate: null,
  excludedDates: [], readingWeekDates: [], readingWeekNote: null, missingFields: ['endDate'] };
