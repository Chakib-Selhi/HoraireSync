import { useEffect, useRef } from 'react';
import { api } from '../api/client.js';

/**
 * Loads the sections of every course in the "loading" state, in the background.
 *
 * It lives in App, which stays mounted, so a load started on the Courses screen completes even if the user
 * moves on. Each course is requested once per term (the backend then answers from its cache).
 */
export function useCourseLoader(state, dispatch) {
  const inFlight = useRef(new Set());
  const termId = state.term?.id;
  const schoolId = state.school?.id;

  useEffect(() => {
    if (!termId || !schoolId) return;
    for (const course of state.courses) {
      const key = `${termId}|${course.code}`;
      if (course.manual || course.status !== 'loading' || inFlight.current.has(key)) continue;
      inFlight.current.add(key);
      api.sections(schoolId, termId, course.code).then(
        (result) => dispatch({ type: 'courseLoaded', code: course.code, course: result.course, sections: result.sections }),
        (error) => dispatch({ type: 'courseFailed', code: course.code, error }),
      ).finally(() => inFlight.current.delete(key));
    }
  }, [state.courses, termId, schoolId, dispatch]);
}
