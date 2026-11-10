import { t } from '../i18n/index.js';
import { formatHours, joinDays } from '../lib/format.js';
import { weekSummary } from '../lib/schedule.js';
import styles from './WeekSummary.module.css';

/**
 * One line of figures under the week view: weekly hours, conflicts, free weekdays. Without `conflictCount`
 * (the server's count, exams included), only the overlaps of the typical week are counted and said so.
 */
export default function WeekSummary({ blocks, conflictCount }) {
  const summary = weekSummary(blocks);
  const { minutes, freeDays } = summary;
  const conflicts = conflictCount ?? summary.conflicts;
  if (minutes === 0) {
    return <p className={styles.summary}>{t('week.noCourse')}</p>;
  }
  return (
    <ul className={styles.summary} aria-live="polite">
      <li><strong>{formatHours(minutes)}</strong> {t('week.perWeek')}</li>
      <li className={conflicts > 0 ? styles.danger : undefined}>
        {conflicts > 0 ? <strong>{t('week.conflicts', { n: conflicts })}</strong> : t(conflictCount == null ? 'week.noWeeklyConflict' : 'week.noConflict')}
      </li>
      {freeDays.length > 0 && (
        <li>{t(freeDays.length === 1 ? 'week.freeDay' : 'week.freeDays', { days: joinDays(freeDays) })}</li>
      )}
    </ul>
  );
}
