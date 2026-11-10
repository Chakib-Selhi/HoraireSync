import { t } from '../i18n/index.js';
import { dayLabel, formatDate, formatTime, groupNumber } from '../lib/format.js';
import ui from './ui.module.css';
import styles from './SectionCard.module.css';

/**
 * Status of a group: preferences first, then the compatibility percentage when preferences are set.
 * Conflicts with courses already chosen are listed separately, under the schedule.
 */
function Score({ score }) {
  const mismatch = score.criteria.some((c) => c.outcome === 'MISMATCH');
  const percent = score.criteria.length > 0 ? ` ${t('sections.score', { score: score.score })}` : '';
  if (score.conflicts.length > 0 && score.criteria.length === 0) return null;
  const label = mismatch
    ? t('sections.incompatible')
    : score.criteria.length > 0
      ? t('sections.matchesPreferences')
      : t('sections.compatible');
  return <span className={mismatch ? ui.badgeDanger : ui.badgeSuccess}>{label}{percent}</span>;
}

/** "Lundi, 18 h 00 à 21 h 00 (laboratoire)": the type is only named when it is not a lecture. */
function meetingText(m) {
  const base = t('sections.meeting', { day: dayLabel(m.day), start: formatTime(m.start), end: formatTime(m.end) });
  return m.type && m.type !== 'LECTURE' ? `${base} (${t(`meetingTypes.${m.type}`).toLowerCase()})` : base;
}

/**
 * One group of a course, as a radio option. Pointing at it (mouse or keyboard focus) previews it in the week view.
 * Instructor, remarks, exams and the detail of each preference stay in a collapsed "Détails" block.
 * A group without a published schedule cannot be chosen, since it could not be exported.
 */
export default function SectionCard({ section, score, chosen, manual, onChoose, onPreview }) {
  const weekly = section.meetings.filter((m) => !m.onlyDate);
  const exams = section.meetings.filter((m) => m.onlyDate);
  const n = groupNumber(section.sectionNumber);
  const title = section.sectionNumber ? t('sections.group', { n }) : section.courseCode;
  const place = section.mode === 'ONLINE'
    ? t('modes.ONLINE')
    : [...new Set(weekly.map((m) => m.campus ?? section.campus).filter(Boolean))].join(', ');
  const conflicts = [...new Set((score?.conflicts ?? []).map((c) => `${c.second.courseCode}|${c.second.sectionNumber}`))];
  const hasDetails = section.instructor || exams.length > 0 || section.notes?.length > 0 || score?.criteria?.length > 0
    || weekly.some((m) => m.room);
  const preview = onPreview && weekly.length > 0 ? () => onPreview(section) : undefined;
  const leave = onPreview ? () => onPreview(null) : undefined;

  return (
    <article
      className={`${styles.option} ${chosen ? styles.chosen : ''}`}
      aria-label={title}
      onMouseEnter={preview}
      onMouseLeave={leave}
      onFocus={preview}
      onBlur={leave}
    >
      <div className={styles.main}>
        {!manual && (
          <input
            type="radio"
            className={styles.radio}
            name={`group-${section.courseCode}`}
            checked={!!chosen}
            disabled={weekly.length === 0}
            onChange={onChoose}
            aria-label={t('sections.chooseGroup', { n: n || section.courseCode })}
          />
        )}
        <div className={styles.body}>
          <div className={styles.titleRow}>
            <h4 className={styles.title}>{title}</h4>
            {place && <span className={styles.place}>{place}</span>}
          </div>
          {weekly.length === 0 ? (
            <p className={`${ui.badge} ${styles.line}`}>{t('sections.noMeetings')}</p>
          ) : (
            <ul className={styles.meetings}>
              {weekly.map((m, i) => (
                <li key={i}>{meetingText(m)}</li>
              ))}
            </ul>
          )}
          {(score || manual) && (
            <p className={styles.line}>
              {manual ? <span className={ui.badgeSuccess}>{t('courses.manual')}</span> : <Score score={score} />}
            </p>
          )}
          {conflicts.length > 0 && (
            <ul className={styles.conflicts}>
              {conflicts.map((key) => {
                const [course, sectionNumber] = key.split('|');
                return (
                  <li key={key} className={ui.badgeDanger}>
                    {t('sections.conflictWith', { course, section: groupNumber(sectionNumber) })}
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>

      {hasDetails && (
        <details className={styles.details}>
          <summary>{t('sections.details')}</summary>
          {section.instructor && <p>{t('fields.instructor')} : {section.instructor}</p>}
          {section.mode && section.mode !== 'UNKNOWN' && <p>{t('fields.mode')} : {t(`modes.${section.mode}`)}</p>}
          {weekly.some((m) => m.room) && (
            <p>{t('fields.room')} : {[...new Set(weekly.map((m) => m.room).filter(Boolean))].join(', ')}</p>
          )}
          {exams.length > 0 && (
            <p>
              {t('sections.exams')} : {exams.map((m) => `${formatDate(m.onlyDate)} ${formatTime(m.start)}`).join(' ; ')}
            </p>
          )}
          {score?.criteria?.length > 0 && (
            <ul className={styles.criteria}>
              {score.criteria.map((c) => (
                <li key={c.criterion}>
                  {t(`sections.criteria.${c.criterion}`)} :{' '}
                  <span className={c.outcome === 'MISMATCH' ? ui.badgeDanger : c.outcome === 'MATCH' ? ui.badgeSuccess : ui.badge}>
                    {t(`sections.outcome.${c.outcome}`)}
                  </span>
                </li>
              ))}
            </ul>
          )}
          {section.notes?.length > 0 && (
            <ul>
              {section.notes.map((note, i) => (
                <li key={i}>{note}</li>
              ))}
            </ul>
          )}
        </details>
      )}
    </article>
  );
}
