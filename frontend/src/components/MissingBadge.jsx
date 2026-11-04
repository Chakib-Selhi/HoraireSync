import { t } from '../i18n/index.js';
import ui from './ui.module.css';

export default function MissingBadge() {
  return <span className={ui.badge}>{t('common.notAvailable')}</span>;
}

/** Value, or the "not available" badge when it is empty. */
export function ValueOrMissing({ value }) {
  return value ? <>{value}</> : <MissingBadge />;
}
