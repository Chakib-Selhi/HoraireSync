import { t } from '../i18n/index.js';
import ui from './ui.module.css';

/** Translated error for an ApiError (or any error), announced to screen readers. */
export default function ErrorMessage({ error }) {
  if (!error) return null;
  const code = error.code ?? 'UNKNOWN';
  const text =
    code === 'INVALID_SCHEDULE'
      ? t('errors.INVALID_SCHEDULE', { detail: error.message?.replace(/^Horaire incomplet : /, '') ?? '' })
      : t(`errors.${code}`) === `errors.${code}`
        ? t('errors.UNKNOWN')
        : t(`errors.${code}`);
  return (
    <p className={ui.error} role="alert">
      {text}
    </p>
  );
}
