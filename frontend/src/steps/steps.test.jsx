import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { meeting, renderWithState, section, term } from '../test/renderWithState.jsx';
import { STEPS } from '../state/reducer.js';
import MissingStep from './MissingStep.jsx';
import ReviewStep from './ReviewStep.jsx';
import SectionsStep from './SectionsStep.jsx';
import CoursesStep from './CoursesStep.jsx';
import ExportStep from './ExportStep.jsx';

vi.mock('../api/client.js', () => ({
  api: {
    rank: vi.fn(),
    conflicts: vi.fn(),
    sections: vi.fn(),
    programCourses: vi.fn(),
    exportIcs: vi.fn(),
  },
}));
const { api } = await import('../api/client.js');

const uqam = { id: 'uqam', name: 'UQAM', supported: true };
const course = (sections) => ({ code: 'INF1120', title: 'Programmation I', credits: 3, sections });

beforeEach(() => {
  vi.clearAllMocks();
});

describe('MissingStep', () => {
  it('shows a badge for each missing value and lets the user fill it', async () => {
    const user = userEvent.setup();
    const { state } = renderWithState(<MissingStep />, {
      school: uqam, term, step: STEPS.indexOf('missing'), courses: [course([section()])],
      selections: { INF1120: { courseTitle: 'Programmation I', section: section() } },
    });

    expect(screen.getAllByText('Information non disponible').length).toBeGreaterThan(0);
    // the meeting has its own dates, so the missing term end date is not asked here
    expect(screen.queryByLabelText(/Dernière date/)).toBeNull();

    await user.type(screen.getByLabelText(/^Enseignant/), 'Mélanie Lord');
    await user.type(screen.getByLabelText(/^Local/), 'PK-1234');

    const updated = state().selections.INF1120.section;
    expect(updated.instructor).toBe('Mélanie Lord');
    expect(updated.meetings[0].room).toBe('PK-1234');
    expect(updated.missingFields).toEqual([]);
    // fields stay on screen after being filled, so they can be corrected
    expect(screen.getByLabelText(/^Enseignant/)).toHaveValue('Mélanie Lord');
  });

  it('asks for essential values of a section without schedule', async () => {
    const user = userEvent.setup();
    const empty = section({ meetings: [], missingFields: ['meetings'] });
    const { state } = renderWithState(<MissingStep />, {
      school: uqam, term: { ...term, endDate: '2027-04-30' }, courses: [course([empty])],
      selections: { INF1120: { courseTitle: 'Programmation I', section: empty } },
    });

    expect(screen.getByText(/Aucune séance publiée/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Ajouter une séance' }));

    const day = screen.getByLabelText(/^Jour/);
    expect(day).toBeRequired();
    await user.selectOptions(day, 'TUESDAY');
    expect(state().selections.INF1120.section.meetings[0].day).toBe('TUESDAY');
    expect(screen.getAllByText('Obligatoire pour l’export').length).toBe(3);
  });
});

describe('SectionsStep', () => {
  it('orders sections by score and records the choice', async () => {
    const user = userEvent.setup();
    const day = section({ sectionNumber: '010' });
    const evening = section({ sectionNumber: '040', instructor: 'Ada', meetings: [meeting({ start: '18:00', end: '21:00' })] });
    api.rank.mockResolvedValue([
      { courseCode: 'INF1120', sectionNumber: '040', score: 100, compatible: true,
        criteria: [{ criterion: 'TIME_OF_DAY', outcome: 'MATCH' }], conflicts: [] },
      { courseCode: 'INF1120', sectionNumber: '010', score: 0, compatible: false,
        criteria: [{ criterion: 'TIME_OF_DAY', outcome: 'MISMATCH' }],
        conflicts: [{ second: { courseCode: 'MAT1060', sectionNumber: '020' } }] },
    ]);
    const { state } = renderWithState(<SectionsStep />, { school: uqam, term, courses: [course([day, evening])] });

    await screen.findByText(/Compatibilité : 100 %/);
    const cards = screen.getAllByRole('article');
    expect(within(cards[0]).getByText('Groupe 40')).toBeInTheDocument();
    expect(within(cards[1]).getByText(/En conflit avec MAT1060 gr. 20/)).toBeInTheDocument();
    expect(within(cards[1]).getByText(/Moment de la journée : non respecté/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Continuer' })).toBeDisabled();

    await user.click(within(cards[0]).getByRole('button', { name: 'Choisir ce groupe' }));

    expect(state().selections.INF1120.section.sectionNumber).toBe('040');
    expect(screen.getByRole('button', { name: 'Continuer' })).toBeEnabled();
    expect(api.rank).toHaveBeenCalledWith(expect.objectContaining({ sections: [day, evening] }));
  });
});

describe('CoursesStep', () => {
  it('adds a course by code and shows a translated error for an unknown one', async () => {
    const user = userEvent.setup();
    api.sections.mockResolvedValueOnce({ course: { code: 'INF1120', title: 'Programmation I', credits: 3 }, termId: '20271', sections: [section()] });
    api.sections.mockRejectedValueOnce({ code: 'COURSE_NOT_FOUND' });
    const { state } = renderWithState(<CoursesStep />, { school: uqam, term });

    await user.type(screen.getByLabelText('Sigle du cours'), 'inf1120');
    await user.click(screen.getByRole('button', { name: 'Ajouter le cours' }));
    await screen.findByText(/1 groupe/);
    expect(api.sections).toHaveBeenCalledWith('uqam', '20271', 'INF1120');
    expect(state().courses[0].code).toBe('INF1120');

    await user.type(screen.getByLabelText('Sigle du cours'), 'ZZZ9999');
    await user.click(screen.getByRole('button', { name: 'Ajouter le cours' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Cours introuvable');
  });
});

describe('ReviewStep', () => {
  it('blocks confirmation while an essential value is missing', async () => {
    api.conflicts.mockResolvedValue([]);
    const incomplete = section({ meetings: [meeting({ start: null })] });
    renderWithState(<ReviewStep />, {
      school: uqam, term, courses: [course([incomplete])],
      selections: { INF1120: { courseTitle: 'Programmation I', section: incomplete } },
    });

    expect(screen.getByRole('button', { name: 'Confirmer cet horaire' })).toBeDisabled();
    expect(screen.getAllByText(/l’export est bloqué/).length).toBeGreaterThan(0);
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
      school: uqam, term, courses: [course([complete])],
      selections: { INF1120: { courseTitle: 'Programmation I', section: complete } },
    });

    expect(await screen.findByText('INF1120 gr. 20 et MAT1060 gr. 10 : Lundi de 11 h 00 à 12 h 30')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirmer cet horaire' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: 'Liste' }));
    expect(screen.getByRole('table')).toHaveTextContent('Ada');
  });
});

describe('ExportStep', () => {
  it('sends the reading week only when it stays excluded', async () => {
    const user = userEvent.setup();
    api.exportIcs.mockResolvedValue({ blob: new Blob(['x']), filename: 'horaire-uqam-20271.ics' });
    URL.createObjectURL = vi.fn(() => 'blob:x');
    URL.revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const withBreak = { ...term, readingWeekDates: ['2027-03-01', '2027-03-02'], readingWeekNote: 'selon facultés/écoles' };
    renderWithState(<ExportStep />, {
      school: uqam, term: withBreak, courses: [course([section()])],
      selections: { INF1120: { courseTitle: 'Programmation I', section: section() } },
    });

    await user.click(screen.getByRole('button', { name: 'Télécharger le fichier .ics' }));
    expect(api.exportIcs.mock.calls[0][0].excludedDates).toEqual(['2027-03-01', '2027-03-02']);
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
    renderWithState(<ExportStep />, { school: uqam, term, selections: {} });

    await user.click(screen.getByRole('button', { name: 'Télécharger le fichier .ics' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Horaire incomplet : INF1120 gr. 020 : jour manquant.');
  });
});
