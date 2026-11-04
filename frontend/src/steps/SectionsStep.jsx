import { api } from '../api/client.js';
import ErrorMessage from '../components/ErrorMessage.jsx';
import ManualCourseForm from '../components/ManualCourseForm.jsx';
import SectionCard from '../components/SectionCard.jsx';
import StepLayout from '../components/StepLayout.jsx';
import ui from '../components/ui.module.css';
import { t } from '../i18n/index.js';
import { useAsync } from '../lib/useAsync.js';
import { orderedSelections } from '../state/reducer.js';
import { useSchedule } from '../state/ScheduleContext.jsx';

export default function SectionsStep() {
  const { state, dispatch } = useSchedule();
  const allChosen = state.courses.every((c) => state.selections[c.code]);
  return (
    <StepLayout
      title={t('sections.title')}
      hint={t('sections.hint')}
      onBack={() => dispatch({ type: 'back' })}
      onNext={() => dispatch({ type: 'next' })}
      nextDisabled={!allChosen}
      nextHint={t('sections.mustChooseAll')}
    >
      <div className={ui.stack}>
        {state.courses.map((course) => (
          <CourseSections key={course.code} course={course} />
        ))}
      </div>
    </StepLayout>
  );
}

function CourseSections({ course }) {
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

  return (
    <section className={ui.stack} aria-labelledby={`course-${course.code}`}>
      <h3 id={`course-${course.code}`}>
        {course.code} {course.title}
      </h3>
      <ErrorMessage error={ranking.error} />
      {sections.length === 0 && !chosen && (
        <>
          <p className={ui.badge}>{t('sections.noSections')}</p>
          <ManualCourseForm
            idPrefix={`manual-${course.code}`}
            fixedCode={course.code}
            fixedTitle={course.title}
            onSubmit={(section) => dispatch({ type: 'chooseSection', section })}
          />
        </>
      )}
      {ordered.map((section) => (
        <SectionCard
          key={section.sectionNumber}
          section={chosen?.section.sectionNumber === section.sectionNumber ? chosen.section : section}
          score={scoreOf(section)}
          chosen={chosen?.section.sectionNumber === section.sectionNumber}
          onChoose={() => dispatch({ type: 'chooseSection', section })}
          onUnchoose={() => dispatch({ type: 'unchooseSection', code: course.code })}
        />
      ))}
      {sections.length === 0 && chosen && (
        <SectionCard section={chosen.section} chosen onUnchoose={() => dispatch({ type: 'unchooseSection', code: course.code })} />
      )}
    </section>
  );
}
