import React, { useEffect, useRef, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, Card, EmptyState, Drawer, useConfirm } from '@/Components/Admin/kit';
import { Field, Toggle } from '@/Components/Admin/form';
import { RoomView } from '@/Components/Shop/ArtPreview';

/*
 * Admin > System parameters > Room Preview Setup (/admin/setup/room-preview). The room photos buyers see a painting in before they pay (/purchase/step-2 and the
 * artwork page's "On a wall" tab). Each photo has a hanging area, drawn on the photo here, and optionally the real
 * width of that area so paintings are shown to scale. The page adapts to however many rooms are shown (none = the
 * artwork on its own).
 */

const EXAMPLE = { w: 24, h: 30 };   // inches: the sample piece hung in the editor's preview

const blankForm = () => ({ id: null, label: '', note: '', widthIn: '', active: true, area: { x: 25, y: 15, w: 50, h: 45 }, photo: null, file: null, width: 0, height: 0 });

/** Drag on the photo to draw the hanging area; drag inside it to move it. Values are percent of the photo. */
function AreaEditor({ src, area, onChange }) {
    const box = useRef(null);
    const drag = useRef(null);
    const at = (e) => {
        const r = box.current.getBoundingClientRect();
        return { x: Math.max(0, Math.min(100, ((e.clientX - r.left) / r.width) * 100)), y: Math.max(0, Math.min(100, ((e.clientY - r.top) / r.height) * 100)) };
    };
    const down = (e) => {
        if (e.button !== undefined && e.button !== 0) return;
        e.preventDefault();
        if (e.currentTarget.setPointerCapture) e.currentTarget.setPointerCapture(e.pointerId);
        const p = at(e);
        const inside = p.x >= area.x && p.x <= area.x + area.w && p.y >= area.y && p.y <= area.y + area.h;
        drag.current = inside ? { mode: 'move', p0: p, a0: area } : { mode: 'draw', p0: p };
    };
    const move = (e) => {
        if (!drag.current) return;
        const p = at(e);
        const d = drag.current;
        if (d.mode === 'move') {
            const x = Math.max(0, Math.min(100 - d.a0.w, d.a0.x + p.x - d.p0.x));
            const y = Math.max(0, Math.min(100 - d.a0.h, d.a0.y + p.y - d.p0.y));
            onChange({ ...d.a0, x, y });
        } else {
            onChange({ x: Math.min(d.p0.x, p.x), y: Math.min(d.p0.y, p.y), w: Math.abs(p.x - d.p0.x), h: Math.abs(p.y - d.p0.y) });
        }
    };
    const up = () => { drag.current = null; };
    const nudge = (e) => {
        const step = e.shiftKey ? 5 : 1;
        const m = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] }[e.key];
        if (!m) return;
        e.preventDefault();
        onChange({ ...area, x: Math.max(0, Math.min(100 - area.w, area.x + m[0])), y: Math.max(0, Math.min(100 - area.h, area.y + m[1])) });
    };
    return (
        <div className="rm-area" ref={box} onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up}
             role="application" aria-label="Hanging area. Drag on the photo to draw it, drag inside it to move it, or use the arrow keys." tabIndex={0} onKeyDown={nudge}>
            <img src={src} alt="" draggable="false" />
            <span className="rm-area__box" style={{ left: `${area.x}%`, top: `${area.y}%`, width: `${area.w}%`, height: `${area.h}%` }}>
                <span>Hanging area</span>
            </span>
        </div>
    );
}

