/**
 * Whole wizard state in one reducer. Screens dispatch actions; network calls stay in the screens.
 *
 * courses: [{ code, title, credits, sections, manual }]
 * selections: { [courseCode]: { courseTitle, section } }   the section may be edited by the user
 */
export const STEPS = ['school', 'term', 'program', 'courses', 'preferences', 'sections', 'missing', 'review', 'export'];

export const initialState = {
  step: 0,
  school: null,
  manual: false,
  term: null,
  program: null,
  courses: [],
  selections: {},
  preferences: { timeOfDay: 'ANY', modes: [], campuses: [], freeDays: [] },
  excludeReadingWeek: true,
};

/** Steps skipped in manual mode: no program list, no ranking of published sections. */
const MANUAL_SKIPS = new Set(['program', 'preferences', 'sections']);

export function isStepAvailable(state, step) {
  return !(state.manual && MANUAL_SKIPS.has(STEPS[step]));
}

function move(state, direction) {
  let step = state.step + direction;
  while (step > 0 && step < STEPS.length - 1 && !isStepAvailable(state, step)) {
    step += direction;
  }
  return { ...state, step: Math.min(Math.max(step, 0), STEPS.length - 1) };
}

const toggle = (list, value) => (list.includes(value) ? list.filter((v) => v !== value) : [...list, value]);

export function reducer(state, action) {
  switch (action.type) {
    case 'next':
      return move(state, 1);
    case 'back':
      return move(state, -1);
    case 'goTo':
      return isStepAvailable(state, action.step) ? { ...state, step: action.step } : state;

    case 'selectSchool': {
      const sameSchool = state.school?.id === action.school.id;
      const base = sameSchool ? state : { ...initialState, step: state.step };
      return { ...base, school: action.school, manual: !action.school.supported };
    }
    case 'selectTerm':
      // Another term means other sections: keep the course list, drop loaded sections and choices.
      if (state.term?.id === action.term.id) return { ...state, term: action.term };
      return {
        ...state,
        term: action.term,
        courses: state.courses.map((c) => (c.manual ? c : { ...c, sections: null })),
        selections: Object.fromEntries(Object.entries(state.selections).filter(([code]) =>
          state.courses.find((c) => c.code === code)?.manual)),
      };
    case 'updateTerm':
      return { ...state, term: { ...state.term, ...action.changes } };
    case 'selectProgram':
      return { ...state, program: action.program };

    case 'addCourse':
      if (state.courses.some((c) => c.code === action.course.code)) return state;
      return { ...state, courses: [...state.courses, action.course] };
    case 'setCourseSections':
      return {
        ...state,
        courses: state.courses.map((c) => (c.code === action.code ? { ...c, ...action.course, sections: action.sections } : c)),
      };
    case 'removeCourse': {
      const { [action.code]: removed, ...selections } = state.selections;
      return { ...state, courses: state.courses.filter((c) => c.code !== action.code), selections };
    }
    case 'addManualCourse': {
      if (state.courses.some((c) => c.code === action.section.courseCode)) return state;
      const course = { code: action.section.courseCode, title: action.title, credits: null, sections: [action.section], manual: true };
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
    case 'updateSelection':
      return {
        ...state,
        selections: {
          ...state.selections,
          [action.code]: { ...state.selections[action.code], section: action.section },
        },
      };

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
