(function (root) {
  'use strict';
  const META_LABEL = /\b(?:Class|Relatives|Guild|Affiliation|Occupation|Hunter Rank|Rank|Blood Type|Level|Age|Height|Weight|Status|Species|Race|Gender|Nationality|Residence|Family|Parents|Siblings|Master|Teacher|Student|Team|Party|Organization|Title|Alias|Aliases|Birthday|Date of Birth)\s*:/ig;
  const PROSE_START = /(?:\b[A-Z][\w'’.-]*(?:\s+[A-Z][\w'’.-]*){0,3}\s+(?:is known|is a|is an|was a|was an|becomes|became|has become|possesses|serves|joins|leads|fights|lives|works|uses|can|will|has|had|was|is)\b|\b(?:He|She|They|It)\s+(?:is|was|has|had|becomes|became|can|will|would|possesses|serves|joins|leads|fights|lives|works|uses)\b)/i;
  function decodeEntities(value) {
    if (typeof document !== 'undefined' && document.createElement) {
      const node = document.createElement('textarea');
      node.innerHTML = value;
      return node.value;
    }
    return value.replace(/&nbsp;/gi, ' ').replace(/&amp;/gi, '&').replace(/&quot;/gi, '"')
      .replace(/&#39;|&apos;/gi, "'").replace(/&lt;/gi, '<').replace(/&gt;/gi, '>');
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
      .replace(/\`{1,3}/g, '')
      .replace(/\r/g, '\n');
    const lines = value.split(/\n+/).map(line => line.replace(/\s+/g, ' ').trim()).filter(Boolean);
    const proseLines = [];
    for (const line of lines) {
      META_LABEL.lastIndex = 0;
      const firstLabel = line.search(META_LABEL);
      if (firstLabel === 0) {
        const match = line.match(PROSE_START);
        META_LABEL.lastIndex = 0;
        if (match && match.index > 0 && META_LABEL.test(line.slice(0, match.index))) proseLines.push(line.slice(match.index).trim());
        META_LABEL.lastIndex = 0;
        continue;
      }
      if (firstLabel > 0) {
        const match = line.match(PROSE_START);
        META_LABEL.lastIndex = 0;
        if (match && match.index > 0 && META_LABEL.test(line.slice(0, match.index))) {
          proseLines.push(line.slice(match.index).trim());
          continue;
        }
      }
      META_LABEL.lastIndex = 0;
      proseLines.push(line);
    }
    value = proseLines.join(' ');
    META_LABEL.lastIndex = 0;
    const firstProse = value.match(PROSE_START);
    const labelBeforeProse = firstProse && META_LABEL.test(value.slice(0, firstProse.index));
    META_LABEL.lastIndex = 0;
    if (labelBeforeProse) value = value.slice(firstProse.index);
    value = value.replace(/\b(?:Class|Relatives|Guild|Affiliation|Occupation|Hunter Rank|Rank|Blood Type)\s*:\s*/gi, '')
      .replace(/\s+/g, ' ').trim();
    return value;
  }
  root.cleanCharacterDescription = cleanCharacterDescription;
  if (typeof module !== 'undefined' && module.exports) module.exports = { cleanCharacterDescription };
})(typeof globalThis !== 'undefined' ? globalThis : this);
