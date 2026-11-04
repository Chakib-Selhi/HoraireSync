import { t } from '../i18n/index.js';
import { dayLabel, formatDate, formatTime, groupNumber } from '../lib/format.js';
import { ValueOrMissing } from './MissingBadge.jsx';
import ui from './ui.module.css';
import styles from './SectionCard.module.css';

/** One section with its meetings, its score against the preferences and its conflicts. */
export default function SectionCard({ section, score, chosen, onChoose, onUnchoose }) {
  const weekly = section.meetings.filter((m) => !m.onlyDate);
  const exams = section.meetings.filter((m) => m.onlyDate);
  const compatible = score?.compatible ?? true;
  return (
    <article className={`${ui.card} ${chosen ? styles.chosen : ''}`} aria-label={t('sections.group', { n: groupNumber(section.sectionNumber) })}>
      <header className={styles.header}>
        <h4 className={styles.title}>{t('sections.group', { n: groupNumber(section.sectionNumber) })}</h4>
        {score && (
          <span className={compatible ? ui.badgeSuccess : ui.badgeDanger}>
            {compatible ? t('sections.compatible') : t('sections.incompatible')} · {t('sections.score', { score: score.score })}
          </span>
        )}
      </header>
      <dl className={styles.facts}>
        <dt>{t('fields.instructor')}</dt>
        <dd><ValueOrMissing value={section.instructor} /></dd>
        <dt>{t('fields.mode')}</dt>
        <dd>{section.mode && section.mode !== 'UNKNOWN' ? t(`modes.${section.mode}`) : <ValueOrMissing value={null} />}</dd>
      </dl>
      {weekly.length === 0 ? (
        <p><span className={ui.badge}>{t('sections.noMeetings')}</span></p>
      ) : (
        <ul className={styles.meetings}>
          {weekly.map((m, i) => (
            <li key={i}>
              <strong>{dayLabel(m.day)}</strong> {m.start ? `${formatTime(m.start)} - ${formatTime(m.end)}` : ''} ·{' '}
              {t(`meetingTypes.${m.type ?? 'OTHER'}`)}
              {section.mode !== 'ONLINE' && (
                <>
                  {' · '}
                  <ValueOrMissing value={[m.room, m.campus].filter(Boolean).join(', ') || null} />
                </>
              )}
            </li>
          ))}
        </ul>
      )}
      {exams.length > 0 && (
        <p className={ui.hint}>
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
      {score?.conflicts?.length > 0 && (
        <ul className={styles.criteria}>
          {[...new Set(score.conflicts.map((c) => `${c.second.courseCode}|${c.second.sectionNumber}`))].map((key) => {
            const [course, sectionNumber] = key.split('|');
            return (
              <li key={key} className={ui.badgeDanger}>
                {t('sections.conflictWith', { course, section: groupNumber(sectionNumber) })}
              </li>
            );
          })}
        </ul>
      )}
      {section.notes?.length > 0 && (
        <details>
          <summary>{t('sections.notes')}</summary>
          <ul>
            {section.notes.map((n, i) => (
              <li key={i}>{n}</li>
            ))}
          </ul>
        </details>
      )}
      <div className={styles.actions}>
        {chosen ? (
          <>
            <span className={ui.badgeSuccess}>{t('sections.chosen')}</span>
            <button type="button" className={ui.link} onClick={onUnchoose}>
              {t('sections.unselect')}
            </button>
          </>
        ) : (
          <button type="button" className={ui.secondary} onClick={onChoose}>
            {t('sections.choose')}
          </button>
        )}
      </div>
    </article>
  );
}
