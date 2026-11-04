import { useEffect, useRef } from 'react';
import { t } from '../i18n/index.js';
import ui from './ui.module.css';
import styles from './StepLayout.module.css';

/**
 * Common frame of a step: heading (focused when the step opens, for keyboard and screen reader users),
 * optional hint, content and the back / next buttons.
 */
export default function StepLayout({ title, hint, children, onBack, onNext, nextLabel, nextDisabled, nextHint }) {
  const heading = useRef(null);
  useEffect(() => {
    heading.current?.focus();
  }, []);
  return (
    <section className={styles.step} aria-labelledby="step-title">
      <h2 id="step-title" ref={heading} tabIndex={-1} className={styles.title}>
        {title}
      </h2>
      {hint && <p className={ui.hint}>{hint}</p>}
      <div className={styles.content}>{children}</div>
      <div className={styles.nav}>
        {onBack && (
          <button type="button" className={ui.secondary} onClick={onBack}>
            {t('nav.back')}
          </button>
        )}
        {onNext && (
          <button type="button" className={ui.button} onClick={onNext} disabled={nextDisabled}
            aria-describedby={nextDisabled && nextHint ? 'next-hint' : undefined}>
            {nextLabel ?? t('nav.next')}
          </button>
        )}
      </div>
      {nextDisabled && nextHint && (
        <p id="next-hint" className={ui.hint}>
          {nextHint}
        </p>
      )}
    </section>
  );
}
