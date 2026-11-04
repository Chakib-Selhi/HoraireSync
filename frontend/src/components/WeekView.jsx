import { t } from '../i18n/index.js';
import { formatTime, groupNumber } from '../lib/format.js';
import { visibleDays } from '../lib/schedule.js';
import styles from './WeekView.module.css';

const DEFAULT_START = 8 * 60;
const DEFAULT_END = 22 * 60;

const toHHMM = (minutes) => `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;

/**
 * Weekly grid built with CSS grid: one column per day, blocks placed by their start and end minutes.
 * Each block is also a readable text, so the view stays usable with a screen reader.
 */
export default function WeekView({ blocks }) {
  const days = visibleDays(blocks);
  const start = Math.min(DEFAULT_START, ...blocks.map((b) => Math.floor(b.start / 60) * 60));
  const end = Math.max(DEFAULT_END, ...blocks.map((b) => Math.ceil(b.end / 60) * 60));
  const hours = [];
  for (let m = start; m < end; m += 60) hours.push(m);
  const span = end - start;

  return (
    <div className={styles.scroller}>
      <div className={styles.grid} style={{ '--days': days.length }}>
        <div className={styles.corner} aria-hidden="true" />
        {days.map((day) => (
          <div key={day} className={styles.dayHeader}>
            <abbr title={t(`days.${day}`)}>{t(`daysShort.${day}`)}</abbr>
          </div>
        ))}
        <div className={styles.hours} aria-hidden="true">
          {hours.map((m) => (
            <span key={m} style={{ top: `${((m - start) / span) * 100}%` }}>
              {formatTime(toHHMM(m))}
            </span>
          ))}
        </div>
        {days.map((day) => (
          <ul key={day} className={styles.column} aria-label={t(`days.${day}`)}>
            {hours.map((m) => (
              <li key={m} aria-hidden="true" className={styles.line} style={{ top: `${((m - start) / span) * 100}%` }} />
            ))}
            {blocks
              .filter((b) => b.day === day)
              .map((b) => (
                <li
                  key={b.key}
                  className={b.conflict ? styles.blockConflict : styles.block}
                  style={{
                    top: `${((b.start - start) / span) * 100}%`,
                    height: `${((b.end - b.start) / span) * 100}%`,
                    left: `${(b.lane / b.lanes) * 100}%`,
                    width: `${100 / b.lanes}%`,
                  }}
                >
                  <strong>{b.courseCode}</strong>
                  <span>
                    {formatTime(toHHMM(b.start))} - {formatTime(toHHMM(b.end))}
                  </span>
                  <span>
                    {t(`meetingTypes.${b.type ?? 'OTHER'}`)} · {t('sections.group', { n: groupNumber(b.sectionNumber) })}
                  </span>
                  {b.room && <span>{b.room}</span>}
                  {b.conflict && <span className={styles.conflictLabel}>{t('review.conflicts')}</span>}
                </li>
              ))}
          </ul>
        ))}
      </div>
    </div>
  );
}
