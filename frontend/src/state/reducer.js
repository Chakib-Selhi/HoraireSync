/**
 * Whole wizard state in one reducer. Screens dispatch actions; network calls stay outside.
 *
 * courses: [{ code, title, credits, sections, status, error, manual }]
 *   status: 'loading' while the sections are fetched, 'ready', or 'error'
 * selections: { [courseCode]: { courseTitle, section } }
 */
export const STEPS = ['term', 'program', 'courses', 'preferences', 'sections', 'review', 'export'];

/** Only UQAM is supported: the school is fixed, the backend stays multi-school. */
export const SCHOOL = { id: 'uqam', name: 'Université du Québec à Montréal (UQAM)', supported: true };

export const initialState = {
  step: 0,
  school: SCHOOL,
  term: null,
  program: null,
  courses: [],
  selections: {},
  preferences: { timeOfDay: 'ANY', modes: [], campuses: [], freeDays: [] },
  excludeReadingWeek: true,
};

const clampStep = (step) => Math.min(Math.max(step, 0), STEPS.length - 1);
const toggle = (list, value) => (list.includes(value) ? list.filter((v) => v !== value) : [...list, value]);
const updateCourse = (state, code, changes) => ({
  ...state,
  courses: state.courses.map((c) => (c.code === code ? { ...c, ...changes } : c)),
});

export function reducer(state, action) {
  switch (action.type) {
    case 'next':
      return { ...state, step: clampStep(state.step + 1) };
    case 'back':
      return { ...state, step: clampStep(state.step - 1) };
    case 'goTo':
      return { ...state, step: clampStep(action.step) };

    case 'selectTerm':
      // Another term means other sections: keep the course list, reload published sections, drop their choices.
      if (state.term?.id === action.term.id) return { ...state, term: action.term };
      return {
        ...state,
        term: action.term,
        courses: state.courses.map((c) => (c.manual ? c : { ...c, sections: null, status: 'loading', error: null })),
        selections: Object.fromEntries(
          Object.entries(state.selections).filter(([code]) => state.courses.find((c) => c.code === code)?.manual),
        ),
      };
    case 'selectProgram':
      return { ...state, program: action.program };

    case 'addCourse': {
      // Added at once, sections loaded in the background: the user can keep adding courses meanwhile.
      const code = action.code.trim().toUpperCase();
      if (!code || state.courses.some((c) => c.code === code)) return state;
      const course = { code, title: action.title ?? null, credits: null, sections: null, status: 'loading', error: null };
      return { ...state, courses: [...state.courses, course] };
    }
    case 'courseLoaded':
      return updateCourse(state, action.code, {
        title: action.course?.title ?? state.courses.find((c) => c.code === action.code)?.title ?? null,
        credits: action.course?.credits ?? null,
        sections: action.sections,
        status: 'ready',
        error: null,
      });
    case 'courseFailed':
      return updateCourse(state, action.code, { sections: [], status: 'error', error: action.error });
    case 'removeCourse': {
      const { [action.code]: removed, ...selections } = state.selections;
      return { ...state, courses: state.courses.filter((c) => c.code !== action.code), selections };
    }
    case 'addManualCourse': {
      if (state.courses.some((c) => c.code === action.section.courseCode)) return state;
      const course = {
        code: action.section.courseCode, title: action.title, credits: null, sections: [action.section],
        status: 'ready', error: null, manual: true,
      };
      return {
        ...state,
        courses: [...state.courses, course],
        selections: { ...state.selections, [course.code]: { courseTitle: action.title, section: action.section } },
      };
    }

    case 'setPreference':
      return { ...state, preferences: { ...state.preferences, [action.key]: action.value } };
    case 'togglePreference':
      return {
        ...state,
        preferences: { ...state.preferences, [action.key]: toggle(state.preferences[action.key], action.value) },
      };

    case 'chooseSection': {
      const course = state.courses.find((c) => c.code === action.section.courseCode);
      return {
        ...state,
        selections: {
          ...state.selections,
          [action.section.courseCode]: { courseTitle: course?.title ?? null, section: action.section },
        },
      };
    }
    case 'unchooseSection': {
      const { [action.code]: removed, ...selections } = state.selections;
      return { ...state, selections };
    }

    case 'setExcludeReadingWeek':
      return { ...state, excludeReadingWeek: action.value };
    default:
      throw new Error(`Unknown action ${action.type}`);
  }
}

/** Selections in the order of the course list. */
export function orderedSelections(state) {
  return state.courses.map((c) => state.selections[c.code]).filter(Boolean);
}
