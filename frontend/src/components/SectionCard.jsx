import { t } from '../i18n/index.js';
import { dayLabel, formatDate, formatTime, groupNumber } from '../lib/format.js';
import ui from './ui.module.css';
import styles from './SectionCard.module.css';

/**
 * One section, kept short: group, schedule, compatibility and conflicts. Instructor, remarks, exams and the detail
 * of each preference are in a collapsed "Détails" block. A section without published schedule cannot be chosen,
 * since it could not be exported.
 */
/**
 * Conflicts and preferences are reported separately: a group can match every preference and still overlap a
 * course already chosen (its conflicts are listed under the schedule). Without any preference set, no percentage
 * is shown.
 */
function ScoreBadge({ score }) {
  const mismatch = score.criteria.some((c) => c.outcome === 'MISMATCH');
  const percent = score.criteria.length > 0 ? ` · ${t('sections.score', { score: score.score })}` : '';
  if (score.conflicts.length > 0) {
    // the conflicting courses are listed below the schedule; only the preference result goes here
    return score.criteria.length > 0 ? <span className={mismatch ? ui.badgeDanger : ui.badgeSuccess}>
      {t(mismatch ? 'sections.incompatible' : 'sections.matchesPreferences')}{percent}</span> : null;
  }
  if (mismatch) {
    return <span className={ui.badgeDanger}>{t('sections.incompatible')}{percent}</span>;
  }
  return <span className={ui.badgeSuccess}>{t('sections.compatible')}{percent}</span>;
}

export default function SectionCard({ section, score, chosen, manual, onChoose, onUnchoose }) {
  const weekly = section.meetings.filter((m) => !m.onlyDate);
  const exams = section.meetings.filter((m) => m.onlyDate);
  const title = section.sectionNumber ? t('sections.group', { n: groupNumber(section.sectionNumber) }) : section.courseCode;
  const conflicts = [...new Set((score?.conflicts ?? []).map((c) => `${c.second.courseCode}|${c.second.sectionNumber}`))];
  const hasDetails = section.instructor || exams.length > 0 || section.notes?.length > 0 || score?.criteria?.length > 0;

  return (
    <article className={`${ui.card} ${chosen ? styles.chosen : ''}`} aria-label={title}>
      <header className={styles.header}>
        <h4 className={styles.title}>{title}</h4>
        {score && <ScoreBadge score={score} />}
      </header>

      {weekly.length === 0 ? (
        <p><span className={ui.badge}>{t('sections.noMeetings')}</span></p>
      ) : (
        <ul className={styles.meetings}>
          {weekly.map((m, i) => (
            <li key={i}>
              <strong>{dayLabel(m.day)}</strong> {m.start ? `${formatTime(m.start)} - ${formatTime(m.end)}` : ''} ·{' '}
              {t(`meetingTypes.${m.type ?? 'OTHER'}`)}
              {section.mode === 'ONLINE' ? ` · ${t('modes.ONLINE')}` : m.campus || m.room ? ` · ${[m.room, m.campus].filter(Boolean).join(', ')}` : ''}
            </li>
          ))}
        </ul>
      )}

      {conflicts.length > 0 && (
        <ul className={styles.criteria}>
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

      {hasDetails && (
        <details className={styles.details}>
          <summary>{t('sections.details')}</summary>
          {section.instructor && <p>{t('fields.instructor')} : {section.instructor}</p>}
          {section.mode && section.mode !== 'UNKNOWN' && <p>{t('fields.mode')} : {t(`modes.${section.mode}`)}</p>}
          {exams.length > 0 && (
            <p>
              {t('sections.exams')} : {exams.map((m) => `${formatDate(m.onlyDate)} ${formatTime(m.start)}`).join(' ; ')}
            </p>
          )}
          {score?.criteria?.length > 0 && (
            <ul className={styles.criteria}>
              {score.criteria.map((c) => (
                <li key={c.criterion} className={c.outcome === 'MISMATCH' ? ui.badgeDanger : c.outcome === 'MATCH' ? ui.badgeSuccess : ui.badge}>
                  {t(`sections.criteria.${c.criterion}`)} : {t(`sections.outcome.${c.outcome}`)}
                </li>
              ))}
            </ul>
          )}
          {section.notes?.length > 0 && (
            <ul>
              {section.notes.map((n, i) => (
                <li key={i}>{n}</li>
              ))}
            </ul>
          )}
        </details>
      )}

      {!manual && (
        <div className={styles.actions}>
          {chosen ? (
            <>
              <span className={ui.badgeSuccess}>{t('sections.chosen')}</span>
              <button type="button" className={ui.link} onClick={onUnchoose}>
                {t('sections.unselect')}
              </button>
            </>
          ) : weekly.length === 0 ? null : (
            <button type="button" className={ui.secondary} onClick={onChoose}>
              {t('sections.choose')}
            </button>
          )}
        </div>
      )}
      {manual && <p><span className={ui.badgeSuccess}>{t('courses.manual')}</span></p>}
    </article>
  );
}