function RoomEditor({ initial, minWidth, onClose, onSaved, toast }) {
    const [f, setF] = useState(initial);
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const set = (patch) => setF((x) => ({ ...x, ...patch }));
    const pick = (file) => {
        if (!file) return;
        const url = URL.createObjectURL(file);
        const img = new Image();
        img.onload = () => {
            set({ file, photo: url, width: img.naturalWidth, height: img.naturalHeight });
            setErrors((e) => ({ ...e, photo: img.naturalWidth < minWidth ? `This photo is ${img.naturalWidth} px wide. Use one at least ${minWidth} px wide (2000+ looks best).` : null }));
        };
        img.src = url;
    };
    const save = () => {
        setBusy(true); setErrors({});
        const fd = new FormData();
        fd.append('label', f.label);
        fd.append('note', f.note || '');
        fd.append('widthIn', f.widthIn === null || f.widthIn === undefined ? '' : String(f.widthIn));
        fd.append('active', f.active ? 'true' : 'false');
        ['x', 'y', 'w', 'h'].forEach((k) => fd.append(k, String(Math.round(f.area[k] * 1000) / 1000)));
        if (f.file) fd.append('photo', f.file);
        axios.post(f.id ? `/admin/api/rooms/${f.id}` : '/admin/api/rooms', fd)
            .then((r) => { toast(f.id ? `${f.label} saved` : `${f.label} added`); onSaved(r.data.rooms); })
            .catch((e) => { setBusy(false); const d = e.response && e.response.data; if (d && d.errors) setErrors(d.errors); toast(errorText(e), 'error'); });
    };
    const preview = f.photo ? { id: 'preview', label: f.label || 'Room', photo: f.photo, width: f.width || 1600, height: f.height || 1067, area: f.area, areaWidthIn: Number(f.widthIn) > 0 ? Number(f.widthIn) : null } : null;
    return (
        <Drawer open title={f.id ? `Edit ${initial.label}` : 'Add a room'} subtitle="Buyers see their painting hung on this wall." onClose={onClose} size="lg"
                footer={<><Button onClick={onClose}>Cancel</Button><Button variant="dark" busy={busy} onClick={save} disabled={!f.photo || !f.label.trim()}>{f.id ? 'Save room' : 'Add room'}</Button></>}>
            <div className="rm-editor">
                <Field label="Room photo" name="photo" error={errors.photo} hint={`Landscape, a straight-on view of a wall, at least ${minWidth} px wide (2000 px or more looks sharpest). JPG, PNG or WebP, up to 8 MB.`}>
                    <input type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => pick(e.target.files && e.target.files[0])} aria-label="Room photo" />
                </Field>
                {f.photo && (
                    <>
                        <Field label="Where a painting hangs" name="area" error={errors.area} hint="Drag on the photo to mark the free part of the wall (for example above the sofa). Drag inside the box to move it.">
                            <AreaEditor src={f.photo} area={f.area} onChange={(area) => set({ area })} />
                        </Field>
                        <Field label="Real width of that area, in inches" name="widthIn" error={errors.widthIn} optional
                               hint="Measure the marked part of the wall. With it, every painting is shown to scale; without it, paintings are fitted into the area.">
                            <input type="number" min="1" max="1200" step="0.5" value={f.widthIn} onChange={(e) => set({ widthIn: e.target.value })} className="adm-input" style={{ maxWidth: 180 }} />
                        </Field>
                    </>
                )}
                <Field label="Name" name="label" error={errors.label} required hint="Shown under the thumbnail, e.g. Living room.">
                    <input type="text" value={f.label} maxLength={120} onChange={(e) => set({ label: e.target.value })} className="adm-input" />
                </Field>
                <Field label="Note" name="note" error={errors.note} optional hint="Helps buyers judge the size, e.g. Above an 86-inch sofa.">
                    <input type="text" value={f.note || ''} maxLength={255} onChange={(e) => set({ note: e.target.value })} className="adm-input" />
                </Field>
                <Toggle checked={f.active} onChange={(v) => set({ active: v })} label="Show this room to buyers" description={errors.active || 'Hidden rooms stay here for later.'} name="active" />
                {preview && (
                    <div className="rm-preview">
                        <p className="rm-preview__label">Preview with a {EXAMPLE.w} × {EXAMPLE.h} in painting</p>
                        <RoomView room={preview} image={null} title="Example" widthIn={EXAMPLE.w} heightIn={EXAMPLE.h} frame="BLACK_WOOD" surface="canvas" />
                    </div>
                )}
            </div>
        </Drawer>
    );
}

