import { describe, expect, it } from 'vitest';
import { t } from './index.js';

describe('t', () => {
  it('reads nested keys and fills placeholders', () => {
    expect(t('steps.current', { n: 2, total: 9 })).toBe('Étape 2 sur 9');
  });

  it('returns the key when it is missing, and arrays as is', () => {
    expect(t('nope.missing')).toBe('nope.missing');
    expect(Array.isArray(t('export.appleSteps'))).toBe(true);
  });

  it('keeps unknown placeholders visible', () => {
    expect(t('steps.current', { n: 1 })).toBe('Étape 1 sur {total}');
  });
});
