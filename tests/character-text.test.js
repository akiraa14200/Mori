'use strict';
const assert = require('node:assert/strict');
const { cleanCharacterDescription } = require('../character-text.js');

const cases = [
  { name: 'removes metadata prefix and preserves prose', input: 'Class: SSS Rank: National Level Relatives: Jinah Sung Jinwoo is a hunter who protects humanity.', expected: 'Sung Jinwoo is a hunter who protects humanity.' },
  { name: 'removes metadata-only lines', input: 'Class: SSS\nRelatives: Jinah\nSung Jinwoo is known as the Shadow Monarch.', expected: 'Sung Jinwoo is known as the Shadow Monarch.' },
  { name: 'keeps ordinary prose and strips Markdown links', input: 'She is a [skilled hunter](https://example.com) who fights monsters.', expected: 'She is a skilled hunter who fights monsters.' },
  { name: 'converts basic HTML and entities', input: '<p>He is a &amp; capable fighter.</p><p>He protects his friends.</p>', expected: 'He is a & capable fighter. He protects his friends.' },
  { name: 'does not drop ordinary descriptions', input: 'A quiet character with a mysterious past.', expected: 'A quiet character with a mysterious past.' },
  { name: 'handles empty values', input: '', expected: '' }
];
for (const item of cases) assert.equal(cleanCharacterDescription(item.input), item.expected, item.name);
console.log('Character description tests passed:', cases.length);
