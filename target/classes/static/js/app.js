// Bibliotheca frontend
const state = {
  books: [],
  filter: '',
  status: '',
  size: localStorage.getItem('bib_size') || 'md',
  theme: localStorage.getItem('bib_theme') || 'light',
  reader: null,
};

const $ = (s, r=document) => r.querySelector(s);
const $$ = (s, r=document) => Array.from(r.querySelectorAll(s));

// PDF.js setup (module build)
import * as pdfjsLib from 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/4.8.69/pdf.min.mjs';
pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/4.8.69/pdf.worker.min.mjs';

function applyTheme() {
  document.documentElement.setAttribute('data-theme', state.theme === 'dark' ? 'dark' : 'light');
  localStorage.setItem('bib_theme', state.theme);
}
function toast(msg) {
  const t = $('#toast'); t.textContent = msg; t.classList.add('show');
  clearTimeout(t._t); t._t = setTimeout(()=>t.classList.remove('show'), 2200);
}
function colorFor(str='x') { let h=0; for (let i=0;i<str.length;i++) h=(h*31+str.charCodeAt(i))>>>0; const hue=h%360; return `linear-gradient(180deg,hsl(${hue} 35% 42%),hsl(${(hue+20)%360} 38% 28%))`; }

async function api(path, opts={}) {
  const res = await fetch(path, { headers: { ...(opts.body instanceof FormData ? {} : {'Content-Type':'application/json'}), ...(opts.headers||{}) }, ...opts });
  if (!res.ok) { let m='Erro'; try{const e=await res.json(); m=e.message||m;}catch{} throw new Error(m); }
  if (res.status === 204) return null;
  const ct = res.headers.get('content-type')||''; return ct.includes('application/json') ? res.json() : res.text();
}

async function loadBooks() {
  $('#shelfLoading').classList.remove('hidden');
  try {
    state.books = await api('/api/books');
    renderShelf();
  } catch(e){ toast(e.message); }
  finally { $('#shelfLoading').classList.add('hidden'); }
}

function renderShelf() {
  const container = $('#shelfContainer'); container.innerHTML='';
  const q = state.filter.toLowerCase();
  const list = state.books.filter(b => {
    const hay = `${b.title||''} ${b.author||''} ${b.tags||''}`.toLowerCase();
    if (q && !hay.includes(q)) return false;
    if (state.status && (b.progress?.status||'NOT_STARTED') !== state.status) return false;
    return true;
  });
  $('#emptyState').classList.toggle('hidden', list.length !== 0 || state.books.length !== 0);
  if (state.books.length === 0) { $('#emptyState').classList.remove('hidden'); return; }
  const perShelf = window.innerWidth < 700 ? 6 : 10;
  container.className = 'shelf-container size-' + state.size;
  for (let i=0;i<list.length;i+=perShelf) {
    const row = list.slice(i,i+perShelf);
    const shelf = document.createElement('div'); shelf.className='shelf';
    const books = document.createElement('div'); books.className='shelf-books';
    row.forEach(b => books.appendChild(renderBook(b)));
    shelf.appendChild(books); container.appendChild(shelf);
  }
}

function renderBook(b) {
  const el = document.createElement('button');
  el.className = 'book'; el.style.background = colorFor(b.title||b.id);
  el.setAttribute('aria-label', `${b.title} por ${b.author||'autor desconhecido'}`);
  const pct = Math.round(b.progress?.percent||0);
  el.innerHTML = `<div class="book-spine"><div class="book-title">${esc(b.title||'Sem título')}</div><div class="book-author">${esc(b.author||'')}</div></div><div class="progress-bar"><span style="width:${pct}%"></span></div>`;
  el.addEventListener('click', () => openBook(b, el));
  return el;
}
function esc(s){ return (s||'').replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[c])); }

async function openBook(book, el) {
  if (el) { el.classList.add('opening'); await new Promise(r=>setTimeout(r, 320)); }
  $('#shelfView').classList.add('hidden'); $('#shelfTopbar').classList.add('hidden');
  $('#readerView').classList.remove('hidden');
  if (state.reader) { try{ await state.reader.destroy(); }catch{} }
  state.reader = new Reader(book);
  await state.reader.init();
}

