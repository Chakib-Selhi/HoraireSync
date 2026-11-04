import { useEffect } from 'react';
import { api } from '../api/client.js';
import ErrorMessage from '../components/ErrorMessage.jsx';
import StepLayout from '../components/StepLayout.jsx';
import ui from '../components/ui.module.css';
import { t } from '../i18n/index.js';
import { useAsync } from '../lib/useAsync.js';
import { useSchedule } from '../state/ScheduleContext.jsx';

/** The backend only offers the upcoming term, the only one whose sections are published. */
export default function TermStep() {
  const { state, dispatch } = useSchedule();
  const terms = useAsync(() => api.terms(state.school.id), [state.school.id]);

  // A single term available: select it, the user only confirms.
  useEffect(() => {
    if (!state.term && terms.data?.length === 1) {
      dispatch({ type: 'selectTerm', term: terms.data[0] });
    }
  }, [terms.data, state.term, dispatch]);

  return (
    <StepLayout
      title={t('term.title')}
      hint={t('term.hint')}
      onNext={() => dispatch({ type: 'next' })}
      nextDisabled={!state.term}
    >
      {terms.loading && <p>{t('common.loading')}</p>}
      <ErrorMessage error={terms.error} />
      {terms.data && terms.data.length === 0 && <p className={ui.error}>{t('term.none')}</p>}
      {terms.data && terms.data.length > 0 && (
        <div className={ui.field}>
          <label htmlFor="term">{t('term.select')}</label>
          <select
            id="term"
            value={state.term?.id ?? ''}
            onChange={(e) => {
              const chosen = terms.data.find((x) => x.id === e.target.value);
              if (chosen) dispatch({ type: 'selectTerm', term: chosen });
            }}
          >
            {!state.term && <option value="">-</option>}
            {terms.data.map((x) => (
              <option key={x.id} value={x.id}>
                {x.label}
              </option>
            ))}
          </select>
        </div>
      )}
    </StepLayout>
  );
}
