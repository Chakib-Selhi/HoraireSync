import { t } from '../i18n/index.js';
import { formatTime, groupNumber } from '../lib/format.js';
import { visibleDays } from '../lib/schedule.js';
import styles from './WeekView.module.css';

const DEFAULT_START = 8 * 60;
const DEFAULT_END = 22 * 60;
const COLORS = 6;

const toHHMM = (minutes) => `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;

/**
 * Weekly grid: one column per day, blocks placed by their start and end minutes. Each course has its own shade of
 * blue (by its position in `courseOrder`); `ghost` blocks are the group being previewed, drawn with a dashed border.
 * Each block is also a readable text, so the view stays usable with a screen reader.
 */
export default function WeekView({ blocks, courseOrder = [], height = 620 }) {
  const days = visibleDays(blocks);
  const start = Math.min(DEFAULT_START, ...blocks.map((b) => Math.floor(b.start / 60) * 60));
  const end = Math.max(DEFAULT_END, ...blocks.map((b) => Math.ceil(b.end / 60) * 60));
  const hours = [];
  for (let m = start; m <= end; m += 60) hours.push(m);
  const span = end - start;
  const pos = (m) => `${((m - start) / span) * 100}%`;
  const color = (code) => {
    const i = courseOrder.indexOf(code);
    return `c${((i < 0 ? 0 : i) % COLORS) + 1}`;
  };

  return (
    <div className={styles.scroller}>
      <div className={styles.grid} style={{ '--days': days.length, '--height': `${height}px` }}>
        <div className={styles.corner} aria-hidden="true" />
        {days.map((day) => (
          <div key={day} className={styles.dayHeader}>
            <span className={styles.full}>{t(`days.${day}`)}</span>
            <abbr className={styles.short} title={t(`days.${day}`)}>{t(`daysShort.${day}`)}</abbr>
          </div>
        ))}
        <div className={styles.hours} aria-hidden="true">
          {hours.filter((m) => (m - start) % 120 === 0).map((m) => (
            <span key={m} style={{ top: pos(m) }}>
              {Math.floor(m / 60)} h
            </span>
          ))}
        </div>
        {days.map((day) => (
          <ul key={day} className={styles.column} aria-label={t(`days.${day}`)}>
            {hours.map((m) => (
              <li key={m} aria-hidden="true" className={(m - start) % 120 === 0 ? styles.line : styles.lineMinor}
                style={{ top: pos(m) }} />
            ))}
            {blocks
              .filter((b) => b.day === day)
              .map((b) => (
                <li
                  key={b.key}
                  className={[styles.block, styles[color(b.courseCode)], b.ghost ? styles.ghost : '', b.conflict ? styles.conflict : '']
                    .filter(Boolean).join(' ')}
                  style={{
                    top: pos(b.start),
                    height: `calc(${((b.end - b.start) / span) * 100}% - 2px)`,
                    left: `calc(${(b.lane / b.lanes) * 100}% + 2px)`,
                    width: `calc(${100 / b.lanes}% - 4px)`,
                  }}
                >
                  <strong>{b.courseCode}</strong>
                  <span className={styles.detail}>
                    {t(`meetingTypes.${b.type ?? 'OTHER'}`)}
                    {b.sectionNumber ? `, ${t('sections.group', { n: groupNumber(b.sectionNumber) }).toLowerCase()}` : ''}
                  </span>
                  <span>
                    {t('week.range', { start: formatTime(toHHMM(b.start)), end: formatTime(toHHMM(b.end)) })}
                  </span>
                  {b.room && <span className={styles.detail}>{b.room}</span>}
                  {b.ghost && <span className="visually-hidden">{t('week.preview')}</span>}
                  {b.conflict && <span className={styles.conflictLabel}>{t('week.conflict')}</span>}
                </li>
              ))}
          </ul>
        ))}
      </div>
    </div>
  );
}
