import { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import ErrorMessage from '../components/ErrorMessage.jsx';
import StepLayout from '../components/StepLayout.jsx';
import ui from '../components/ui.module.css';
import { t } from '../i18n/index.js';
import { useAsync } from '../lib/useAsync.js';
import { useSchedule } from '../state/ScheduleContext.jsx';

/** Waits until the user stops typing before searching. */
function useDebounced(value, delay = 300) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);
  return debounced;
}

export default function ProgramStep() {
  const { state, dispatch } = useSchedule();
  const [query, setQuery] = useState('');
  const debounced = useDebounced(query.trim());
  const results = useAsync(() => api.searchPrograms(state.school.id, debounced), [state.school.id, debounced], {
    enabled: debounced.length >= 2,
  });

  return (
    <StepLayout
      title={t('program.title')}
      hint={t('program.hint')}
      onBack={() => dispatch({ type: 'back' })}
      onNext={() => dispatch({ type: 'next' })}
      nextLabel={state.program ? t('nav.next') : t('nav.skip')}
    >
      <div className={ui.stack}>
        {state.program && (
          <p className={ui.badgeSuccess} role="status">
            {t('program.selected', { name: state.program.name, code: state.program.code })}
          </p>
        )}
        <div className={ui.field}>
          <label htmlFor="program-search">{t('program.search')}</label>
          <input id="program-search" type="search" value={query} placeholder={t('program.searchPlaceholder')}
            onChange={(e) => setQuery(e.target.value)} />
        </div>
        {results.loading && <p>{t('common.loading')}</p>}
        <ErrorMessage error={results.error} />
        {results.data && results.data.length === 0 && <p>{t('program.noResult')}</p>}
        {results.data && results.data.length > 0 && (
          <ul className={ui.list} aria-live="polite">
            {results.data.map((p) => (
              <li key={p.code} className={`${ui.card} ${ui.row}`} style={{ alignItems: 'center' }}>
                <span style={{ flex: 1 }}>
                  <strong>{p.name}</strong>
                  <br />
                  <span className={ui.hint}>
                    {[p.type, p.code].filter(Boolean).join(', ')}
                  </span>
                </span>
                <button type="button" className={ui.secondary} aria-pressed={state.program?.code === p.code}
                  onClick={() => dispatch({ type: 'selectProgram', program: p })}>
                  {t('program.choose')}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </StepLayout>
  );
}
