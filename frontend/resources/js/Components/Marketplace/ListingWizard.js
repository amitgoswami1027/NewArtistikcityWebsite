import React, { useRef, useState } from 'react';
import axios from 'axios';
import { MEDIUMS, SURFACES, SUBJECTS, finalPrice, inr, num, errorText, Spinner, StatusChip } from '@/Components/Marketplace/shared';

/*
 * The three-step listing wizard shared by super admins, instructors and students:
 *   1 Visuals & story  ·  2 Structural ledger  ·  3 Pricing  ·  then Review.
 * Validation mirrors ListingInput on the server; the server stays the source of truth and returns the
 * step to jump back to when it disagrees.
 */

const STEPS = [
    { n: 1, title: 'Visuals & story', icon: 'fa-picture-o' },
    { n: 2, title: 'Structural ledger', icon: 'fa-arrows-alt' },
    { n: 3, title: 'Pricing', icon: 'fa-inr' },
    { n: 4, title: 'Review', icon: 'fa-check' },
];

const YEAR = new Date().getFullYear();

function fromPainting(p, defaults) {
    if (!p) {
        return {
            title: defaults.title || '', description: defaults.description || '', artistNotes: '', medium: '', surface: '', subject: '', styleTags: '',
            heightInches: '', widthInches: '', depthInches: '', weightKg: '', yearCreated: String(defaults.year || YEAR),
            framed: false, frameDetails: '', signed: true, certificate: true, basePrice: '', discountPercentage: 0,
            images: defaults.imageUrl ? [{ url: defaults.imageUrl, primary: true, alt: defaults.title || '' }] : [],
            artistAdminId: defaults.artistAdminId || '', artistName: '',
        };
    }
    return {
        title: p.title || '', description: p.description || '', artistNotes: p.artist_notes || '', medium: p.medium || '', surface: p.surface || '',
        subject: p.subject || '', styleTags: p.style_tags || '', heightInches: num(p.height_inches, 2), widthInches: num(p.width_inches, 2),
        depthInches: Number(p.depth_inches) ? num(p.depth_inches, 2) : '', weightKg: p.weight_kg ? num(p.weight_kg, 2) : '', yearCreated: String(p.year_created || YEAR),
        framed: !!p.is_framed, frameDetails: p.frame_details || '', signed: p.is_signed !== false, certificate: p.has_certificate !== false,
        basePrice: p.base_price ? String(Number(p.base_price)) : '', discountPercentage: Number(p.discount_percentage) || 0,
        images: (p.images || []).map((i) => ({ url: i.url, primary: !!i.primary, alt: i.alt || '' })),
        artistAdminId: p.artist_admin_id || '', artistName: p.artist_admin_id ? '' : (p.artist_name || ''),
    };
}

function validate(f, step) {
    const e = {};
    if (step === 1) {
        if (f.title.trim().length < 3) e.title = 'Give the painting a title (3 characters or more).';
        if (f.description.trim().length < 20) e.description = `Describe the painting in at least 20 characters (${f.description.trim().length}/20).`;
        if (!f.images.length) e.images = 'Upload at least one high-resolution photo.';
    }
    if (step === 2) {
        if (f.medium.trim().length < 2) e.medium = 'Choose the medium.';
        if (f.surface.trim().length < 2) e.surface = 'Describe the surface.';
        const h = Number(f.heightInches), w = Number(f.widthInches);
        if (!(h >= 1 && h <= 240)) e.heightInches = 'Between 1 and 240 inches.';
        if (!(w >= 1 && w <= 240)) e.widthInches = 'Between 1 and 240 inches.';
        if (f.depthInches !== '' && !(Number(f.depthInches) >= 0 && Number(f.depthInches) <= 24)) e.depthInches = 'Between 0 and 24 inches.';
        if (f.weightKg !== '' && !(Number(f.weightKg) >= 0 && Number(f.weightKg) <= 200)) e.weightKg = 'Between 0 and 200 kg.';
        const y = Number(f.yearCreated);
        if (!(Number.isInteger(y) && y >= 1900 && y <= YEAR)) e.yearCreated = `Between 1900 and ${YEAR}.`;
        if (f.framed && f.frameDetails.trim().length < 3) e.frameDetails = 'Describe the frame, for example "Black oak float frame with museum glass".';
    }
    if (step === 3) {
        const b = Number(f.basePrice);
        if (!(b >= 500 && b <= 50000000)) e.basePrice = 'Set a price between ₹500 and ₹5,00,00,000.';
    }
    return e;
}

