import React, { useRef, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import StudioLayout from '@/Components/Studio/StudioLayout';
import { inr } from '@/Components/Studio/Journey';
import { statusOf } from '@/Pages/Studio/Sell';

/*
 * My studio > Sell your art > the listing form (/dashboard/sell/new?submission=ID, /dashboard/sell/listings/ID).
 * The approved artwork's photo is always the cover; the student adds detail photos, the story, the physical details
 * and the price (with what they earn), then saves a draft or submits it for review.
 */

const MEDIUMS = ['Oil', 'Acrylic', 'Watercolour', 'Gouache', 'Pencil', 'Charcoal', 'Ink', 'Soft pastel', 'Oil pastel', 'Mixed media'];
const SURFACES = ['Stretched cotton canvas', 'Canvas board', 'Watercolour paper (300 gsm)', 'Drawing paper', 'Wood panel', 'Handmade paper'];

const toForm = (l, s) => (l ? {
    title: l.title || '', description: l.description || '', artistNotes: l.artist_notes || '', medium: l.medium || '', surface: l.surface || '',
    subject: l.subject || '', styleTags: l.style_tags || '', heightInches: l.height_inches || '', widthInches: l.width_inches || '',
    depthInches: Number(l.depth_inches) ? l.depth_inches : '', weightKg: Number(l.weight_kg) ? l.weight_kg : '', yearCreated: l.year_created || new Date().getFullYear(),
    framed: !!Number(l.is_framed), frameDetails: l.frame_details || '', signed: !!Number(l.is_signed), basePrice: l.base_price ? String(Math.round(Number(l.base_price))) : '',
} : {
    title: (s && s.title) || '', description: (s && s.description) || '', artistNotes: '', medium: '', surface: '', subject: '', styleTags: '',
    heightInches: '', widthInches: '', depthInches: '', weightKg: '', yearCreated: new Date().getFullYear(), framed: false, frameDetails: '', signed: true, basePrice: '',
});

export default function SellListing() {
    const { listing, submission, terms = {}, restriction } = usePage().props;
    const [v, setV] = useState(() => toForm(listing, submission));
    const cover = submission ? submission.file_url : null;
    const [photos, setPhotos] = useState(() => (listing && listing.images ? listing.images.map((i) => i.image_url).filter((u) => u !== cover) : []));
    const [errors, setErrors] = useState([]);
    const [busy, setBusy] = useState(null);
    const [upload, setUpload] = useState(null);
    const fileRef = useRef(null);
    const set = (k, x) => setV((p) => ({ ...p, [k]: x }));
    const live = listing && listing.stock_status === 'AVAILABLE';
    const pct = Number(terms.artistPercent || 70);
    const price = Number(String(v.basePrice).replace(/[^\d.]/g, '')) || 0;
    const earn = Math.floor((price * pct + 1e-6) / 100);     // same rounding as the server (whole rupees, down)

    const addPhotos = async (files) => {
        for (const f of Array.from(files || []).slice(0, Math.max(0, 11 - photos.length))) {
            const fd = new FormData();
            fd.append('file', f);
            setUpload(`Uploading ${f.name}…`);
            try {
                const r = await axios.post('/api/studio/sell/images', fd);
                setPhotos((p) => [...p, r.data.url]);
                setUpload(null);
            } catch (e) {
                setUpload(null);
                setErrors([(e.response && e.response.data && e.response.data.message) || `Couldn't upload ${f.name}.`]);
            }
        }
        if (fileRef.current) fileRef.current.value = '';
    };

    const save = (intent) => {
        const local = [];
        if (v.title.trim().length < 3) local.push('Give the painting a title (3–255 characters).');
        if (v.description.trim().length < 20) local.push('Describe the painting in at least 20 characters.');
        if (!v.medium.trim()) local.push('Choose the medium (for example Oil or Watercolour).');
        if (!v.surface.trim()) local.push('Describe the surface (for example Stretched cotton canvas).');
        if (!Number(v.heightInches) || !Number(v.widthInches)) local.push('Enter the height and width in inches.');
        if (v.framed && v.frameDetails.trim().length < 3) local.push('Describe the frame (material and glazing).');
        if (price < Number(terms.minPrice || 500)) local.push(`Set a price of at least ${inr(terms.minPrice || 500)}.`);
        if (local.length && intent === 'submit') { setErrors(local); window.scrollTo({ top: 0, behavior: 'smooth' }); return; }
        setErrors([]);
        setBusy(intent);
        const body = {
            ...v, basePrice: price, intent,
            images: photos.map((url) => ({ url, primary: false, alt: v.title })),
            ...(listing ? { version: Number(listing.version) } : { submissionId: submission.id }),
        };
        const req = listing ? axios.put(`/api/studio/sell/listings/${listing.id}`, body) : axios.post('/api/studio/sell/listings', body);
        req.then((r) => {
            try { window.sessionStorage.setItem('ac.sell.flash', r.data.message); } catch (e) { /* private mode */ }
            window.location.assign('/dashboard/sell');
        }).catch((e) => {
            const d = (e.response && e.response.data) || {};
            setErrors(d.errors && Array.isArray(d.errors) ? d.errors : [d.message || (d.errors && Object.values(d.errors)[0]) || 'Something went wrong. Please try again.']);
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }).finally(() => setBusy(null));
    };

    const input = (k, label, props = {}, hint) => (
        <label className="sl-field" htmlFor={`f-${k}`}>
            <span>{label}</span>
            <input id={`f-${k}`} value={v[k]} onChange={(e) => set(k, e.target.value)} {...props} />
            {hint && <small>{hint}</small>}
        </label>
    );
    const title = listing ? `Edit “${listing.title}”` : 'List your artwork';

    return (
        <StudioLayout title={title} active="/dashboard/sell" crumbs={[{ href: '/dashboard/sell', label: 'Sell your art' }]}
            subtitle={submission ? `From your approved work in ${submission.course_title}.` : 'Your listing'}>
            {restriction && <div className="sl-alert" role="alert"><i className="fa fa-ban" aria-hidden="true"></i> {restriction}</div>}
            {listing && (
                <p className="sl-status-line">Status: <b>{statusOf(listing).label}</b>{listing.changes_requested && <> · <span className="sl-note-inline">From our team: {listing.review_notes}</span></>}</p>
            )}
            {live && <div className="sl-alert sl-alert--info" role="note"><i className="fa fa-info-circle" aria-hidden="true"></i> This piece is live. Saving changes sends it back to review; it returns to the marketplace once approved.</div>}
            {errors.length > 0 && (
                <div className="sl-alert" role="alert"><b>Please fix:</b><ul>{errors.map((e) => <li key={e}>{e}</li>)}</ul></div>
            )}

            <div className="sl-editor">
                <div className="sl-editor__main">
                    <section className="sl-panel" aria-labelledby="h-photos">
                        <h2 id="h-photos">Photos</h2>
                        <p className="sl-small">The approved photo is the cover. Add close-ups, the back and the signature: collectors buy from detail.</p>
                        <div className="sl-photos">
                            {cover && <figure className="sl-photo is-cover"><img src={cover} alt="Cover" /><figcaption>Cover</figcaption></figure>}
                            {photos.map((u) => (
                                <figure key={u} className="sl-photo"><img src={u} alt="" />
                                    <button type="button" onClick={() => setPhotos((p) => p.filter((x) => x !== u))} aria-label="Remove this photo">×</button></figure>
                            ))}
                            {photos.length < 11 && (
                                <label className="sl-photo sl-photo--add" htmlFor="sl-file">
                                    <i className="fa fa-plus" aria-hidden="true"></i><span>Add photos</span>
                                    <input id="sl-file" ref={fileRef} type="file" accept="image/jpeg,image/png" multiple onChange={(e) => addPhotos(e.target.files)} />
                                </label>
                            )}
                        </div>
                        {upload && <p className="sl-small" role="status">{upload}</p>}
                    </section>

                    <section className="sl-panel" aria-labelledby="h-story">
                        <h2 id="h-story">The story</h2>
                        {input('title', 'Title', { maxLength: 255 })}
                        <label className="sl-field" htmlFor="f-description"><span>Description</span>
                            <textarea id="f-description" rows={5} value={v.description} maxLength={6000} onChange={(e) => set('description', e.target.value)} />
                            <small>What it shows, the mood, the light. At least 20 characters.</small></label>
                        <label className="sl-field" htmlFor="f-artistNotes"><span>Artist's notes <em className="sl-opt">optional</em></span>
                            <textarea id="f-artistNotes" rows={3} value={v.artistNotes} maxLength={6000} onChange={(e) => set('artistNotes', e.target.value)} />
                            <small>What you learned or tried while painting it.</small></label>
                        <div className="sl-grid2">
                            {input('subject', 'Subject', { maxLength: 100, placeholder: 'Landscape, portrait, still life…' })}
                            {input('styleTags', 'Style tags', { maxLength: 255, placeholder: 'impressionist, monsoon' }, 'Comma separated, optional.')}
                        </div>
                    </section>

                    <section className="sl-panel" aria-labelledby="h-details">
                        <h2 id="h-details">The piece</h2>
                        <div className="sl-grid2">
                            {input('medium', 'Medium', { list: 'sl-mediums', maxLength: 100 })}
                            {input('surface', 'Surface', { list: 'sl-surfaces', maxLength: 100 })}
                        </div>
                        <datalist id="sl-mediums">{MEDIUMS.map((m) => <option key={m} value={m} />)}</datalist>
                        <datalist id="sl-surfaces">{SURFACES.map((m) => <option key={m} value={m} />)}</datalist>
                        <div className="sl-grid4">
                            {input('heightInches', 'Height (in)', { inputMode: 'decimal' })}
                            {input('widthInches', 'Width (in)', { inputMode: 'decimal' })}
                            {input('depthInches', 'Depth (in)', { inputMode: 'decimal', placeholder: 'optional' })}
                            {input('yearCreated', 'Year', { inputMode: 'numeric' })}
                        </div>
                        <div className="sl-checks">
                            <label><input type="checkbox" checked={v.signed} onChange={(e) => set('signed', e.target.checked)} /> Signed by me</label>
                            <label><input type="checkbox" checked={v.framed} onChange={(e) => set('framed', e.target.checked)} /> Sold framed</label>
                        </div>
                        {v.framed && input('frameDetails', 'Frame', { maxLength: 255, placeholder: 'Black wood, glass' })}
                    </section>
                </div>

                <aside className="sl-editor__side">
                    <section className="sl-panel sl-price" aria-labelledby="h-price">
                        <h2 id="h-price">Your price</h2>
                        <label className="sl-field" htmlFor="f-basePrice"><span>Price of the original (₹)</span>
                            <input id="f-basePrice" inputMode="numeric" value={v.basePrice} onChange={(e) => set('basePrice', e.target.value.replace(/[^\d]/g, ''))} placeholder={String(terms.minPrice || 500)} />
                        </label>
                        <dl className="sl-earn">
                            <div><dt>Collector pays</dt><dd>{inr(price)}</dd></div>
                            <div><dt>ArtistikCity ({terms.commissionPercent}%)</dt><dd>− {inr(price - earn)}</dd></div>
                            <div className="is-total"><dt>You earn</dt><dd>{inr(earn)}</dd></div>
                        </dl>
                        <p className="sl-small">Shipping of the original is included in the price. Payable {terms.payoutDays} days after the sale.</p>
                        <div className="sl-actions sl-actions--stack">
                            <button type="button" className="sl-btn sl-btn--brand" disabled={!!busy || !!restriction} onClick={() => save('submit')}>
                                {busy === 'submit' ? 'Sending…' : live ? 'Save and send for review' : 'Submit for review'}</button>
                            {!live && <button type="button" className="sl-btn sl-btn--line" disabled={!!busy || !!restriction} onClick={() => save('draft')}>{busy === 'draft' ? 'Saving…' : 'Save draft'}</button>}
                            <a href="/dashboard/sell" className="sl-btn sl-btn--ghost">Cancel</a>
                        </div>
                    </section>
                    <p className="sl-small sl-side-note"><i className="fa fa-shield" aria-hidden="true"></i> Our team checks every listing (photos, details, price) before it goes live, and tells you here if something needs a change.</p>
                </aside>
            </div>
        </StudioLayout>
    );
}