class Reader {
  constructor(book){ this.book=book; this.type=(book.extension||'').toLowerCase(); this.page=1; this.total=1; this.scale=1.2; this.anim=localStorage.getItem('bib_anim')||'flip'; this.mode='pdf'; this.pdf=null; this.epub=null; this.rendition=null; this.spread=false; this.saveTimer=null; this.uiTimer=null; this.cache=new Map(); }
  async init(){
    $('#readerTitle').textContent=this.book.title||''; $('#readerAuthor').textContent=this.book.author||'';
    $('#animSelect').value=this.anim; $('#themeSelect').value=localStorage.getItem('bib_read_theme')||'paper';
    this.setReadTheme($('#themeSelect').value);
    this.bind();
    if (this.type==='pdf') await this.initPdf();
    else if (this.type==='epub') await this.initEpub();
    else await this.initText();
    await this.restoreProgress();
    this.showUI(); this.updateInfo();
  }
  bind(){
    $('#backToShelf').onclick=()=>this.close();
    $('#prevPage').onclick=()=>this.prev(); $('#nextPage').onclick=()=>this.next();
    $('#tapPrev').onclick=()=>this.prev(); $('#tapNext').onclick=()=>this.next();
    $('#progressSlider').oninput=(e)=>{ if(this.mode==='pdf'||this.mode==='text'){ this.goTo(parseInt(e.target.value,10)); } };
    $('#zoomIn').onclick=()=>{ this.scale=Math.min(3,this.scale+0.1); this.render(); };
    $('#zoomOut').onclick=()=>{ this.scale=Math.max(0.5,this.scale-0.1); this.render(); };
    $('#themeSelect').onchange=(e)=>this.setReadTheme(e.target.value);
    $('#animSelect').onchange=(e)=>{ this.anim=e.target.value; localStorage.setItem('bib_anim',this.anim); };
    $('#fsBtn').onclick=()=>{ if(!document.fullscreenElement) document.documentElement.requestFullscreen(); else document.exitFullscreen(); };
    $('#bookmarkBtn').onclick=()=>this.addBookmark();
    $('#noteBtn').onclick=()=>this.addNote();
    $('#ttsBtn').onclick=()=>this.tts();
    $('#tocBtn').onclick=()=>$('#tocPanel').classList.toggle('hidden');
    $('#searchBtn').onclick=()=>$('#searchPanel').classList.toggle('hidden');
    $$('#readerView [data-close]').forEach(b=>b.onclick=()=>$('#'+b.dataset.close).classList.add('hidden'));
    $('#readerSearchBtn').onclick=()=>this.search();
    $('#spreadToggle').onclick=()=>{ if(this.mode==='epub'&&this.rendition){ this.spread=!this.spread; this.rendition.spread(this.spread?'auto':'none'); } else { toast('Spread disponível principalmente para EPUB/telas largas'); } };
    this.keyHandler=(e)=>{ if(e.key==='ArrowRight') this.next(); if(e.key==='ArrowLeft') this.prev(); if(e.key==='Escape') this.close(); };
    document.addEventListener('keydown', this.keyHandler);
    const stage=$('#readerStageWrap'); let last=0; const show=()=>this.showUI();
    stage.addEventListener('mousemove',show); stage.addEventListener('click',show); stage.addEventListener('touchstart',show,{passive:true});
    // swipe
    let sx=0; stage.addEventListener('touchstart',e=>{sx=e.touches[0].clientX;},{passive:true});
    stage.addEventListener('touchend',e=>{const dx=e.changedTouches[0].clientX-sx; if(Math.abs(dx)>40){ dx<0?this.next():this.prev(); }},{passive:true});
  }
  showUI(){ $('#readerView').classList.remove('ui-hidden'); clearTimeout(this.uiTimer); this.uiTimer=setTimeout(()=>$('#readerView').classList.add('ui-hidden'), 2500); }
  setReadTheme(t){ const st=$('#readerStage'); st.classList.remove('theme-paper','theme-sepia','theme-dark','theme-oled'); st.classList.add('theme-'+t); localStorage.setItem('bib_read_theme',t); }
  async initPdf(){
    this.mode='pdf'; $('#flipBook').classList.remove('hidden'); $('#epubContainer').classList.add('hidden'); $('#textContainer').classList.add('hidden');
    const loadingTask = pdfjsLib.getDocument(`/api/books/${this.book.id}/file`);
    this.pdf = await loadingTask.promise; this.total=this.pdf.numPages; $('#progressSlider').max=String(this.total);
    await this.render(true);
  }
  async renderPageToCanvas(num, canvas){
    if(!this.pdf || num<1 || num>this.total) { const ctx=canvas.getContext('2d'); canvas.width=800; canvas.height=1100; ctx.fillStyle='#fff'; ctx.fillRect(0,0,800,1100); return; }
    let page = this.cache.get(num); if(!page){ page = await this.pdf.getPage(num); this.cache.set(num,page); if(this.cache.size>6){ const k=this.cache.keys().next().value; this.cache.delete(k);} }
    const viewport = page.getViewport({ scale: this.scale * (window.devicePixelRatio||1) * 0.9 });
    const ctx = canvas.getContext('2d'); canvas.width = viewport.width; canvas.height = viewport.height;
    canvas.style.width = Math.min(viewport.width/(window.devicePixelRatio||1), $('#flipBook').clientWidth-10)+'px';
    canvas.style.height = 'auto';
    await page.render({ canvasContext: ctx, viewport }).promise;
  }
  async render(initial=false){
    if(this.mode==='pdf'){
      const cur=$('#pageCurrent'); cur.innerHTML=''; const c1=document.createElement('canvas'); cur.appendChild(c1); await this.renderPageToCanvas(this.page,c1);
      const nxt=$('#pageNext'); nxt.innerHTML=''; const c2=document.createElement('canvas'); nxt.appendChild(c2); await this.renderPageToCanvas(Math.min(this.total,this.page+1),c2);
      this.updateInfo();
    } else if(this.mode==='text') {
      this.renderTextPage(); this.updateInfo();
    }
  }
  async flip(direction){
    if(this._flipping) return; this._flipping=true;
    const bookEl=$('#flipBook'); bookEl.classList.remove('anim-flip','anim-slide','anim-fade','flipping-next','flipping-prev');
    void bookEl.offsetWidth; bookEl.classList.add('anim-'+this.anim, direction>0?'flipping-next':'flipping-prev');
    const dur = (window.matchMedia('(prefers-reduced-motion: reduce)').matches) ? 1 : (this.anim==='flip'?650:320);
    await new Promise(r=>setTimeout(r, dur));
    bookEl.classList.remove('flipping-next','flipping-prev');
    this._flipping=false; await this.render();
  }
  async next(){ if(this.mode==='epub'&&this.rendition){ this.rendition.next(); return; } if(this.page>=this.total) return; this.page++; await this.flip(1); this.scheduleSave(); }
  async prev(){ if(this.mode==='epub'&&this.rendition){ this.rendition.prev(); return; } if(this.page<=1) return; this.page--; await this.flip(-1); this.scheduleSave(); }
  async goTo(p){ if(this.mode==='epub') return; p=Math.max(1,Math.min(this.total,p)); if(p===this.page) return; const dir=p>this.page?1:-1; this.page=p; await this.flip(dir); this.scheduleSave(); }
  updateInfo(){
    const pct = this.total>1? ((this.mode==='epub'? (this._epubPct||0) : ((this.page-1)/(this.total-1))*100)) : 100;
    $('#pageInfo').textContent = this.mode==='epub'? `${Math.round(this._epubPct||0)}%` : `${this.page} / ${this.total}`;
    $('#percentInfo').textContent = `${Math.round(pct)}%`;
    if(this.mode!=='epub') $('#progressSlider').value=String(this.page);
    const minsLeft = Math.max(1, Math.round(((this.total - this.page) * 2))); // rough estimate
    $('#timeLeft').textContent = this.mode==='epub'? '' : `~${minsLeft} min restantes`;
  }
  scheduleSave(){ clearTimeout(this.saveTimer); this.saveTimer=setTimeout(()=>this.saveProgress(), 700); }
  async saveProgress(){
    const percent = this.mode==='epub'? (this._epubPct||0) : (this.total>1? ((this.page-1)/(this.total-1))*100 : 100);
    const location = this.mode==='epub'? (this._epubCfi||'') : String(this.page);
    try { await api(`/api/books/${this.book.id}/progress`, {method:'PUT', body: JSON.stringify({location, percent, status: percent>=99.5?'FINISHED':'READING'})}); } catch{}
  }
  async restoreProgress(){
    try{
      const p = await api(`/api/books/${this.book.id}/progress`);
      if(!p) return;
      if(this.mode==='pdf' || this.mode==='text'){ const loc=parseInt(p.location,10); if(loc>1&&loc<=this.total){ this.page=loc; await this.render(); this.updateInfo(); } }
      if(this.mode==='epub' && p.location && this.rendition){ try{ await this.rendition.display(p.location); }catch{} }
    }catch{}
  }
  async initEpub(){
    this.mode='epub'; $('#flipBook').classList.add('hidden'); $('#textContainer').classList.add('hidden');
    const cont=$('#epubContainer'); cont.classList.remove('hidden'); cont.innerHTML='';
    this.epub = ePub(`/api/books/${this.book.id}/file`);
    this.rendition = this.epub.renderTo(cont, { width:'100%', height:'100%', spread: window.innerWidth>900?'auto':'none', flow:'paginated' });
    await this.rendition.display();
    this.epub.loaded.navigation.then(nav=>{ const toc=$('#tocContent'); toc.innerHTML=''; (nav.toc||[]).forEach(item=>{ const d=document.createElement('div'); d.className='toc-item'; d.textContent=item.label; d.onclick=()=>this.rendition.display(item.href); toc.appendChild(d); }); });
    this.rendition.on('relocated', (loc)=>{ this._epubCfi=loc.start.cfi; this._epubPct = this.epub.locations && this.epub.locations.total ? (loc.start.location/this.epub.locations.total)*100 : (loc.start.percentage?loc.start.percentage*100:0); this.updateInfo(); this.scheduleSave(); });
    try{ await this.epub.locations.generate(1024); }catch{}
    this.total=100; $('#progressSlider').max='100';
  }
  async initText(){
    this.mode='text'; $('#flipBook').classList.add('hidden'); $('#epubContainer').classList.add('hidden');
    $('#textContainer').classList.remove('hidden');
    const res = await fetch(`/api/books/${this.book.id}/file`);
    this.fullText = await res.text();
    const chars = 2500; this.pages=[]; for(let i=0;i<this.fullText.length;i+=chars) this.pages.push(this.fullText.slice(i,i+chars));
    if(this.pages.length===0) this.pages=[''];
    this.total=this.pages.length; $('#progressSlider').max=String(this.total);
    this.renderTextPage();
  }
  renderTextPage(){ $('#textContainer').textContent = this.pages[this.page-1]||''; $('#textContainer').scrollTop=0; }
  async addBookmark(){
    const location = this.mode==='epub'? (this._epubCfi||'') : String(this.page);
    try { await api(`/api/books/${this.book.id}/bookmarks`, {method:'POST', body: JSON.stringify({location, label:`Página ${this.page}`})}); toast('Marcador salvo'); } catch(e){ toast(e.message); }
  }
  async addNote(){
    const sel = window.getSelection ? String(window.getSelection()) : '';
    const content = prompt('Nota:', sel ? `Sobre: ${sel.slice(0,40)}` : '');
    if(!content) return;
    const location = this.mode==='epub'? (this._epubCfi||'') : String(this.page);
    try { await api(`/api/books/${this.book.id}/notes`, {method:'POST', body: JSON.stringify({location, selectedText: sel, content, color:'#ffeb3b'})}); toast('Nota salva'); } catch(e){ toast(e.message); }
  }
  async search(){
    const q=$('#readerSearchInput').value.trim(); if(!q) return;
    const resBox=$('#searchResults'); resBox.innerHTML='Buscando...';
    try{
      const hits = await api(`/api/books/${this.book.id}/search?q=`+encodeURIComponent(q));
      if(!hits.length){ resBox.innerHTML='<div class="search-hit">Nenhum resultado</div>'; return; }
      resBox.innerHTML='';
      hits.forEach(h=>{ const d=document.createElement('div'); d.className='search-hit'; d.innerHTML=`<b>Pág. ${h.page}</b><br>${esc(h.snippet)}`; d.onclick=()=>{ if(this.mode==='pdf'||this.mode==='text'){ this.goTo(h.page); } }; resBox.appendChild(d); });
    }catch(e){ resBox.innerHTML='Erro na busca'; }
  }
  async tts(){
    if(!('speechSynthesis' in window)) { toast('TTS não suportado'); return; }
    if(window.speechSynthesis.speaking){ window.speechSynthesis.cancel(); toast('TTS pausado'); return; }
    let text='';
    if(this.mode==='text') text=this.pages[this.page-1]||'';
    else if(this.mode==='pdf' && this.pdf){ try{ const p=this.cache.get(this.page)||await this.pdf.getPage(this.page); const c=await p.getTextContent(); text=c.items.map(i=>i.str).join(' ');}catch{} }
    else { toast('TTS disponível para PDF/TXT com texto'); return; }
    if(!text.trim()) { toast('Sem texto para ler nesta página'); return; }
    const u=new SpeechSynthesisUtterance(text); u.lang='pt-BR'; window.speechSynthesis.speak(u); toast('Lendo em voz alta...');
  }
  async close(){
    await this.saveProgress();
    document.removeEventListener('keydown', this.keyHandler);
    if(this.rendition) try{ this.rendition.destroy(); }catch{}
    if(this.epub) try{ this.epub.destroy(); }catch{}
    $('#readerView').classList.add('hidden'); $('#shelfView').classList.remove('hidden'); $('#shelfTopbar').classList.remove('hidden');
    $('#tocPanel').classList.add('hidden'); $('#searchPanel').classList.add('hidden');
    window.speechSynthesis && window.speechSynthesis.cancel();
    await loadBooks();
  }
  async destroy(){ try{ await this.saveProgress(); }catch{} }
}