function Field({ id, label, error, hint, children, className = '', optional }) {
    return (
        <div className={className}>
            <label htmlFor={id} className="block text-sm font-bold text-ink">{label}{optional && <span className="font-normal text-gray-400"> · optional</span>}</label>
            <div className="mt-1.5">{children}</div>
            {hint && !error && <p className="mt-1 text-xs text-gray-500">{hint}</p>}
            {error && <p className="mt-1 text-xs font-semibold text-red-600" role="alert">{error}</p>}
        </div>
    );
}
const inputCls = (err) => `w-full h-12 rounded-xl text-base border bg-white px-4 ${err ? 'border-red-400 focus:border-red-500 focus:ring-red-500' : 'border-gray-300 focus:border-brand focus:ring-brand'}`;
const areaCls = (err) => `w-full rounded-xl text-base border bg-white px-4 py-3 ${err ? 'border-red-400 focus:border-red-500 focus:ring-red-500' : 'border-gray-300 focus:border-brand focus:ring-brand'}`;

function Toggle({ checked, onChange, label, hint }) {
    return (
        <label className="flex items-start gap-4 rounded-xl border border-gray-200 p-4 cursor-pointer hover:border-gray-400">
            <span className="relative flex-none mt-0.5">
                <input type="checkbox" className="sr-only" checked={checked} onChange={(e) => onChange(e.target.checked)} />
                <span className={`block w-11 h-6 rounded-full transition ${checked ? 'bg-brand' : 'bg-gray-300'}`}></span>
                <span className={`absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-white shadow transform transition ${checked ? 'translate-x-5' : ''}`}></span>
            </span>
            <span><span className="block font-bold text-ink">{label}</span>{hint && <span className="block text-sm text-gray-500">{hint}</span>}</span>
        </label>
    );
}

