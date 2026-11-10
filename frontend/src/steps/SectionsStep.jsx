import { useState } from 'react';
import { api } from '../api/client.js';
import ErrorMessage from '../components/ErrorMessage.jsx';
import SectionCard from '../components/SectionCard.jsx';
import StepLayout from '../components/StepLayout.jsx';
import WeekSummary from '../components/WeekSummary.jsx';
import WeekView from '../components/WeekView.jsx';
import ui from '../components/ui.module.css';
import { t } from '../i18n/index.js';
import { formatDayMonth } from '../lib/format.js';
import { termBounds, weekLayout } from '../lib/schedule.js';
import { useAsync } from '../lib/useAsync.js';
import { orderedSelections } from '../state/reducer.js';
import { useSchedule } from '../state/ScheduleContext.jsx';
import styles from './SectionsStep.module.css';

/**
 * Courses and their groups on the left, the week being built on the right. Pointing at a group draws it with a
 * dashed border in the week, in place of the group chosen for that course, before the user commits to it.
 */
export default function SectionsStep() {
  const { state, dispatch } = useSchedule();
  const [preview, setPreview] = useState(null);
  const allChosen = state.courses.every((c) => state.selections[c.code]);

  const chosen = orderedSelections(state);
  const previewing = preview && state.selections[preview.courseCode]?.section !== preview ? preview : null;
  const shown = previewing
    ? [...chosen.filter((s) => s.section.courseCode !== previewing.courseCode), { section: previewing }]
    : chosen;
  const blocks = weekLayout(shown).map((b) =>
    previewing && b.courseCode === previewing.courseCode ? { ...b, ghost: true } : b,
  );
  const courseOrder = state.courses.map((c) => c.code);
  const bounds = termBounds(state.term, state.courses);

  return (
    <StepLayout
      title={t('sections.title')}
      hint={t('sections.hint')}
      onBack={() => dispatch({ type: 'back' })}
      onNext={() => dispatch({ type: 'next' })}
      nextDisabled={!allChosen}
      nextHint={t('sections.mustChooseAll')}
    >
      <div className={styles.layout}>
        <div className={styles.courses}>
          {state.courses.map((course, i) => (
            <CourseSections key={course.code} course={course} colorIndex={i} onPreview={setPreview} />
          ))}
        </div>
        <aside className={styles.week} aria-labelledby="week-title">
          <h3 id="week-title" className={styles.weekTitle}>{state.term?.label ?? t('sections.week')}</h3>
          <p className={styles.weekRange}>
            {bounds.start && bounds.end
              ? t('sections.weekRange', { start: formatDayMonth(bounds.start), end: formatDayMonth(bounds.end) })
              : t('sections.week')}
          </p>
          <WeekView blocks={blocks} courseOrder={courseOrder} height={520} />
          <WeekSummary blocks={blocks} />
        </aside>
      </div>
    </StepLayout>
  );
}

function CourseSections({ course, colorIndex, onPreview }) {
  const { state, dispatch } = useSchedule();
  const chosen = state.selections[course.code];
  const others = orderedSelections(state).filter((s) => s.section.courseCode !== course.code);
  const sections = course.sections ?? [];
  // Ranking is re-run when preferences or the choices for other courses change.
  const ranking = useAsync(
    () => api.rank({ sections, preferences: state.preferences, chosen: others }),
    [sections, state.preferences, JSON.stringify(others)],
    { enabled: sections.length > 0 && !course.manual },
  );
  const scoreOf = (s) => ranking.data?.find((r) => r.sectionNumber === s.sectionNumber);
  const ordered = ranking.data
    ? ranking.data.map((r) => sections.find((s) => s.sectionNumber === r.sectionNumber)).filter(Boolean)
    : sections;
  const remove = () => dispatch({ type: 'removeCourse', code: course.code });

  return (
    <section className={styles.course} aria-labelledby={`course-${course.code}`}>
      <header className={styles.courseHeader}>
        <h3 id={`course-${course.code}`} className={styles.courseTitle}>
          <i className={styles.swatch} style={{ background: `var(--course-${(colorIndex % 6) + 1}-bg)` }} aria-hidden="true" />
          {course.code} <span>{course.title}</span>
        </h3>
        {chosen && !course.manual && (
          <button type="button" className={ui.link} onClick={() => dispatch({ type: 'unchooseSection', code: course.code })}>
            {t('sections.unselect')}
          </button>
        )}
      </header>
      {course.status === 'loading' && <p className={ui.hint}>{t('courses.loading')}</p>}
      {course.status === 'error' && <ErrorMessage error={course.error} />}
      {course.status === 'ready' && sections.length === 0 && <p className={ui.badge}>{t('sections.noSections')}</p>}
      {course.manual ? (
        <SectionCard section={chosen.section} chosen manual />
      ) : (
        <div role="radiogroup" aria-labelledby={`course-${course.code}`}>
          {ordered.map((section) => (
            <SectionCard
              key={section.sectionNumber}
              section={section}
              score={scoreOf(section)}
              chosen={chosen?.section.sectionNumber === section.sectionNumber}
              onChoose={() => dispatch({ type: 'chooseSection', section })}
              onPreview={onPreview}
            />
          ))}
        </div>
      )}
      {(course.status === 'error' || (course.status === 'ready' && sections.length === 0)) && (
        <button type="button" className={ui.link} onClick={remove}>
          {t('sections.removeCourse')}
        </button>
      )}
    </section>
  );
}