// Upload
const modal = $('#uploadModal');
$('#addBookBtn').onclick=()=>modal.classList.remove('hidden');
$('#emptyAddBtn').onclick=()=>modal.classList.remove('hidden');
$('#closeUpload').onclick=()=>modal.classList.add('hidden');
const dz=$('#dropZone'); const fi=$('#fileInput');
dz.onclick=()=>fi.click(); dz.onkeydown=(e)=>{ if(e.key==='Enter'||e.key===' ') fi.click(); };
['dragenter','dragover'].forEach(ev=>dz.addEventListener(ev,e=>{e.preventDefault(); dz.classList.add('drag');}));
['dragleave','drop'].forEach(ev=>dz.addEventListener(ev,e=>{e.preventDefault(); dz.classList.remove('drag');}));
dz.addEventListener('drop', e=>handleFiles(e.dataTransfer.files));
fi.onchange=()=>handleFiles(fi.files);
async function handleFiles(files){
  const prog=$('#uploadProgress'); prog.innerHTML='';
  for(const f of files){
    const row=document.createElement('div'); row.textContent=`Enviando ${f.name}...`; prog.appendChild(row);
    const fd=new FormData(); fd.append('file', f);
    try{ await api('/api/books/upload',{method:'POST', body:fd}); row.textContent=`✓ ${f.name}`; }
    catch(e){ row.textContent=`✗ ${f.name}: ${e.message}`; }
  }
  await loadBooks();
  setTimeout(()=>modal.classList.add('hidden'), 600);
}

// Topbar controls
$('#searchInput').addEventListener('input', e=>{ state.filter=e.target.value; renderShelf(); });
$('#statusFilter').addEventListener('change', e=>{ state.status=e.target.value; renderShelf(); });
$('#sizeSelect').value=state.size; $('#sizeSelect').addEventListener('change', e=>{ state.size=e.target.value; localStorage.setItem('bib_size',state.size); renderShelf(); });
$('#themeToggle').onclick=()=>{ state.theme = state.theme==='dark'?'light':'dark'; applyTheme(); };

applyTheme();
loadBooks();
window.addEventListener('resize', ()=>renderShelf());