function Uploader({ images, setImages, uploadUrl, error }) {
    const [over, setOver] = useState(false);
    const [queue, setQueue] = useState([]);
    const [err, setErr] = useState(null);
    const ref = useRef(null);

    const upload = async (files) => {
        setErr(null);
        const list = [...files].slice(0, 12 - images.length);
        for (const file of list) {
            const key = `${file.name}-${Date.now()}`;
            setQueue((q) => [...q, { key, name: file.name, pct: 0 }]);
            const fd = new FormData();
            fd.append('file', file);
            try {
                const r = await axios.post(uploadUrl, fd, { onUploadProgress: (ev) => setQueue((q) => q.map((x) => (x.key === key ? { ...x, pct: Math.round((ev.loaded / (ev.total || 1)) * 100) } : x))) });
                setImages((cur) => [...cur, { url: r.data.url, primary: cur.length === 0, alt: '', w: r.data.width, h: r.data.height }]);
            } catch (e) {
                setErr(`${file.name}: ${errorText(e, 'upload failed.')}`);
            }
            setQueue((q) => q.filter((x) => x.key !== key));
        }
    };
    const setPrimary = (i) => setImages((cur) => cur.map((m, j) => ({ ...m, primary: j === i })));
    const remove = (i) => setImages((cur) => { const n = cur.filter((_, j) => j !== i); if (n.length && !n.some((m) => m.primary)) n[0] = { ...n[0], primary: true }; return n; });
    const move = (i, d) => setImages((cur) => { const n = [...cur]; const j = i + d; if (j < 0 || j >= n.length) return cur; [n[i], n[j]] = [n[j], n[i]]; return n; });

    return (
        <div>
            <div className={`mk-drop rounded-2xl border-2 border-dashed ${error ? 'border-red-300' : 'border-gray-300'} p-8 text-center transition ${over ? 'is-over' : ''}`}
                 onDragOver={(e) => { e.preventDefault(); setOver(true); }} onDragLeave={() => setOver(false)}
                 onDrop={(e) => { e.preventDefault(); setOver(false); upload(e.dataTransfer.files); }}>
                <i className="fa fa-cloud-upload text-4xl text-brand" aria-hidden="true"></i>
                <p className="mt-3 text-lg font-bold text-ink">Drag photos here, or <button type="button" className="text-brand underline" onClick={() => ref.current && ref.current.click()}>browse</button></p>
                <p className="mt-1 text-sm text-gray-500">JPG or PNG · at least 800 px on the long side (2000 px+ lets collectors zoom into brushwork) · up to 12 photos, 20 MB each</p>
                <p className="mt-1 text-xs text-gray-400">Tip: shoot in daylight, square-on, then add details, the signature and the back of the canvas.</p>
                <input ref={ref} type="file" accept="image/jpeg,image/png" multiple className="sr-only" onChange={(e) => { upload(e.target.files); e.target.value = ''; }} aria-label="Upload photos" />
            </div>
            {queue.map((q) => (
                <div key={q.key} className="mt-3 rounded-xl border border-gray-200 p-3 text-sm">
                    <div className="flex justify-between"><span className="truncate">{q.name}</span><span className="font-mono">{q.pct}%</span></div>
                    <div className="mt-2 h-1.5 rounded-full bg-gray-100"><div className="h-1.5 rounded-full bg-brand transition-all" style={{ width: `${q.pct}%` }}></div></div>
                </div>
            ))}
            {(err || error) && <p className="mt-2 text-sm font-semibold text-red-600" role="alert">{err || error}</p>}
            {images.length > 0 && (
                <ul className="mt-5 grid grid-cols-2 sm:grid-cols-3 gap-4">
                    {images.map((m, i) => (
                        <li key={m.url} className={`rounded-xl border-2 overflow-hidden bg-white ${m.primary ? 'border-brand' : 'border-gray-200'}`}>
                            <div className="relative bg-gray-50 h-36 flex items-center justify-center">
                                <img src={m.url} alt={m.alt || `Photo ${i + 1}`} className="max-h-full max-w-full object-contain" />
                                {m.primary && <span className="absolute top-2 left-2 rounded-full bg-brand text-white text-xs font-bold px-2 py-0.5">Cover</span>}
                            </div>
                            <div className="p-2 flex items-center gap-1">
                                {!m.primary && <button type="button" onClick={() => setPrimary(i)} className="text-xs font-bold text-ink hover:text-brand px-2 py-1">Make cover</button>}
                                <span className="flex-1"></span>
                                <button type="button" onClick={() => move(i, -1)} disabled={i === 0} className="w-7 h-7 rounded text-gray-500 hover:bg-gray-100 disabled:opacity-30" aria-label="Move earlier"><i className="fa fa-angle-left" aria-hidden="true"></i></button>
                                <button type="button" onClick={() => move(i, 1)} disabled={i === images.length - 1} className="w-7 h-7 rounded text-gray-500 hover:bg-gray-100 disabled:opacity-30" aria-label="Move later"><i className="fa fa-angle-right" aria-hidden="true"></i></button>
                                <button type="button" onClick={() => remove(i)} className="w-7 h-7 rounded text-red-500 hover:bg-red-50" aria-label="Remove photo"><i className="fa fa-trash-o" aria-hidden="true"></i></button>
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
}

function Preview({ f, artistName }) {
    const cover = f.images.find((m) => m.primary) || f.images[0];
    const fp = finalPrice(f.basePrice, f.discountPercentage);
    return (
        <div className="rounded-2xl border border-gray-200 bg-white overflow-hidden">
            <p className="px-4 py-2 font-mono text-xs uppercase tracking-widest text-gray-500 border-b border-gray-100">Live preview</p>
            <div className="bg-gray-50 min-h-[160px] flex items-center justify-center p-4">
                {cover ? <img src={cover.url} alt="" className="mk-art max-h-56 w-auto" /> : <span className="text-sm text-gray-400">Your cover photo appears here</span>}
            </div>
            <div className="p-4">
                <p className="font-extrabold text-ink truncate">{f.title || 'Untitled'}</p>
                <p className="text-sm text-gray-500 truncate">{artistName || 'Artist'} · {f.yearCreated}</p>
                <p className="mt-1 text-xs text-gray-500">{f.medium || 'Medium'}{f.surface ? ` on ${f.surface.toLowerCase()}` : ''}{f.heightInches && f.widthInches ? ` · ${f.heightInches} × ${f.widthInches} in` : ''}</p>
                {Number(f.basePrice) > 0 && (
                    <div className="mt-2 flex items-baseline gap-2">
                        <span className="text-xl font-black text-ink">{inr(fp)}</span>
                        {Number(f.discountPercentage) > 0 && <><span className="text-sm text-gray-400 line-through">{inr(f.basePrice)}</span><span className="rounded-full bg-brand text-white text-xs font-bold px-2">-{f.discountPercentage}%</span></>}
                    </div>
                )}
            </div>
        </div>
    );
}

/**
 * @param mode 'publisher' (super admin: publishes directly) | 'instructor' | 'student' (both go to review)
 * @param endpoints { upload, create, update(id) } ; extra = fields added to every save (e.g. submissionId)
 */
export default function ListingWizard({ painting, defaults = {}, mode, endpoints, extra = {}, artists = [], artistName, onSaved }) {
    const [f, setF] = useState(() => fromPainting(painting, defaults));
    const [step, setStep] = useState(1);
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(null);
    const [serverErr, setServerErr] = useState(null);
    const set = (k, v) => setF((x) => ({ ...x, [k]: v }));
    const setImages = (fn) => setF((x) => ({ ...x, images: typeof fn === 'function' ? fn(x.images) : fn }));
    const status = painting ? painting.raw_status || painting.stock_status : null;
    const live = status === 'AVAILABLE';
    const publisher = mode === 'publisher';
    const fp = finalPrice(f.basePrice, f.discountPercentage);
    const shownArtist = publisher && f.artistAdminId ? ((artists.find((a) => String(a.id) === String(f.artistAdminId)) || {}).name) : publisher && f.artistName ? f.artistName : artistName;

    const next = () => {
        const e = validate(f, step);
        setErrors(e);
        if (Object.keys(e).length === 0) { setStep((s) => Math.min(4, s + 1)); window.scrollTo({ top: 0, behavior: 'smooth' }); }
    };
    const goto = (n) => {
        // only allow jumping forward past steps that validate
        for (let s = 1; s < n; s += 1) { const e = validate(f, s); if (Object.keys(e).length) { setErrors(e); setStep(s); return; } }
        setErrors({}); setStep(n);
    };

    const save = (intent) => {
        for (let s = 1; s <= 3; s += 1) { const e = validate(f, s); if (Object.keys(e).length) { setErrors(e); setStep(s); return; } }
        setBusy(intent); setServerErr(null);
        const body = {
            ...extra, ...f, intent, version: painting ? painting.version : undefined,
            heightInches: Number(f.heightInches), widthInches: Number(f.widthInches),
            depthInches: f.depthInches === '' ? null : Number(f.depthInches), weightKg: f.weightKg === '' ? null : Number(f.weightKg),
            yearCreated: Number(f.yearCreated), basePrice: Number(f.basePrice), discountPercentage: Number(f.discountPercentage),
            artistAdminId: f.artistAdminId ? Number(f.artistAdminId) : null,
            images: f.images.map(({ url, primary, alt }) => ({ url, primary, alt: alt || f.title })),
        };
        const req = painting ? axios.put(endpoints.update(painting.id), body) : axios.post(endpoints.create, body);
        req.then((r) => onSaved(r.data)).catch((e) => {
            setBusy(null);
            const d = e.response && e.response.data;
            if (d && d.step && d.step < 4) setStep(d.step);
            setServerErr(d && d.errors ? d.errors : [errorText(e)]);
        });
    };

    const actions = publisher
        ? [['draft', 'Save as draft', false], [live ? 'publish' : 'publish', live ? 'Save & keep live' : 'Publish to marketplace', true]]
        : [['draft', live ? 'Withdraw to draft & save' : 'Save as draft', false], ['submit', live ? 'Save & resubmit for review' : 'Submit for review', true]];

    return (
        <div className="grid grid-cols-1 xl:grid-cols-12 gap-8">
            <div className="xl:col-span-8">
                {/* stepper */}
                <ol className="flex flex-wrap items-center gap-2 mb-8" aria-label="Listing steps">
                    {STEPS.map((s, i) => {
                        const done = s.n < step;
                        const cur = s.n === step;
                        return (
                            <li key={s.n} className="flex items-center gap-2">
                                <button type="button" onClick={() => goto(s.n)} aria-current={cur ? 'step' : undefined}
                                        className={`flex items-center gap-2 h-10 pl-1.5 pr-4 rounded-full border text-sm font-bold ${cur ? 'bg-ink text-white border-ink' : done ? 'bg-white border-ink text-ink' : 'bg-white border-gray-300 text-gray-500'}`}>
                                    <span className={`w-7 h-7 rounded-full flex items-center justify-center text-xs ${cur ? 'bg-brand text-white' : done ? 'bg-ink text-white' : 'bg-gray-100'}`}>{done ? <i className="fa fa-check" aria-hidden="true"></i> : s.n}</span>
                                    {s.title}
                                </button>
                                {i < STEPS.length - 1 && <span className="hidden sm:block w-6 h-px bg-gray-300" aria-hidden="true"></span>}
                            </li>
                        );
                    })}
                </ol>

                {painting && painting.review_notes && status === 'DRAFT' && (
                    <div className="mb-6 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-900"><strong>Reviewer notes:</strong> {painting.review_notes}</div>
                )}

                <div className="rounded-2xl border border-gray-200 bg-white p-6 sm:p-8">
                    {step === 1 && (
                        <div className="space-y-6">
                            <div><h2 className="text-2xl font-black tracking-tight text-ink">Visuals & story</h2><p className="text-gray-600">Collectors buy with their eyes first, then with their heart. Show the work clearly and tell its story.</p></div>
                            <Uploader images={f.images} setImages={setImages} uploadUrl={endpoints.upload} error={errors.images} />
                            {publisher && (
                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                                    <Field id="artist" label="Artist" hint="Pick a studio artist, or type a guest artist's name.">
                                        <select id="artist" value={f.artistAdminId} onChange={(e) => set('artistAdminId', e.target.value)} className={inputCls(false)}>
                                            <option value="">Guest artist…</option>
                                            {artists.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
                                        </select>
                                    </Field>
                                    {!f.artistAdminId && <Field id="artistName" label="Guest artist name"><input id="artistName" value={f.artistName} onChange={(e) => set('artistName', e.target.value)} className={inputCls(false)} /></Field>}
                                </div>
                            )}
                            <Field id="title" label="Title" error={errors.title}><input id="title" value={f.title} maxLength={255} onChange={(e) => set('title', e.target.value)} className={inputCls(errors.title)} placeholder="e.g. The Old Mill at Dawn" /></Field>
                            <Field id="description" label="Description" error={errors.description} hint="What's in the painting and what makes it special. Shown at the top of the story panel.">
                                <textarea id="description" rows={4} value={f.description} onChange={(e) => set('description', e.target.value)} className={areaCls(errors.description)} />
                            </Field>
                            <Field id="notes" label="Artist's inspiration & creative notes" optional hint="In the first person: where the idea came from, how you painted it. This builds the emotional connection.">
                                <textarea id="notes" rows={5} value={f.artistNotes} onChange={(e) => set('artistNotes', e.target.value)} className={areaCls(false)} />
                            </Field>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                                <Field id="subject" label="Subject" optional>
                                    <input id="subject" list="mk-subjects" value={f.subject} onChange={(e) => set('subject', e.target.value)} className={inputCls(false)} />
                                    <datalist id="mk-subjects">{SUBJECTS.map((s) => <option key={s} value={s} />)}</datalist>
                                </Field>
                                <Field id="tags" label="Style tags" optional hint="Comma separated, e.g. Realism, Nature"><input id="tags" value={f.styleTags} onChange={(e) => set('styleTags', e.target.value)} className={inputCls(false)} /></Field>
                            </div>
                        </div>
                    )}

                    {step === 2 && (
                        <div className="space-y-6">
                            <div><h2 className="text-2xl font-black tracking-tight text-ink">Structural ledger</h2><p className="text-gray-600">The physical facts collectors and couriers rely on. Measure the artwork itself, without the frame.</p></div>
                            <Field id="medium" label="Medium" error={errors.medium}>
                                <div className="flex flex-wrap gap-2 mb-3">
                                    {MEDIUMS.map((m) => (
                                        <button key={m} type="button" onClick={() => set('medium', m)} aria-pressed={f.medium === m}
                                                className={`h-9 px-3 rounded-full text-sm font-bold border ${f.medium === m ? 'bg-ink text-white border-ink' : 'bg-white border-gray-300 text-gray-700 hover:border-ink'}`}>{m}</button>
                                    ))}
                                </div>
                                <input id="medium" value={f.medium} onChange={(e) => set('medium', e.target.value)} className={inputCls(errors.medium)} placeholder="Or type another medium" />
                            </Field>
                            <Field id="surface" label="Surface" error={errors.surface}>
                                <input id="surface" list="mk-surfaces" value={f.surface} onChange={(e) => set('surface', e.target.value)} className={inputCls(errors.surface)} placeholder="e.g. Stretched cotton canvas" />
                                <datalist id="mk-surfaces">{SURFACES.map((s) => <option key={s} value={s} />)}</datalist>
                            </Field>
                            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                                <Field id="h" label="Height (in)" error={errors.heightInches} hint={f.heightInches ? `${(Number(f.heightInches) * 2.54).toFixed(1)} cm` : null}><input id="h" type="number" step="0.25" min="1" inputMode="decimal" value={f.heightInches} onChange={(e) => set('heightInches', e.target.value)} className={inputCls(errors.heightInches)} /></Field>
                                <Field id="w" label="Width (in)" error={errors.widthInches} hint={f.widthInches ? `${(Number(f.widthInches) * 2.54).toFixed(1)} cm` : null}><input id="w" type="number" step="0.25" min="1" inputMode="decimal" value={f.widthInches} onChange={(e) => set('widthInches', e.target.value)} className={inputCls(errors.widthInches)} /></Field>
                                <Field id="d" label="Depth (in)" optional error={errors.depthInches}><input id="d" type="number" step="0.05" min="0" inputMode="decimal" value={f.depthInches} onChange={(e) => set('depthInches', e.target.value)} className={inputCls(errors.depthInches)} /></Field>
                                <Field id="kg" label="Weight (kg)" optional error={errors.weightKg}><input id="kg" type="number" step="0.1" min="0" inputMode="decimal" value={f.weightKg} onChange={(e) => set('weightKg', e.target.value)} className={inputCls(errors.weightKg)} /></Field>
                            </div>
                            <Field id="year" label="Year created" error={errors.yearCreated} className="max-w-xs"><input id="year" type="number" min="1900" max={YEAR} value={f.yearCreated} onChange={(e) => set('yearCreated', e.target.value)} className={inputCls(errors.yearCreated)} /></Field>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                <Toggle checked={f.framed} onChange={(v) => set('framed', v)} label="Framed" hint="Ships framed and ready to hang" />
                                <Toggle checked={f.signed} onChange={(v) => set('signed', v)} label="Signed" hint="Hand-signed by the artist" />
                                <Toggle checked={f.certificate} onChange={(v) => set('certificate', v)} label="Certificate" hint="Certificate of authenticity included" />
                            </div>
                            {f.framed && <Field id="frame" label="Frame details" error={errors.frameDetails}><input id="frame" value={f.frameDetails} onChange={(e) => set('frameDetails', e.target.value)} className={inputCls(errors.frameDetails)} placeholder="e.g. Black oak float frame with museum glass" /></Field>}
                        </div>
                    )}

                    {step === 3 && (
                        <div className="space-y-8">
                            <div><h2 className="text-2xl font-black tracking-tight text-ink">Pricing mechanics</h2><p className="text-gray-600">Set the base price, then an optional markdown. Collectors see the original price struck through and the saving.</p></div>
                            <Field id="base" label="Base price (INR)" error={errors.basePrice} className="max-w-sm">
                                <div className="relative">
                                    <span className="absolute inset-y-0 left-4 flex items-center text-gray-500 text-lg pointer-events-none">₹</span>
                                    <input id="base" type="number" min="500" step="100" inputMode="numeric" value={f.basePrice} onChange={(e) => set('basePrice', e.target.value)} className={`${inputCls(errors.basePrice)} pl-9 text-lg font-mono`} />
                                </div>
                            </Field>
                            <div>
                                <div className="flex items-end justify-between">
                                    <label htmlFor="disc" className="block text-sm font-bold text-ink">Discount</label>
                                    <span className="font-mono text-2xl font-black text-brand" aria-live="polite">{Number(f.discountPercentage)}%</span>
                                </div>
                                <input id="disc" type="range" min="0" max="90" step="1" value={f.discountPercentage} onChange={(e) => set('discountPercentage', Number(e.target.value))}
                                       className="mk-range w-full mt-3" style={{ background: `linear-gradient(to right, #e5156b ${(f.discountPercentage / 90) * 100}%, #e5e7eb ${(f.discountPercentage / 90) * 100}%)` }}
                                       aria-valuetext={`${f.discountPercentage} percent`} />
                                <div className="mt-2 flex justify-between text-xs text-gray-400 font-mono"><span>0%</span><span>30%</span><span>60%</span><span>90%</span></div>
                                <div className="mt-3 flex flex-wrap gap-2">
                                    {[0, 10, 15, 20, 25].map((d) => <button key={d} type="button" onClick={() => set('discountPercentage', d)} className={`h-8 px-3 rounded-full text-xs font-bold border ${Number(f.discountPercentage) === d ? 'bg-ink text-white border-ink' : 'border-gray-300 hover:border-ink'}`}>{d === 0 ? 'No discount' : `${d}%`}</button>)}
                                </div>
                            </div>
                            <div className="rounded-2xl bg-ink text-white p-6 sm:p-8" aria-live="polite">
                                <p className="font-mono text-xs uppercase tracking-widest text-pink-300">Collector pays</p>
                                <div className="mt-2 flex flex-wrap items-baseline gap-x-4 gap-y-1">
                                    <span className="text-5xl font-black tracking-tight">{Number(f.basePrice) > 0 ? inr(fp) : '₹—'}</span>
                                    {Number(f.discountPercentage) > 0 && Number(f.basePrice) > 0 && <><span className="text-xl text-gray-400 line-through">{inr(f.basePrice)}</span><span className="rounded-full bg-brand px-3 py-1 text-sm font-bold">-{f.discountPercentage}% off</span></>}
                                </div>
                                {Number(f.discountPercentage) > 0 && Number(f.basePrice) > 0 && <p className="mt-2 text-sm text-gray-300">Saving {inr(Number(f.basePrice) - fp)} · rounded to the paisa exactly as the server stores it</p>}
                                {Number(f.discountPercentage) > 40 && <p className="mt-3 text-sm text-yellow-300"><i className="fa fa-exclamation-triangle mr-1" aria-hidden="true"></i>Deep discounts can make collectors question value. Consider 10–25%.</p>}
                            </div>
                        </div>
                    )}

                    {step === 4 && (
                        <div className="space-y-6">
                            <div><h2 className="text-2xl font-black tracking-tight text-ink">Review</h2><p className="text-gray-600">{publisher ? 'Check everything, then publish or keep it as a draft.' : 'Check everything. A moderator reviews each original before it goes live, usually within 2 working days.'}</p></div>
                            <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-8">
                                {[
                                    ['Title', f.title, 1], ['Artist', shownArtist, 1], ['Photos', `${f.images.length} photo${f.images.length === 1 ? '' : 's'}`, 1],
                                    ['Medium & surface', `${f.medium} on ${f.surface}`, 2], ['Size', `${f.heightInches} × ${f.widthInches} in${f.depthInches ? ` × ${f.depthInches} in` : ''}`, 2],
                                    ['Year', f.yearCreated, 2], ['Framing', f.framed ? `Framed · ${f.frameDetails}` : 'Unframed', 2],
                                    ['Signed / COA', `${f.signed ? 'Signed' : 'Unsigned'} · ${f.certificate ? 'COA included' : 'No COA'}`, 2],
                                    ['Price', `${inr(fp)}${Number(f.discountPercentage) > 0 ? ` (${inr(f.basePrice)} − ${f.discountPercentage}%)` : ''}`, 3],
                                ].map(([k, v, s]) => (
                                    <div key={k} className="py-3 border-b border-gray-100 flex justify-between gap-4">
                                        <dt className="text-sm text-gray-500">{k}</dt>
                                        <dd className="text-sm font-semibold text-ink text-right">{v} <button type="button" onClick={() => setStep(s)} className="ml-2 text-xs font-bold text-brand">Edit</button></dd>
                                    </div>
                                ))}
                            </dl>
                            {serverErr && <ul className="rounded-xl bg-red-50 border border-red-200 p-4 text-sm text-red-800 list-disc pl-8" role="alert">{serverErr.map((m) => <li key={m}>{m}</li>)}</ul>}
                            <div className="flex flex-wrap gap-3 pt-2">
                                {actions.map(([intent, label, primary]) => (
                                    <button key={intent} type="button" onClick={() => save(intent)} disabled={!!busy}
                                            className={`inline-flex items-center gap-2 h-12 px-6 rounded-full font-bold disabled:opacity-60 ${primary ? 'bg-brand text-white hover:bg-brand-dark' : 'border border-gray-300 bg-white hover:border-ink'}`}>
                                        {busy === intent && <Spinner />}{label}
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}

                    {step < 4 && (
                        <div className="mt-8 pt-6 border-t border-gray-100 flex items-center justify-between gap-3">
                            <button type="button" onClick={() => setStep((s) => Math.max(1, s - 1))} disabled={step === 1} className="h-12 px-5 rounded-full font-bold text-gray-600 hover:text-ink disabled:opacity-30"><i className="fa fa-arrow-left mr-2" aria-hidden="true"></i>Back</button>
                            <div className="flex gap-3">
                                {step > 1 && !live && <button type="button" onClick={() => save('draft')} disabled={!!busy} className="hidden sm:inline-flex items-center h-12 px-5 rounded-full border border-gray-300 font-bold hover:border-ink">{busy === 'draft' ? <Spinner /> : 'Save draft'}</button>}
                                <button type="button" onClick={next} className="h-12 px-6 rounded-full bg-ink text-white font-bold hover:bg-gray-800">Continue <i className="fa fa-arrow-right ml-2" aria-hidden="true"></i></button>
                            </div>
                        </div>
                    )}
                    {serverErr && step < 4 && <ul className="mt-4 rounded-xl bg-red-50 border border-red-200 p-4 text-sm text-red-800 list-disc pl-8" role="alert">{serverErr.map((m) => <li key={m}>{m}</li>)}</ul>}
                </div>
            </div>
            <aside className="xl:col-span-4">
                <div className="xl:sticky xl:top-24 space-y-4">
                    {status && <p className="text-sm text-gray-600">Current status: <StatusChip status={status} /></p>}
                    <Preview f={f} artistName={shownArtist} />
                    <div className="rounded-2xl bg-gray-50 border border-gray-200 p-5 text-sm text-gray-600">
                        <p className="font-bold text-ink"><i className="fa fa-lightbulb-o text-brand mr-2" aria-hidden="true"></i>{publisher ? 'Publishing' : 'What happens next'}</p>
                        <p className="mt-2">{publisher ? 'Published pieces appear in the marketplace immediately. While a collector holds a piece (15 minutes), it can\'t be edited.' : 'After you submit, a moderator checks photos, facts and price. If something needs changing you\'ll see their notes here. Once live, collectors can reserve and buy it.'}</p>
                    </div>
                </div>
            </aside>
        </div>
    );
}
