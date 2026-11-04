import fr from './fr.js';

const dictionaries = { fr };
let current = dictionaries.fr;

/** Selects the dictionary; only French exists for now. */
export function setLanguage(lang) {
  current = dictionaries[lang] ?? dictionaries.fr;
}

/**
 * Returns the text at a dotted key, with {placeholders} replaced.
 * A missing key returns the key itself, so a gap is visible instead of crashing.
 */
export function t(key, params = {}) {
  const value = key.split('.').reduce((node, part) => (node == null ? undefined : node[part]), current);
  if (value == null) {
    return key;
  }
  if (typeof value !== 'string') {
    return value;
  }
  return value.replace(/\{(\w+)\}/g, (match, name) => (name in params ? String(params[name]) : match));
}
