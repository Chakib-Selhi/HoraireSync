import { useState } from 'react';
import { api } from '../api/client.js';
import ErrorMessage from '../components/ErrorMessage.jsx';
import StepLayout from '../components/StepLayout.jsx';
import ui from '../components/ui.module.css';
import { t } from '../i18n/index.js';
import { formatDate } from '../lib/format.js';
import { exportPayload } from '../lib/schedule.js';
import { orderedSelections } from '../state/reducer.js';
import { useSchedule } from '../state/ScheduleContext.jsx';

/** Saves a Blob as a file through a temporary link. */
export function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export default function ExportStep() {
  const { state, dispatch } = useSchedule();
  const [status, setStatus] = useState({ busy: false, error: null, filename: null });
  const readingWeek = state.term?.readingWeekDates ?? [];

  const download = async () => {
    setStatus({ busy: true, error: null, filename: null });
    try {
      const payload = exportPayload({ ...state, selections: orderedSelections(state) });
      const { blob, filename } = await api.exportIcs(payload);
      saveBlob(blob, filename);
      setStatus({ busy: false, error: null, filename });
    } catch (error) {
      setStatus({ busy: false, error, filename: null });
    }
  };

  return (
    <StepLayout title={t('export.title')} onBack={() => dispatch({ type: 'back' })}>
      <div className={ui.stack}>
        {readingWeek.length > 0 && (
          <div>
            <label className={ui.choice}>
              <input type="checkbox" checked={state.excludeReadingWeek}
                onChange={(e) => dispatch({ type: 'setExcludeReadingWeek', value: e.target.checked })} />
              {t('export.excludeReadingWeek', {
                from: formatDate(readingWeek[0]),
                to: formatDate(readingWeek[readingWeek.length - 1]),
              })}
            </label>
            {state.term.readingWeekNote && (
              <p className={ui.hint}>{t('export.excludeReadingWeekNote', { note: state.term.readingWeekNote })}</p>
            )}
          </div>
        )}
        <button type="button" className={ui.button} onClick={download} disabled={status.busy}>
          {status.busy ? t('export.downloading') : t('export.download')}
        </button>
        <div aria-live="polite">
          {status.filename && <p className={ui.badgeSuccess}>{t('export.done', { name: status.filename })}</p>}
          <ErrorMessage error={status.error} />
        </div>

        <h3 style={{ marginTop: 32, marginBottom: 0 }}>{t('export.howTo')}</h3>
        <p className={ui.hint}>{t('export.tip')}</p>
        <Instructions title={t('export.apple')} steps={t('export.appleSteps')} extra={t('export.appleIos')} />
        <Instructions title={t('export.google')} steps={t('export.googleSteps')} />
        <Instructions title={t('export.outlook')} steps={t('export.outlookSteps')} />
      </div>
    </StepLayout>
  );
}

function Instructions({ title, steps, extra }) {
  return (
    <details className={ui.card}>
      <summary>
        <strong>{title}</strong>
      </summary>
      <ol>
        {steps.map((s) => (
          <li key={s}>{s}</li>
        ))}
      </ol>
      {extra && <p className={ui.hint}>{extra}</p>}
    </details>
  );
}
