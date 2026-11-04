import { t } from '../i18n/index.js';
import { STEPS } from '../state/reducer.js';
import styles from './Stepper.module.css';

/** Progress list. Previous steps are buttons so the user can go back to change a choice. */
export default function Stepper({ state, onGoTo }) {
  const visible = STEPS.map((name, index) => ({ name, index }));
  const position = visible.findIndex((s) => s.index === state.step) + 1;
  return (
    <nav aria-label={t('steps.label')} className={styles.stepper}>
      <p className={styles.counter}>{t('steps.current', { n: position, total: visible.length })}</p>
      <ol className={styles.list}>
        {visible.map(({ name, index }) => {
          const current = index === state.step;
          const done = index < state.step;
          return (
            <li key={name} className={current ? styles.current : done ? styles.done : styles.todo}>
              {done ? (
                <button type="button" className={styles.item} onClick={() => onGoTo(index)}>
                  {t(`steps.${name}`)}
                </button>
              ) : (
                <span className={styles.item} aria-current={current ? 'step' : undefined}>
                  {t(`steps.${name}`)}
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
