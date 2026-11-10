import { useState } from 'react';
import { api } from '../api/client.js';
import ErrorMessage from '../components/ErrorMessage.jsx';
import { ValueOrMissing } from '../components/MissingBadge.jsx';
import StepLayout from '../components/StepLayout.jsx';
import WeekSummary from '../components/WeekSummary.jsx';
import WeekView from '../components/WeekView.jsx';
import ui from '../components/ui.module.css';
import { t } from '../i18n/index.js';
import { dayLabel, describePath, formatDate, formatTime, groupNumber, sectionLabel } from '../lib/format.js';
import { essentialProblems, oneOffMeetings, termBounds, weekLayout } from '../lib/schedule.js';
import { useAsync } from '../lib/useAsync.js';
import { orderedSelections } from '../state/reducer.js';
import { useSchedule } from '../state/ScheduleContext.jsx';
import styles from './ReviewStep.module.css';

const label = (ref) => sectionLabel(ref.courseCode, ref.sectionNumber);

export default function ReviewStep() {
  const { state, dispatch } = useSchedule();
  const [view, setView] = useState('week');
  const selections = orderedSelections(state);
  const conflicts = useAsync(
    () => api.conflicts({ schoolId: state.school.id, termId: state.term?.id, selections }),
    [JSON.stringify(selections)],
  );
  const bounds = termBounds(state.term, state.courses);
  const blocking = essentialProblems(selections, { startDate: bounds.start, endDate: bounds.end });
  const blocks = weekLayout(selections, conflicts.data ?? []);
  const exams = oneOffMeetings(selections);

  return (
    <StepLayout
      title={t('review.title')}
      onBack={() => dispatch({ type: 'back' })}
      onNext={() => dispatch({ type: 'next' })}
      nextLabel={t('review.confirm')}
      nextDisabled={blocking.length > 0}
      nextHint={t('review.blocking')}
    >
      <div className={ui.stack}>
        <div className={ui.card}>
          <div className={ui.tabs} role="group" aria-label={t('review.title')}>
            {['week', 'list'].map((v) => (
              <button key={v} type="button" className={ui.tab} aria-pressed={view === v} onClick={() => setView(v)}>
                {t(`review.${v}`)}
              </button>
            ))}
          </div>
          <div className={styles.view}>
            {view === 'week'
              ? <WeekView blocks={blocks} courseOrder={state.courses.map((c) => c.code)} height={560} />
              : <ListView selections={selections} />}
          </div>
          {view === 'week' && <WeekSummary blocks={blocks} conflictCount={conflicts.data?.length} />}
        </div>

        {exams.length > 0 && (
          <div>
            <h3>{t('review.exams')}</h3>
            <ul>
              {exams.map(({ courseCode, sectionNumber, meeting }, i) => (
                <li key={i}>
                  {sectionLabel(courseCode, sectionNumber)}, {formatDate(meeting.onlyDate)}, {t('week.range', { start: formatTime(meeting.start), end: formatTime(meeting.end) })}
                </li>
              ))}
            </ul>
          </div>
        )}

        <div aria-live="polite">
          <h3>{t('review.conflicts')}</h3>
          <ErrorMessage error={conflicts.error} />
          {conflicts.data && conflicts.data.length === 0 && <p className={ui.badgeSuccess}>{t('review.noConflicts')}</p>}
          {conflicts.data && conflicts.data.length > 0 && (
            <ul>
              {conflicts.data.map((c, i) => (
                <li key={i} className={ui.badgeDanger}>
                  {c.date
                    ? t('review.conflictOn', { a: label(c.first), b: label(c.second), date: formatDate(c.date),
                        start: formatTime(c.overlapStart), end: formatTime(c.overlapEnd) })
                    : t('review.conflict', { a: label(c.first), b: label(c.second), day: dayLabel(c.day),
                        start: formatTime(c.overlapStart), end: formatTime(c.overlapEnd) })}
                </li>
              ))}
            </ul>
          )}
        </div>

        {blocking.length > 0 && (
          <div>
            <p className={ui.error} role="alert">{t('review.blocking')}</p>
            <ul>
              {blocking.map((p, i) => (
                <li key={i}>
                  {sectionLabel(p.courseCode, p.sectionNumber)} :{' '}
                  {describePath(selections.find((s) => s.section.courseCode === p.courseCode).section, p.path)}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </StepLayout>
  );
}

function ListView({ selections }) {
  return (
    <div className={ui.tableWrap}>
      <table className={ui.table}>
        <thead>
          <tr>
            <th scope="col">{t('steps.courses')}</th>
            <th scope="col">{t('manual.section')}</th>
            <th scope="col">{t('meeting.day')}</th>
            <th scope="col">{t('meeting.start')}</th>
            <th scope="col">{t('meeting.room')}</th>
            <th scope="col">{t('fields.instructor')}</th>
          </tr>
        </thead>
        <tbody>
          {selections.flatMap(({ courseTitle, section }) =>
            section.meetings
              .filter((m) => !m.onlyDate)
              .map((m, i) => (
                <tr key={`${section.courseCode}-${i}`}>
                  <td>
                    <strong>{section.courseCode}</strong> {courseTitle} ({t(`meetingTypes.${m.type ?? 'OTHER'}`).toLowerCase()})
                  </td>
                  <td>{groupNumber(section.sectionNumber)}</td>
                  <td><ValueOrMissing value={m.day ? dayLabel(m.day) : null} /></td>
                  <td><ValueOrMissing value={m.start && m.end ? t('week.range', { start: formatTime(m.start), end: formatTime(m.end) }) : null} /></td>
                  <td>
                    {section.mode === 'ONLINE' ? t('modes.ONLINE') : <ValueOrMissing value={[m.room, m.campus].filter(Boolean).join(', ') || null} />}
                  </td>
                  <td><ValueOrMissing value={section.instructor} /></td>
                </tr>
              )),
          )}
        </tbody>
      </table>
    </div>
  );
}
