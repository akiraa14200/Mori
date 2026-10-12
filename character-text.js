(function (root) {
  'use strict';
  const META_LABEL = /\b(?:Class|Relatives|Guild|Affiliation|Occupation|Hunter Rank|Rank|Blood Type|Level|Age|Height|Weight|Status|Species|Race|Gender|Nationality|Residence|Family|Parents|Siblings|Master|Teacher|Student|Team|Party|Organization|Title|Alias|Aliases|Birthday|Date of Birth)\s*:/ig;
  const PROSE_START = /(?:\b[A-Z][\w'’.-]*\s+[A-Z][\w'’.-]*(?:\s+[A-Z][\w'’.-]*){0,2}\s+(?:is known|is a|is an|was a|was an|becomes|became|has become|possesses|serves|joins|leads|fights|lives|works|uses|can|will|has|had|was|is)\b|\b(?:He|She|They|It)\s+(?:is|was|has|had|becomes|became|can|will|would|possesses|serves|joins|leads|fights|lives|works|uses)\b)/i;
  function decodeEntities(value) {
    if (typeof document !== 'undefined' && document.createElement) {
      const node = document.createElement('textarea');
      node.innerHTML = value;
      return node.value;
    }
    return value.replace(/&nbsp;/gi, ' ').replace(/&amp;/gi, '&').replace(/&quot;/gi, '"')
      .replace(/&#39;|&apos;/gi, "'").replace(/&lt;/gi, '<').replace(/&gt;/gi, '>');
  }
  function proseAfterMetadata(line) {
    let lastLabel = -1;
    META_LABEL.lastIndex = 0;
    let label;
    while ((label = META_LABEL.exec(line)) !== null) lastLabel = label.index;
    META_LABEL.lastIndex = 0;
    if (lastLabel < 0) return null;
    const matcher = new RegExp('^(?:' + PROSE_START.source + ')', 'i');
    let candidate = null, fallback = null;
    for (let index = lastLabel + 1; index < line.length; index++) {
      const actual = matcher.exec(line.slice(index));
      if (!actual) continue;
      const verb = /\b(?:is known|is a|is an|was a|was an|becomes|became|has become|possesses|serves|joins|leads|fights|lives|works|uses|can|will|has|had|was|is)\b/i.exec(actual[0]);
      const subject = verb ? actual[0].slice(0, verb.index).trim() : '';
      const found = { index };
      if (subject.split(/\s+/).filter(Boolean).length >= 2) candidate = found;
      else fallback = found;
    }
    return candidate || fallback;
  }
  function cleanCharacterDescription(raw) {
    let value = String(raw || '');
    if (!value.trim()) return '';
    value = value.replace(/<br\s*\/?\s*>/gi, '\n')
      .replace(/<\/(?:p|div|li|h[1-6]|blockquote)\s*>/gi, '\n')
      .replace(/<(?:p|div|li|h[1-6]|blockquote)\b[^>]*>/gi, '\n');
    value = value.replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
      .replace(/\[([^\]]+)\]\((?:https?:\/\/)?[^)]*\)/g, '$1')
      .replace(/<a\b[^>]*>([\s\S]*?)<\/a>/gi, '$1')
      .replace(/<[^>]*>/g, ' ');
    value = decodeEntities(value);
    value = value.replace(/^\s{0,3}#{1,6}\s*/gm, '')
      .replace(/(?:__|\*\*|~~|~!|!~|\*|_)/g, '')
      .replace(/[\x60]{1,3}/g, '')
      .replace(/\r/g, '\n');
    const lines = value.split(/\n+/).map(line => line.replace(/\s+/g, ' ').trim()).filter(Boolean);
    const proseLines = [];
    for (const line of lines) {
      META_LABEL.lastIndex = 0;
      const firstLabel = line.search(META_LABEL);
      META_LABEL.lastIndex = 0;
      if (firstLabel >= 0) {
        const prose = proseAfterMetadata(line);
        if (prose) proseLines.push(line.slice(prose.index).trim());
        continue;
      }
      proseLines.push(line);
    }
    value = proseLines.join(' ');
    value = value.replace(/\b(?:Class|Relatives|Guild|Affiliation|Occupation|Hunter Rank|Rank|Blood Type)\s*:\s*/gi, '')
      .replace(/\s+/g, ' ').trim();
    return value;
  }
  root.cleanCharacterDescription = cleanCharacterDescription;
  if (typeof module !== 'undefined' && module.exports) module.exports = { cleanCharacterDescription };
})(typeof globalThis !== 'undefined' ? globalThis : this);
