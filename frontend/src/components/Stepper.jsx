import { t } from '../i18n/index.js';
import { STEPS } from '../state/reducer.js';
import styles from './Stepper.module.css';

/**
 * Steps as text tabs in the header. Previous steps are buttons so the user can go back to change a choice.
 * On a phone only "Étape n sur 7, Nom" is shown.
 */
export default function Stepper({ state, onGoTo }) {
  const current = STEPS[state.step];
  return (
    <nav aria-label={t('steps.label')} className={styles.stepper}>
      <p className={styles.counter}>
        {t('steps.current', { n: state.step + 1, total: STEPS.length })}, {t(`steps.${current}`).toLowerCase()}
      </p>
      <ol className={styles.list}>
        {STEPS.map((name, index) => {
          const isCurrent = index === state.step;
          const done = index < state.step;
          return (
            <li key={name}>
              {done ? (
                <button type="button" className={`${styles.item} ${styles.done}`} onClick={() => onGoTo(index)}>
                  {t(`steps.${name}`)}
                </button>
              ) : (
                <span className={`${styles.item} ${isCurrent ? styles.current : styles.todo}`}
                  aria-current={isCurrent ? 'step' : undefined}>
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
