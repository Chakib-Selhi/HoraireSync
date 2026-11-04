import { useState } from 'react';
import { api } from '../api/client.js';
import ErrorMessage from '../components/ErrorMessage.jsx';
import ManualCourseForm from '../components/ManualCourseForm.jsx';
import StepLayout from '../components/StepLayout.jsx';
import ui from '../components/ui.module.css';
import { t } from '../i18n/index.js';
import { useAsync } from '../lib/useAsync.js';
import { useSchedule } from '../state/ScheduleContext.jsx';
import styles from './CoursesStep.module.css';

/**
 * Three ways to add a course: from the program list, by code (any UQAM course, in or out of the program),
 * or typed by hand. Courses are added at once and their sections load in the background.
 */
export default function CoursesStep() {
  const { state, dispatch } = useSchedule();
  const [code, setCode] = useState('');
  const [notice, setNotice] = useState(null);
  const [showManual, setShowManual] = useState(false);

  const add = (rawCode, title) => {
    const normalized = rawCode.trim().toUpperCase();
    if (!normalized) return;
    if (state.courses.some((c) => c.code === normalized)) {
      setNotice(t('courses.alreadyAdded'));
      return;
    }
    setNotice(null);
    dispatch({ type: 'addCourse', code: normalized, title });
  };

  return (
    <StepLayout
      title={t('courses.title')}
      onBack={() => dispatch({ type: 'back' })}
      onNext={() => dispatch({ type: 'next' })}
      nextDisabled={state.courses.length === 0}
      nextHint={t('courses.empty')}
    >
      <div className={ui.stack}>
        <AddedCourses />

        <form
          className={`${ui.card} ${ui.stack}`}
          onSubmit={(e) => {
            e.preventDefault();
            add(code);
            setCode('');
          }}
        >
          <h3>{t('courses.otherTitle')}</h3>
          <p className={ui.hint}>{t('courses.otherHint')}</p>
          <div className={ui.row}>
            <div className={ui.field}>
              <label htmlFor="course-code">{t('courses.byCode')}</label>
              <input id="course-code" type="text" value={code} placeholder={t('courses.byCodePlaceholder')}
                autoCapitalize="characters" onChange={(e) => setCode(e.target.value)} />
            </div>
            <button type="submit" className={ui.button} disabled={!code.trim()}>
              {t('courses.addByCode')}
            </button>
          </div>
          {notice && <p className={ui.error} role="alert">{notice}</p>}
        </form>

        {showManual ? (
          <ManualCourseForm
            onSubmit={(section, title) => {
              dispatch({ type: 'addManualCourse', section, title });
              setShowManual(false);
            }}
            onCancel={() => setShowManual(false)}
          />
        ) : (
          <div>
            <button type="button" className={ui.secondary} onClick={() => setShowManual(true)}>
              {t('courses.manualOpen')}
            </button>
            <p className={ui.hint}>{t('courses.manualHint')}</p>
          </div>
        )}

        {state.program && <ProgramCourses onAdd={(c) => add(c.code, c.title)} />}
      </div>
    </StepLayout>
  );
}

function status(course) {
  if (course.manual) return t('courses.manual');
  if (course.status === 'loading') return t('courses.loading');
  if (course.status === 'error') return null;
  if (course.sections.length === 0) return t('courses.noSections');
  return t('courses.sectionsCount', { count: course.sections.length });
}

function AddedCourses() {
  const { state, dispatch } = useSchedule();
  return (
    <div>
      <h3>{t('courses.added', { count: state.courses.length })}</h3>
      {state.courses.length === 0 ? (
        <p className={ui.hint}>{t('courses.empty')}</p>
      ) : (
        <ul className={ui.list} aria-live="polite">
          {state.courses.map((c) => (
            <li key={c.code} className={`${ui.card} ${ui.row}`}>
              <span style={{ flex: 1 }}>
                <strong>{c.code}</strong> {c.title}
                <br />
                {c.status === 'error' ? (
                  <ErrorMessage error={c.error} />
                ) : (
                  <span className={ui.hint}>
                    {status(c)}
                    {c.credits ? ` · ${t('courses.credits', { count: c.credits })}` : ''}
                  </span>
                )}
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

function ProgramCourses({ onAdd }) {
  const { state } = useSchedule();
  const [filter, setFilter] = useState('');
  const courses = useAsync(() => api.programCourses(state.school.id, state.program.code),
    [state.school.id, state.program.code]);
  const f = filter.trim().toLowerCase();
  const visible = (courses.data ?? []).filter(
    (c) => !f || c.code.toLowerCase().includes(f) || c.title.toLowerCase().includes(f),
  );
  return (
    <div className={`${ui.card} ${ui.stack}`}>
      <h3>{t('courses.fromProgram', { name: state.program.name })}</h3>
      {courses.loading && <p>{t('common.loading')}</p>}
      <ErrorMessage error={courses.error} />
      {courses.data && (
        <>
          <div className={ui.field}>
            <label htmlFor="program-filter">{t('courses.filterProgram')}</label>
            <input id="program-filter" type="search" value={filter} onChange={(e) => setFilter(e.target.value)} />
          </div>
          <ul className={`${ui.list} ${styles.programList}`}>
            {visible.map((c) => {
              const added = state.courses.some((x) => x.code === c.code);
              return (
                <li key={c.code} className={ui.row}>
                  <span style={{ flex: 1 }}>
                    <strong>{c.code}</strong> {c.title}
                  </span>
                  <button type="button" className={ui.secondary} disabled={added} onClick={() => onAdd(c)}
                    aria-label={`${t('common.add')} ${c.code}`}>
                    {added ? t('courses.addedShort') : t('common.add')}
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
