import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { meeting, renderWithState, section, term } from '../test/renderWithState.jsx';
import App from '../App.jsx';
import { initialState } from '../state/reducer.js';
import { ScheduleProvider } from '../state/ScheduleContext.jsx';
import CoursesStep from './CoursesStep.jsx';
import ExportStep from './ExportStep.jsx';
import ReviewStep from './ReviewStep.jsx';
import SectionsStep from './SectionsStep.jsx';
import TermStep from './TermStep.jsx';

vi.mock('../api/client.js', () => ({
  warmUp: vi.fn(),
  onSlowRequests: vi.fn(() => () => {}),
  api: {
    terms: vi.fn(),
    rank: vi.fn(),
    conflicts: vi.fn(),
    sections: vi.fn(),
    programCourses: vi.fn(),
    exportIcs: vi.fn(),
  },
}));
const { api } = await import('../api/client.js');

const course = (sections, over = {}) => ({
  code: 'INF1120', title: 'Programmation I', credits: 3, sections, status: 'ready', error: null, ...over,
});

beforeEach(() => {
  vi.clearAllMocks();
});

describe('TermStep', () => {
  it('shows only a dropdown and selects the single upcoming term', async () => {
    api.terms.mockResolvedValue([term]);
    const { state } = renderWithState(<TermStep />);

    await waitFor(() => expect(state().term?.id).toBe('20271'));
    expect(screen.getByLabelText('Session')).toHaveValue('20271');
    expect(screen.queryByLabelText(/Début de la session/)).toBeNull();
    expect(screen.queryByLabelText(/Fin de la session/)).toBeNull();
    expect(screen.getByRole('button', { name: 'Continuer' })).toBeEnabled();
  });

  it('explains when no upcoming term is published', async () => {
    api.terms.mockResolvedValue([]);
    renderWithState(<TermStep />);

    expect(await screen.findByText(/Aucune session à venir/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Continuer' })).toBeDisabled();
  });
});

describe('CoursesStep with the background loader', () => {
  it('adds several courses without waiting for the previous one', async () => {
    const user = userEvent.setup();
    let resolveFirst;
    api.sections.mockImplementation((school, termId, code) =>
      code === 'INF1120'
        ? new Promise((resolve) => { resolveFirst = resolve; })
        : Promise.reject({ code: 'COURSE_NOT_FOUND' }));
    render(
      <ScheduleProvider initial={{ ...initialState, term, step: 2 }}>
        <App />
      </ScheduleProvider>,
    );

    const field = screen.getByLabelText('Sigle du cours');
    await user.type(field, 'inf1120');
    await user.click(screen.getByRole('button', { name: 'Ajouter' }));
    // the field is free again right away, while INF1120 is still loading
    await user.type(field, 'ZZZ9999');
    await user.click(screen.getByRole('button', { name: 'Ajouter' }));

    expect(screen.getByText('Recherche des groupes…')).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toHaveTextContent('Cours introuvable');
    expect(api.sections).toHaveBeenCalledTimes(2);

    resolveFirst({ course: { code: 'INF1120', title: 'Programmation I', credits: 3 }, sections: [section()] });
    expect(await screen.findByText(/1 groupe\(s\)/)).toBeInTheDocument();
  });

  it('rejects a course added twice and offers manual entry', async () => {
    const user = userEvent.setup();
    renderWithState(<CoursesStep />, { term, courses: [course([section()])] });

    await user.type(screen.getByLabelText('Sigle du cours'), 'INF1120');
    await user.click(screen.getByRole('button', { name: 'Ajouter' }));
    expect(screen.getByRole('alert')).toHaveTextContent('déjà dans votre liste');

    await user.click(screen.getByRole('button', { name: 'Saisir un cours manuellement' }));
    expect(screen.getByRole('heading', { name: 'Ajouter un cours' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Annuler' }));
    expect(screen.queryByRole('heading', { name: 'Ajouter un cours' })).toBeNull();
  });

  it('adds a course typed by hand, selected at once', async () => {
    const user = userEvent.setup();
    const { state } = renderWithState(<CoursesStep />, { term });

    await user.click(screen.getByRole('button', { name: 'Saisir un cours manuellement' }));
    await user.type(screen.getByLabelText(/^Sigle ou code/), 'ang2011');
    await user.selectOptions(screen.getByLabelText(/^Jour/), 'FRIDAY');
    await user.type(screen.getByLabelText(/^Début/), '09:00');
    await user.type(screen.getByLabelText(/^Fin/), '12:00');
    await user.click(screen.getByRole('button', { name: 'Ajouter ce cours' }));

    expect(state().courses[0]).toMatchObject({ code: 'ANG2011', manual: true });
    expect(state().selections.ANG2011.section.meetings[0]).toMatchObject({ day: 'FRIDAY', start: '09:00', end: '12:00' });
    expect(screen.getByText('Saisi manuellement')).toBeInTheDocument();
  });
});

describe('SectionsStep', () => {
  it('orders sections by score, keeps details collapsed and records the choice', async () => {
    const user = userEvent.setup();
    const day = section({ sectionNumber: '010', notes: ['Examens en présentiel'] });
    const evening = section({ sectionNumber: '040', instructor: 'Ada', meetings: [meeting({ start: '18:00', end: '21:00' })] });
    api.rank.mockResolvedValue([
      { courseCode: 'INF1120', sectionNumber: '040', score: 100, compatible: true,
        criteria: [{ criterion: 'TIME_OF_DAY', outcome: 'MATCH' }], conflicts: [] },
      { courseCode: 'INF1120', sectionNumber: '010', score: 0, compatible: false,
        criteria: [{ criterion: 'TIME_OF_DAY', outcome: 'MISMATCH' }],
        conflicts: [{ second: { courseCode: 'MAT1060', sectionNumber: '020' } }] },
    ]);
    const { state } = renderWithState(<SectionsStep />, { term, courses: [course([day, evening])] });

    await screen.findByText(/Compatibilité : 100 %/);
    const cards = screen.getAllByRole('article');
    expect(within(cards[0]).getByText('Groupe 40')).toBeInTheDocument();
    expect(within(cards[1]).getByText(/En conflit avec MAT1060 gr. 20/)).toBeInTheDocument();
    expect(within(cards[1]).getByText('Ne respecte pas une préférence · Compatibilité : 0 %')).toBeInTheDocument();
    expect(screen.queryByText('Information non disponible')).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Ajouter un cours' })).toBeNull();
    expect(within(cards[1]).getByText('Détails')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Continuer' })).toBeDisabled();

    await user.click(within(cards[0]).getByRole('button', { name: 'Choisir ce groupe' }));

    expect(state().selections.INF1120.section.sectionNumber).toBe('040');
    expect(screen.getByRole('button', { name: 'Continuer' })).toBeEnabled();
  });

  it('shows no percentage when no preference is set', async () => {
    api.rank.mockResolvedValue([
      { courseCode: 'INF1120', sectionNumber: '020', score: 100, compatible: true, criteria: [], conflicts: [] },
    ]);
    renderWithState(<SectionsStep />, { term, courses: [course([section()])] });

    expect(await screen.findByText('Compatible')).toBeInTheDocument();
    expect(screen.queryByText(/Compatibilité/)).toBeNull();
  });

  it('a course without published section can only be removed, a section without schedule cannot be chosen', async () => {
    const user = userEvent.setup();
    api.rank.mockResolvedValue([]);
    const noSchedule = section({ sectionNumber: '063', meetings: [] });
    const { state } = renderWithState(<SectionsStep />, {
      term,
      courses: [course([], { code: 'PSY7010', title: 'Psychologie' }), course([noSchedule])],
    });

    expect(screen.getByText(/Aucun groupe publié/)).toBeInTheDocument();
    expect(screen.getByText('Horaire non publié')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Choisir ce groupe' })).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Retirer ce cours' }));
    expect(state().courses.map((c) => c.code)).toEqual(['INF1120']);
  });
});

describe('ReviewStep', () => {
  it('blocks confirmation while a group lacks a day or a time', async () => {
    api.conflicts.mockResolvedValue([]);
    const incomplete = section({ meetings: [meeting({ start: null })] });
    renderWithState(<ReviewStep />, {
      term, courses: [course([incomplete])],
      selections: { INF1120: { courseTitle: 'Programmation I', section: incomplete } },
    });

    expect(screen.getByRole('button', { name: 'Confirmer cet horaire' })).toBeDisabled();
    expect(screen.getAllByText(/choisissez un autre groupe/).length).toBeGreaterThan(0);
    expect(await screen.findByText('Aucun conflit.')).toBeInTheDocument();
  });

  it('lists conflicts and switches to the list view', async () => {
    const user = userEvent.setup();
    api.conflicts.mockResolvedValue([{
      first: { courseCode: 'INF1120', sectionNumber: '020', meetingIndex: 0 },
      second: { courseCode: 'MAT1060', sectionNumber: '010', meetingIndex: 0 },
      day: 'MONDAY', date: null, overlapStart: '11:00:00', overlapEnd: '12:30:00',
    }]);
    const complete = section({ instructor: 'Ada', missingFields: [] });
    renderWithState(<ReviewStep />, {
      term, courses: [course([complete])],
      selections: { INF1120: { courseTitle: 'Programmation I', section: complete } },
    });

    expect(await screen.findByText('INF1120 gr. 20 et MAT1060 gr. 10 : Lundi de 11 h 00 à 12 h 30')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirmer cet horaire' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: 'Liste' }));
    expect(screen.getByRole('table')).toHaveTextContent('Ada');
  });
});

describe('ExportStep', () => {
  it('sends the reading week only when it stays excluded, with term dates from the sections', async () => {
    const user = userEvent.setup();
    api.exportIcs.mockResolvedValue({ blob: new Blob(['x']), filename: 'horaire-uqam-20271.ics' });
    URL.createObjectURL = vi.fn(() => 'blob:x');
    URL.revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const withBreak = { ...term, readingWeekDates: ['2027-03-01', '2027-03-02'], readingWeekNote: 'selon facultés/écoles' };
    renderWithState(<ExportStep />, {
      term: withBreak, courses: [course([section()])],
      selections: { INF1120: { courseTitle: 'Programmation I', section: section() } },
    });

    await user.click(screen.getByRole('button', { name: 'Télécharger le fichier .ics' }));
    expect(api.exportIcs.mock.calls[0][0]).toMatchObject({
      excludedDates: ['2027-03-01', '2027-03-02'], termStart: '2027-01-11', termEnd: '2027-05-02',
    });
    expect(await screen.findByText(/Fichier téléchargé : horaire-uqam-20271.ics/)).toBeInTheDocument();
    expect(click).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('checkbox', { name: /Exclure la semaine de relâche/ }));
    await user.click(screen.getByRole('button', { name: 'Télécharger le fichier .ics' }));
    await waitFor(() => expect(api.exportIcs).toHaveBeenCalledTimes(2));
    expect(api.exportIcs.mock.calls[1][0].excludedDates).toEqual([]);
  });

  it('shows the server validation message', async () => {
    const user = userEvent.setup();
    api.exportIcs.mockRejectedValue({ code: 'INVALID_SCHEDULE', message: 'Horaire incomplet : INF1120 gr. 020 : jour manquant.' });
    renderWithState(<ExportStep />, { term, selections: {} });

    await user.click(screen.getByRole('button', { name: 'Télécharger le fichier .ics' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Horaire incomplet : INF1120 gr. 020 : jour manquant.');
  });
});