export default function Rooms() {
    const { rooms: initialRooms = [], enabled: initialEnabled = true, maxActive = 12, minWidth = 1000 } = usePage().props;
    const [rooms, setRooms] = useState(initialRooms);
    const [enabled, setEnabled] = useState(initialEnabled);
    const [editing, setEditing] = useState(null);
    const [toastNode, toast] = useToast();
    const [confirmNode, confirm] = useConfirm();
    const active = rooms.filter((r) => r.status === 'ACTIVE');
    const hidden = rooms.filter((r) => r.status !== 'ACTIVE');

    useEffect(() => { setRooms(initialRooms); }, [initialRooms]);

    const toggleAll = (on) => {
        setEnabled(on);
        axios.post('/admin/api/rooms/settings', { enabled: on })
            .then(() => toast(on ? 'Room preview is on' : 'Room preview is off: buyers see the artwork on its own'))
            .catch((e) => { setEnabled(!on); toast(errorText(e), 'error'); });
    };
    const moveRoom = (r, d) => {
        const ids = active.map((x) => x.id);
        const i = ids.indexOf(r.id);
        const j = i + d;
        if (j < 0 || j >= ids.length) return;
        [ids[i], ids[j]] = [ids[j], ids[i]];
        axios.post('/admin/api/rooms/order', { ids: [...ids, ...hidden.map((x) => x.id)] }).then((x) => setRooms(x.data.rooms)).catch((e) => toast(errorText(e), 'error'));
    };
    const remove = (r) => confirm({ title: `Delete ${r.label}?`, body: 'Buyers stop seeing this room. The photo file stays in storage.', confirmLabel: 'Delete room', danger: true })
        .then((ok) => ok && axios.post(`/admin/api/rooms/${r.id}/delete`).then((x) => { setRooms(x.data.rooms); toast(`${r.label} deleted`); }).catch((e) => toast(errorText(e), 'error')));
    const edit = (r) => setEditing({ id: r.id, label: r.label, note: r.note || '', widthIn: r.areaWidthIn === null || r.areaWidthIn === undefined ? '' : r.areaWidthIn,
        active: r.status === 'ACTIVE', area: r.area, photo: r.photo, file: null, width: r.width, height: r.height });

    const card = (r, i, list) => (
        <li key={r.id} className={`rm-card ${r.status !== 'ACTIVE' ? 'is-hidden' : ''}`} data-room-id={r.id}>
            <div className="rm-card__img">
                <img src={r.photo} alt="" loading="lazy" />
                <span className="rm-card__area" style={{ left: `${r.area.x}%`, top: `${r.area.y}%`, width: `${r.area.w}%`, height: `${r.area.h}%` }} aria-hidden="true"></span>
            </div>
            <div className="rm-card__body">
                <p className="rm-card__title"><b>{r.label}</b>{r.status === 'ACTIVE' ? <Badge tone="green" dot>Shown</Badge> : <Badge>Hidden</Badge>}</p>
                {r.note && <p className="adm-muted">{r.note}</p>}
                <p className="adm-muted rm-card__meta">{r.width} × {r.height} px · {r.areaWidthIn ? `to scale (area ${r.areaWidthIn} in wide)` : 'no scale: paintings are fitted'}</p>
                <div className="rm-card__actions">
                    <Button size="sm" icon="fa-pencil" onClick={() => edit(r)}>Edit</Button>
                    {r.status === 'ACTIVE' && <Button size="sm" icon="fa-arrow-up" onClick={() => moveRoom(r, -1)} disabled={i === 0} aria-label={`Move ${r.label} earlier`} />}
                    {r.status === 'ACTIVE' && <Button size="sm" icon="fa-arrow-down" onClick={() => moveRoom(r, 1)} disabled={i === list.length - 1} aria-label={`Move ${r.label} later`} />}
                    <Button size="sm" variant="ghost" icon="fa-trash" onClick={() => remove(r)} aria-label={`Delete ${r.label}`} />
                </div>
            </div>
        </li>
    );

    return (
        <AdminLayout title='Room Preview Setup' crumbs={[{ label: 'System parameters' }, { label: "Room Preview Setup" }]}>
            <PageHeader eyebrow="System parameters" title="Room Preview Setup"
                        subtitle="The rooms buyers see a painting in before they pay. The artwork on its own is always shown first; each room you show adds one more view."
                        actions={<Button variant="dark" icon="fa-plus" onClick={() => setEditing(blankForm())}>Add a room</Button>} />

            <Card>
                <Toggle checked={enabled} onChange={toggleAll} name="enabled" label="Show room photos on the purchase page"
                        description={enabled ? `${active.length} room${active.length === 1 ? '' : 's'} shown (up to ${maxActive}). Four or five work best.` : 'Off: buyers see the artwork on its own, with frames and sizes.'} />
            </Card>

            {rooms.length === 0 ? (
                <EmptyState icon="fa-picture-o" title="No rooms yet" action={<Button variant="dark" onClick={() => setEditing(blankForm())}>Add a room</Button>}>
                    Upload 4 to 5 photos of real walls: a living room, a bedroom, a study, an office. Buyers see the painting on its own until you do.
                </EmptyState>
            ) : (
                <>
                    <h2 className="rm-h">Shown to buyers, in this order</h2>
                    {active.length ? <ul className="rm-grid">{active.map((r, i) => card(r, i, active))}</ul> : <p className="adm-muted">No room is shown. Edit a room and switch on "Show this room to buyers".</p>}
                    {hidden.length > 0 && (<><h2 className="rm-h">Hidden</h2><ul className="rm-grid">{hidden.map((r, i) => card(r, i, hidden))}</ul></>)}
                </>
            )}

            {editing && <RoomEditor initial={editing} minWidth={minWidth} onClose={() => setEditing(null)} toast={toast}
                                    onSaved={(list) => { setRooms(list); setEditing(null); }} />}
            {confirmNode}
            {toastNode}
        </AdminLayout>
    );
}
