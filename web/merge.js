// Three-way merge of one bin, used when two phones change the same bin before they've seen each other's edit.
//   base   = the bin as both phones last agreed on it (what the server had at our last sync)
//   local  = this phone's version now
//   remote = the server's version now
// Rules: whatever only one side changed is kept. Items are merged one by one (by item id), so adding,
// editing or removing different items never loses anything. If both sides changed the same quantity,
// both changes count (5 → 4 here and 5 → 7 there gives 6). If both changed the same field, this phone wins.
// An edit beats a delete, so nothing quietly disappears.
(function (root) {
  "use strict";
  function stable(v) {
    if (v === undefined) return undefined;
    if (v === null || typeof v !== "object") return JSON.stringify(v);
    if (Array.isArray(v)) return "[" + v.map(x => { const s = stable(x); return s === undefined ? "null" : s; }).join(",") + "]";
    return "{" + Object.keys(v).sort().filter(k => v[k] !== undefined).map(k => JSON.stringify(k) + ":" + stable(v[k])).join(",") + "}";
  }
  const eq = (a, b) => stable(a) === stable(b);
  const clone = v => (v === undefined ? undefined : JSON.parse(JSON.stringify(v)));

  // field by field; `special` lets a caller merge some keys its own way
  function mergeFields(b, l, r, special) {
    b = b || {};
    const out = {};
    const keys = new Set([...Object.keys(l), ...Object.keys(r), ...Object.keys(b)]);
    for (const k of keys) {
      let v;
      if (special && special[k]) v = (l[k] === undefined && r[k] === undefined) ? undefined : special[k](b[k], l[k], r[k]);
      else if (eq(l[k], b[k])) v = r[k];
      else if (eq(r[k], b[k])) v = l[k];
      else v = l[k];
      if (v !== undefined) out[k] = clone(v);
    }
    return out;
  }

  function qty(b, l, r) {
    if (eq(l, b)) return r;
    if (eq(r, b)) return l;
    const nb = +(b == null ? 1 : b), nl = +(l == null ? 1 : l), nr = +(r == null ? 1 : r);
    if ([nb, nl, nr].some(isNaN)) return l;
    return Math.max(0, nr + (nl - nb));
  }

  // Lists of things with an identity (items by id, photos by id, barcodes by value).
  function mergeList(b, l, r, key, mergeOne) {
    b = b || []; l = l || []; r = r || [];
    const B = new Map(b.map(x => [key(x), x])), L = new Map(l.map(x => [key(x), x])), R = new Map(r.map(x => [key(x), x]));
    const out = [];
    for (const x of r) {
      const k = key(x);
      if (L.has(k)) out.push(mergeOne ? mergeOne(B.get(k), L.get(k), x) : clone(L.get(k)));
      else if (B.has(k)) { if (!eq(x, B.get(k))) out.push(clone(x)); }   // removed here: stays removed unless changed there
      else out.push(clone(x));                                            // new there
    }
    // things that only this phone still has: new here, or removed there but changed here
    l.forEach((y, i) => {
      const k = key(y);
      if (R.has(k)) return;
      if (B.has(k) && eq(y, B.get(k))) return;                            // removed there, untouched here
      let at = 0;                                                         // keep it next to the item it followed here
      for (let j = i - 1; j >= 0; j--) { const p = out.findIndex(z => key(z) === key(l[j])); if (p >= 0) { at = p + 1; break; } }
      out.splice(at, 0, clone(y));
    });
    return out;
  }

  function mergeItem(b, l, r) { return b ? mergeFields(b, l, r, { qty }) : clone(l); }
  const idKey = x => (x && typeof x === "object" ? x.id : x);
  const codeKey = x => stable(x);

  function mergeLog(b, l, r) {
    const seen = new Set(), out = [];
    for (const e of [...(l || []), ...(r || [])]) { const k = e.t + "|" + e.msg; if (!seen.has(k)) { seen.add(k); out.push(e); } }
    return out.sort((x, y) => y.t - x.t).slice(0, 40);
  }

  function mergeBin(base, local, remote) {
    if (!base) return clone(local);
    if (remote.deleted && local.deleted) return clone(remote);
    if (remote.deleted) return eq(local, base) ? clone(remote) : clone(local);
    if (local.deleted) return eq(remote, base) ? clone(local) : clone(remote);
    const b = base.deleted ? {} : base;
    return mergeFields(b, local, remote, {
      items: (bb, ll, rr) => mergeList(bb, ll, rr, idKey, mergeItem),
      photos: (bb, ll, rr) => mergeList(bb, ll, rr, idKey, (x, y, z) => mergeFields(x || {}, y, z)),
      codes: (bb, ll, rr) => mergeList(bb, ll, rr, codeKey),
      log: mergeLog,
    });
  }

  const api = { mergeBin, eq, stable, clone };
  if (typeof module !== "undefined" && module.exports) module.exports = api; else root.Merge = api;
})(this);
