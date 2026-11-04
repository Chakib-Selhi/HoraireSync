import { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import ErrorMessage from '../components/ErrorMessage.jsx';
import ManualCourseForm from '../components/ManualCourseForm.jsx';
import StepLayout from '../components/StepLayout.jsx';
import ui from '../components/ui.module.css';
import { t } from '../i18n/index.js';
import { useAsync } from '../lib/useAsync.js';
import { useSchedule } from '../state/ScheduleContext.jsx';

export default function CoursesStep() {
  const { state, dispatch } = useSchedule();
  const [code, setCode] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = async (courseCode) => {
    const normalized = courseCode.trim().toUpperCase();
    if (!normalized) return;
    if (state.courses.some((c) => c.code === normalized)) {
      setError({ code: 'ALREADY' });
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const result = await api.sections(state.school.id, state.term.id, normalized);
      dispatch({ type: 'addCourse', course: { ...result.course, sections: result.sections } });
      setCode('');
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  // After a term change, the published sections of the kept courses are loaded again.
  useEffect(() => {
    const stale = state.courses.filter((c) => !c.manual && c.sections == null);
    stale.forEach((course) => {
      api.sections(state.school.id, state.term.id, course.code).then(
        (result) => dispatch({ type: 'setCourseSections', code: course.code, course: result.course, sections: result.sections }),
        () => dispatch({ type: 'setCourseSections', code: course.code, course: {}, sections: [] }),
      );
    });
  }, [state.courses, state.school.id, state.term.id, dispatch]);

  return (
    <StepLayout
      title={t('courses.title')}
      onBack={() => dispatch({ type: 'back' })}
      onNext={() => dispatch({ type: 'next' })}
      nextDisabled={state.courses.length === 0}
      nextHint={t('courses.empty')}
    >
      <div className={ui.stack}>
        {state.manual ? (
          <ManualCourseForm onSubmit={(section, title) => dispatch({ type: 'addManualCourse', section, title })} />
        ) : (
          <>
            <form
              className={ui.row}
              onSubmit={(e) => {
                e.preventDefault();
                load(code);
              }}
            >
              <div className={ui.field}>
                <label htmlFor="course-code">{t('courses.byCode')}</label>
                <input id="course-code" type="text" value={code} placeholder={t('courses.byCodePlaceholder')}
                  autoCapitalize="characters" onChange={(e) => setCode(e.target.value)} />
              </div>
              <button type="submit" className={ui.button} disabled={busy || !code.trim()}>
                {busy ? t('common.loading') : t('courses.addByCode')}
              </button>
            </form>
            {error?.code === 'ALREADY' ? (
              <p className={ui.error} role="alert">{t('courses.alreadyAdded')}</p>
            ) : (
              <ErrorMessage error={error} />
            )}
          </>
        )}

        <AddedCourses />

        {!state.manual && state.program && <ProgramCourses onAdd={(c) => load(c.code)} busy={busy} />}
      </div>
    </StepLayout>
  );
}

function AddedCourses() {
  const { state, dispatch } = useSchedule();
  return (
    <div>
      <h3>{t('courses.added', { count: state.courses.length })}</h3>
      {state.courses.length === 0 ? (
        <p className={ui.hint}>{t('courses.empty')}</p>
      ) : (
        <ul className={ui.list}>
          {state.courses.map((c) => (
            <li key={c.code} className={`${ui.card} ${ui.row}`}>
              <span style={{ flex: 1 }}>
                <strong>{c.code}</strong> {c.title}
                <br />
                <span className={ui.hint}>
                  {c.sections == null
                    ? t('common.loading')
                    : c.sections.length === 0
                      ? t('courses.noSections')
                      : t('courses.sectionsCount', { count: c.sections.length })}
                  {c.credits ? ` · ${t('courses.credits', { count: c.credits })}` : ''}
                </span>
              </span>
              <button type="button" className={ui.link} onClick={() => dispatch({ type: 'removeCourse', code: c.code })}
                aria-label={`${t('common.remove')} ${c.code}`}>
                {t('common.remove')}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function ProgramCourses({ onAdd, busy }) {
  const { state } = useSchedule();
  const [filter, setFilter] = useState('');
  const courses = useAsync(() => api.programCourses(state.school.id, state.program.code), [state.school.id, state.program.code]);
  const f = filter.trim().toLowerCase();
  const visible = (courses.data ?? []).filter(
    (c) => !f || c.code.toLowerCase().includes(f) || c.title.toLowerCase().includes(f),
  );
  return (
    <div className={ui.stack}>
      <h3>{t('courses.fromProgram')}</h3>
      {courses.loading && <p>{t('common.loading')}</p>}
      <ErrorMessage error={courses.error} />
      {courses.data && (
        <>
          <div className={ui.field}>
            <label htmlFor="program-filter">{t('courses.filterProgram')}</label>
            <input id="program-filter" type="search" value={filter} onChange={(e) => setFilter(e.target.value)} />
          </div>
          <ul className={ui.list}>
            {visible.map((c) => {
              const added = state.courses.some((x) => x.code === c.code);
              return (
                <li key={c.code} className={ui.row}>
                  <span style={{ flex: 1 }}>
                    <strong>{c.code}</strong> {c.title}
                  </span>
                  <button type="button" className={ui.secondary} disabled={added || busy} onClick={() => onAdd(c)}
                    aria-label={`${t('common.add')} ${c.code}`}>
                    {added ? '✓' : t('common.add')}
                  </button>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </div>
  );
}
