/* RykoCollection shared utilities.
 * Kept dependency-free so the main app can migrate incrementally.
 */
(function(global){
  'use strict';

  const normalizeSearchText=value=>String(value??'')
    .normalize('NFKC')
    .toLocaleLowerCase()
    .trim()
    .replace(/\s+/g,' ');

  const clamp=(value,min,max)=>{
    const n=Number(value);
    return Number.isFinite(n)?Math.min(max,Math.max(min,n)):min;
  };

  const unique=(items)=>[...new Set((items||[]).filter(Boolean))];

  const debounce=(fn,wait=150)=>{
    let timer=0;
    return function(...args){
      clearTimeout(timer);
      timer=setTimeout(()=>fn.apply(this,args),wait);
    };
  };

  global.RykoUtils=Object.freeze({normalizeSearchText,clamp,unique,debounce});
})(window);
