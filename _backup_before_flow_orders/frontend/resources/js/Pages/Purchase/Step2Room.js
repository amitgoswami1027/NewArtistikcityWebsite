import React, { useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import PurchaseLayout from '@/Components/Shop/PurchaseLayout';
import RoomStage, { SCENES } from '@/Components/Shop/RoomStage';
import { FORMAT_INFO, FRAME_INFO, formatOf, normalise, priceOf, frameCost, inr, num, errorText } from '@/Components/Shop/shop';

const ROOM_KEY = 'artistikcity.room';

/** Step 2: the artwork on a wall, to scale. Change room, size and frame; drag it to where it would hang. */
export default function Step2Room() {
    const { painting: p, offer, draft, hold, serverNow } = usePage().props;
    const [sel, setSel] = useState(() => normalise(offer, { ...draft }));
    const [scene, setScene] = useState(() => { try { return window.sessionStorage.getItem(ROOM_KEY) || 'living'; } catch (e) { return 'living'; } });
    const [reset, setReset] = useState(0);
    const [measure, setMeasure] = useState(true);
    const [busy, setBusy] = useState(false);
    const [err, setErr] = useState(null);
    const f = formatOf(offer, sel.format);
    const price = useMemo(() => priceOf(offer, sel), [offer, sel]);
    const update = (patch) => setSel((s) => normalise(offer, { ...s, ...patch }));
    const pickScene = (k) => { setScene(k); try { window.sessionStorage.setItem(ROOM_KEY, k); } catch (e) { /* private mode */ } };
    const surface = sel.format === 'POSTER' || (sel.format === 'ORIGINAL' && !/canvas|panel|linen/i.test(p.surface)) ? 'paper' : 'canvas';

    const save = (to) => {
        setBusy(true); setErr(null);
        return axios.post('/api/v1/purchase/draft', { paintingId: p.id, ...sel })
            .then(() => Inertia.visit(to))
            .catch((e) => { setBusy(false); setErr(errorText(e)); });
    };

    if (!f || !price) return null;
    return (
        <PurchaseLayout painting={p} step={2} title="See it on your wall" hold={hold} serverNow={serverNow}>
            <div className="pz-room">
                <div className="pz-room__stage">
                    <div className="pz-rooms" role="tablist" aria-label="Room">
                        {Object.entries(SCENES).map(([k, v]) => (
                            <button key={k} type="button" role="tab" aria-selected={scene === k} className={scene === k ? 'is-on' : ''} onClick={() => pickScene(k)}>
                                <i className={`fa ${v.icon}`} aria-hidden="true"></i>{v.label}
                            </button>
                        ))}
                    </div>
                    <RoomStage image={p.image_url} title={p.title} widthIn={price.width} heightIn={price.height} frame={sel.frame}
                               surface={surface} scene={scene} showSize={measure} resetToken={reset} />
                    <div className="pz-room__tools">
                        <p className="pz-fine">Drawn to scale, {SCENES[scene].note}. Drag the artwork, or focus it and use the arrow keys.</p>
                        <div>
                            <label className="pz-check"><input type="checkbox" checked={measure} onChange={(e) => setMeasure(e.target.checked)} /> Show size</label>
                            <button type="button" className="pz-link" onClick={() => setReset((n) => n + 1)}><i className="fa fa-undo" aria-hidden="true"></i> Reset position</button>
                        </div>
                    </div>
                </div>

                <aside className="pz-room__panel">
                    <p className="pz-eyebrow">{FORMAT_INFO[sel.format].name}</p>
                    {sel.format === 'ORIGINAL' ? (
                        <div className="pz-option">
                            <p className="pz-legend">Size</p>
                            <p className="pz-static">{num(price.width)} × {num(price.height)} in <span className="pz-muted">· the original's real size</span></p>
                        </div>
                    ) : (
                        <fieldset className="pz-option">
                            <legend className="pz-legend">Size</legend>
                            <div className="pz-chips">
                                {f.sizes.map((s) => (
                                    <button key={s.code} type="button" aria-pressed={sel.size === s.code} className={sel.size === s.code ? 'is-on' : ''} onClick={() => update({ size: s.code })}>
                                        {num(s.width)} × {num(s.height)}″
                                    </button>
                                ))}
                            </div>
                        </fieldset>
                    )}

                    <fieldset className="pz-option">
                        <legend className="pz-legend">Frame</legend>
                        {f.frames.length <= 1 ? (
                            <p className="pz-static pz-muted">{p.is_framed ? 'This original arrives framed.' : 'Not available for this format.'}</p>
                        ) : (
                            <div className="pz-frames">
                                {f.frames.map((code) => {
                                    const extra = code === 'NONE' ? 0 : frameCost(offer, sel, code);
                                    return (
                                        <label key={code} className={`pz-frame ${sel.frame === code ? 'is-on' : ''}`}>
                                            <input type="radio" name="frame" value={code} checked={sel.frame === code} onChange={() => update({ frame: code })} />
                                            <span className={`pz-sw ${FRAME_INFO[code].swatch}`} aria-hidden="true"></span>
                                            <span className="pz-frame__name">{FRAME_INFO[code].name}</span>
                                            <span className="pz-frame__price">{code === 'NONE' ? 'Included' : `+${inr(extra)}`}</span>
                                        </label>
                                    );
                                })}
                            </div>
                        )}
                    </fieldset>

                    <div className="pz-summary">
                        <div><span>{sel.qty > 1 ? `${sel.qty} × ${inr(price.unit)}` : 'Price'}</span><b>{inr(price.total)}</b></div>
                        {price.framePrice > 0 && <p className="pz-fine">Includes {inr(price.framePrice)} for the {FRAME_INFO[sel.frame].name.toLowerCase()} frame{sel.qty > 1 ? ' each' : ''}.</p>}
                    </div>
                    {err && <p className="pz-error" role="alert">{err}</p>}
                    <div className="pz-nav">
                        <button type="button" className="pz-btn pz-btn--line" onClick={() => save('/purchase/step-1')} disabled={busy}><i className="fa fa-arrow-left" aria-hidden="true"></i> Back</button>
                        <button type="button" className="pz-btn pz-btn--brand" onClick={() => save('/purchase/preview')} disabled={busy}>
                            {busy && <i className="fa fa-circle-o-notch fa-spin" aria-hidden="true"></i>} Next: Review <i className="fa fa-arrow-right" aria-hidden="true"></i>
                        </button>
                    </div>
                </aside>
            </div>
        </PurchaseLayout>
    );
}
