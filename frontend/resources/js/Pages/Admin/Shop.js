import React, { useEffect, useMemo, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Card, Button, Badge, EmptyState, Segments, Drawer, Stat, useConfirm, money, plural } from '@/Components/Admin/kit';
import { Field, Toggle, ChoiceChips } from '@/Components/Admin/form';

/*
 * Sales > Art shop: cart orders (originals, canvas editions, posters, downloads), artist messages,
 * the print price list, coupons and per-listing shop settings.
 */

const STATUS = {
    PAID: ['Paid · to pack', 'pink'], PACKED: ['Packed', 'blue'], SHIPPED: ['Shipped', 'blue'], DELIVERED: ['Delivered', 'green'],
    REFUND_REQUIRED: ['Refund required', 'red'], REFUNDED: ['Refunded', 'grey'], CANCELLED: ['Cancelled', 'grey'], EXPIRED: ['Expired', 'grey'],
};
const NEXT = { PACKED: 'Mark packed', SHIPPED: 'Mark shipped', DELIVERED: 'Mark delivered', REFUNDED: 'Record full refund' };
const KIND = { CANVAS: 'Canvas prints (limited edition)', POSTER: 'Poster prints', DIGITAL: 'Digital download', FRAME: 'Frames (price per inch of perimeter)', SHIPPING: 'Shipping for prints (per order)' };
const FORMATS = [{ value: 'CANVAS', label: 'Canvas edition' }, { value: 'POSTER', label: 'Poster' }, { value: 'DIGITAL', label: 'Digital download' }];
const when = (ms) => (ms ? new Date(ms).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—');
const inr = (n) => money(n, 'INR');

/* ---------------------------------------------------------------- orders */

function OrderDrawer({ order, flow, can, onClose, onSaved, toast }) {
    const [courier, setCourier] = useState(order.courier || '');
    const [tracking, setTracking] = useState(order.tracking_number || '');
    const [notes, setNotes] = useState('');
    const [relist, setRelist] = useState(true);
    const [busy, setBusy] = useState(null);
    const next = (flow[order.status] || []).filter((s) => order.needs_shipping || !['PACKED', 'SHIPPED'].includes(s));
    const go = (status) => {
        setBusy(status);
        axios.post(`/admin/console/api/shop/orders/${order.order_id}`, { status, courier, tracking, notes, relist })
            .then((r) => { onSaved(r.data); toast('Order updated.'); })
            .catch((e) => toast(errorText(e), 'error')).finally(() => setBusy(null));
    };
    const settle = () => {
        setBusy('settle');
        axios.post(`/admin/console/api/shop/orders/${order.order_id}/refund-settled`, { notes })
            .then((r) => { onSaved(r.data); toast('Refund recorded.'); })
            .catch((e) => toast(errorText(e), 'error')).finally(() => setBusy(null));
    };
    const hasOriginal = order.items.some((i) => i.format === 'ORIGINAL' && i.status === 'OK');
    return (
        <Drawer open title={`Order ${order.order_id}`} subtitle={`${order.buyer_name} · ${when(order.paid_at || order.created_at)}`} onClose={onClose} size="lg">
            <div className="adm-stack">
                <div className="adm-inline"><Badge tone={(STATUS[order.status] || [])[1]}>{(STATUS[order.status] || [order.status])[0]}</Badge>
                    {Number(order.refund_due_inr) > 0 && <Badge tone="red">Refund due {inr(order.refund_due_inr)}</Badge>}
                    <span className="adm-muted">{order.gateway} · {order.currency} {order.amount}</span></div>
                <ul className="adm-list">
                    {order.items.map((i) => (
                        <li key={i.id} className="adm-list__row">
                            {i.image_url && <img src={i.image_url} alt="" className="adm-thumb" />}
                            <div className="adm-grow">
                                <b>{i.title}</b> <span className="adm-muted">by {i.artist_name}</span>
                                <div className="adm-small">{i.variant_label} · qty {i.quantity}{i.edition_no ? ` · edition no. ${i.edition_no}` : ''}</div>
                                {i.status !== 'OK' && <Badge tone={i.status === 'REFUND_REQUIRED' ? 'red' : 'grey'}>{i.status.replace('_', ' ').toLowerCase()}</Badge>}
                                {i.format === 'DIGITAL' && <div className="adm-small">Downloaded {i.download_count} time{i.download_count === 1 ? '' : 's'}</div>}
                            </div>
                            <b>{inr(i.line_total_inr)}</b>
                        </li>
                    ))}
                </ul>
                <dl className="adm-dl">
                    <dt>Subtotal</dt><dd>{inr(order.subtotal_inr)}</dd>
                    {Number(order.discount_inr) > 0 && <><dt>Coupon {order.coupon_code}</dt><dd>−{inr(order.discount_inr)}</dd></>}
                    <dt>Shipping</dt><dd>{inr(order.shipping_inr)}</dd>
                    <dt>Total</dt><dd><b>{inr(order.total_inr)}</b></dd>
                    <dt>Buyer</dt><dd>{order.buyer_name} · {order.buyer_email}{order.buyer_phone ? ` · ${order.buyer_phone}` : ''}</dd>
                    {order.needs_shipping && <><dt>Ship to</dt><dd>{order.address_line1}{order.address_line2 ? `, ${order.address_line2}` : ''}, {order.city} {order.postal_code}, {order.state ? `${order.state}, ` : ''}{order.country}</dd></>}
                    {order.transaction_id && <><dt>Payment ref</dt><dd><code>{order.transaction_id}</code></dd></>}
                    {order.staff_notes && <><dt>Notes</dt><dd>{order.staff_notes}</dd></>}
                </dl>
                {can.orders && (next.length > 0 || Number(order.refund_due_inr) > 0) && (
                    <div className="adm-stack">
                        {order.needs_shipping && next.includes('SHIPPED') && (
                            <div className="adm-grid2">
                                <Field label="Courier" name="courier"><input id="f-courier" className="adm-input" value={courier} onChange={(e) => setCourier(e.target.value)} /></Field>
                                <Field label="Tracking number" name="tracking"><input id="f-tracking" className="adm-input" value={tracking} onChange={(e) => setTracking(e.target.value)} /></Field>
                            </div>
                        )}
                        <Field label="Note for the team" name="notes" optional><textarea id="f-notes" className="adm-input" rows="2" value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
                        {next.includes('REFUNDED') && hasOriginal && (
                            <Toggle checked={relist} onChange={setRelist} label="Put the original back on sale after a full refund" description="Otherwise it goes to the archive." />
                        )}
                        <div className="adm-inline">
                            {next.map((s) => (
                                <Button key={s} variant={s === 'REFUNDED' ? 'dangerline' : 'dark'} busy={busy === s} onClick={() => go(s)}>{NEXT[s]}</Button>
                            ))}
                            {Number(order.refund_due_inr) > 0 && order.status !== 'REFUND_REQUIRED' && (
                                <Button variant="line" busy={busy === 'settle'} onClick={settle}>Partial refund paid ({inr(order.refund_due_inr)})</Button>
                            )}
                        </div>
                    </div>
                )}
            </div>
        </Drawer>
    );
}

function Orders({ orders, setOrders, flow, can, toast }) {
    const [filter, setFilter] = useState('open');
    const [open, setOpen] = useState(null);
    const counts = {
        open: orders.filter((o) => ['PAID', 'PACKED', 'REFUND_REQUIRED'].includes(o.status) || Number(o.refund_due_inr) > 0).length,
        shipped: orders.filter((o) => ['SHIPPED', 'DELIVERED'].includes(o.status)).length,
        all: orders.length,
    };
    const rows = orders.filter((o) => (filter === 'all' ? true : filter === 'open'
        ? ['PAID', 'PACKED', 'REFUND_REQUIRED'].includes(o.status) || Number(o.refund_due_inr) > 0 : ['SHIPPED', 'DELIVERED'].includes(o.status)));
    return (
        <Card title="Orders" subtitle="Paid cart orders. Digital-only orders are delivered automatically." flush
              actions={<Segments value={filter} onChange={setFilter} label="Orders" options={[
                  { value: 'open', label: 'To do', count: counts.open }, { value: 'shipped', label: 'Shipped', count: counts.shipped }, { value: 'all', label: 'All', count: counts.all }]} />}>
            {rows.length === 0 ? <EmptyState icon="fa-shopping-bag" title="No orders here">Paid orders from the marketplace cart appear here.</EmptyState> : (
                <div className="adm-table"><div className="adm-table__scroll"><table>
                    <thead><tr><th>Order</th><th>Buyer</th><th>Items</th><th className="is-right">Total</th><th>Status</th><th></th></tr></thead>
                    <tbody>
                        {rows.map((o) => (
                            <tr key={o.order_id} className="is-clickable" onClick={() => setOpen(o)}>
                                <td data-label="Order"><code>{o.order_id}</code><div className="adm-small adm-muted">{when(o.paid_at || o.created_at)}</div></td>
                                <td data-label="Buyer">{o.buyer_name}<div className="adm-small adm-muted">{o.country}</div></td>
                                <td data-label="Items">{o.items.map((i) => <div key={i.id} className="adm-small">{i.quantity > 1 ? `${i.quantity} × ` : ''}{i.title} · {i.format.toLowerCase()}</div>)}</td>
                                <td data-label="Total" className="is-right"><b>{inr(o.total_inr)}</b>{o.currency === 'USD' && <div className="adm-small adm-muted">${o.amount}</div>}</td>
                                <td data-label="Status"><Badge tone={(STATUS[o.status] || [])[1]}>{(STATUS[o.status] || [o.status])[0]}</Badge>
                                    {Number(o.refund_due_inr) > 0 && o.status !== 'REFUND_REQUIRED' && <div><Badge tone="red">Refund {inr(o.refund_due_inr)}</Badge></div>}</td>
                                <td className="is-right"><Button size="sm" onClick={() => setOpen(o)}>Open</Button></td>
                            </tr>
                        ))}
                    </tbody>
                </table></div></div>
            )}
            {open && <OrderDrawer order={open} flow={flow} can={can} toast={toast} onClose={() => setOpen(null)}
                                  onSaved={(o) => { setOrders(orders.map((x) => (x.order_id === o.order_id ? o : x))); setOpen(o); }} />}
        </Card>
    );
}

/* ---------------------------------------------------------------- messages */

function Messages({ threads, setThreads, initial, toast }) {
    const [sel, setSel] = useState(initial ? Number(initial) : (threads[0] && threads[0].id) || null);
    const [msgs, setMsgs] = useState([]);
    const [status, setStatus] = useState('OPEN');
    const [reply, setReply] = useState('');
    const [busy, setBusy] = useState(false);
    const thread = threads.find((t) => t.id === sel);
    useEffect(() => {
        if (!sel) return;
        axios.get(`/admin/console/api/shop/threads/${sel}`).then((r) => {
            setMsgs(r.data.messages); setStatus(r.data.status);
            setThreads((ts) => ts.map((t) => (t.id === sel ? { ...t, staff_unread: 0 } : t)));
        }).catch((e) => toast(errorText(e), 'error'));
    }, [sel]); // eslint-disable-line react-hooks/exhaustive-deps
    const send = (e) => {
        e.preventDefault();
        if (reply.trim().length < 2) return;
        setBusy(true);
        axios.post(`/admin/console/api/shop/threads/${sel}/reply`, { body: reply })
            .then((r) => { setMsgs(r.data.messages); setReply(''); toast(r.data.message); setThreads((ts) => ts.map((t) => (t.id === sel ? { ...t, last_body: reply, last_sender: 'ARTIST', last_message_at: Date.now() } : t))); })
            .catch((x) => toast(errorText(x), 'error')).finally(() => setBusy(false));
    };
    const flip = () => {
        const to = status === 'OPEN' ? 'CLOSED' : 'OPEN';
        axios.post(`/admin/console/api/shop/threads/${sel}/status`, { status: to }).then(() => { setStatus(to); setThreads((ts) => ts.map((t) => (t.id === sel ? { ...t, status: to } : t))); });
    };
    if (threads.length === 0) return <Card><EmptyState icon="fa-comments-o" title="No messages yet">Questions collectors send from “Meet the artist” land here.</EmptyState></Card>;
    return (
        <div className="pzadm-inbox">
            <Card title="Conversations" flush>
                <ul className="pzadm-threads">
                    {threads.map((t) => (
                        <li key={t.id}>
                            <button type="button" className={`${t.id === sel ? 'is-on' : ''} ${t.staff_unread > 0 ? 'is-unread' : ''}`} onClick={() => setSel(t.id)}>
                                {t.image_url && <img src={t.image_url} alt="" />}
                                <span>
                                    <b>{t.buyer_name}{t.guest ? ' (guest)' : ''}</b>
                                    <small>{t.title}</small>
                                    <em>{t.last_sender === 'ARTIST' ? 'You: ' : ''}{t.last_body}</em>
                                </span>
                                {t.staff_unread > 0 ? <i className="pzadm-dot">{t.staff_unread}</i> : t.status === 'CLOSED' && <Badge>closed</Badge>}
                            </button>
                        </li>
                    ))}
                </ul>
            </Card>
            {thread && (
                <Card title={thread.buyer_name} subtitle={`${thread.buyer_email} · about “${thread.title}” by ${thread.artist_name}`}
                      actions={<><Button as="a" size="sm" href={`/marketplace/${thread.slug}`} target="_blank" rel="noopener" icon="fa-external-link">Artwork</Button>
                          <Button size="sm" onClick={flip}>{status === 'OPEN' ? 'Close' : 'Reopen'}</Button></>}>
                    {thread.context && <p className="adm-small adm-muted"><i className="fa fa-tag" aria-hidden="true"></i> Looking at: {thread.context}</p>}
                    <ol className="pzadm-msgs">
                        {msgs.map((m) => (
                            <li key={m.id} className={m.sender === 'ARTIST' ? 'is-me' : ''}>
                                <p>{m.body}</p><small>{m.sender === 'ARTIST' ? m.by : thread.buyer_name} · {when(m.at)}</small>
                            </li>
                        ))}
                    </ol>
                    <form onSubmit={send} className="adm-stack">
                        <Field label="Reply" name="reply" hint="The collector sees it in the chat on the artwork page and gets it by e-mail.">
                            <textarea id="f-reply" className="adm-input" rows="3" value={reply} onChange={(e) => setReply(e.target.value)} maxLength={2000} />
                        </Field>
                        <div><Button type="submit" variant="brand" busy={busy} icon="fa-paper-plane">Send reply</Button></div>
                    </form>
                </Card>
            )}
        </div>
    );
}

/* ---------------------------------------------------------------- prices */

function Prices({ prices, setPrices, can, toast }) {
    const [rows, setRows] = useState(prices);
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const dirty = JSON.stringify(rows) !== JSON.stringify(prices);
    const set = (id, k, v) => setRows(rows.map((r) => (r.id === id ? { ...r, [k]: v } : r)));
    const save = () => {
        setBusy(true); setErrors({});
        axios.put('/admin/console/api/shop/prices', { rows: rows.map((r) => ({ ...r, is_active: !!r.is_active })) })
            .then((r) => { setPrices(r.data.prices); setRows(r.data.prices); toast(r.data.message); })
            .catch((e) => { setErrors((e.response && e.response.data && e.response.data.errors) || {}); toast(errorText(e), 'error'); })
            .finally(() => setBusy(false));
    };
    const kinds = Object.keys(KIND);
    return (
        <div className="adm-stack">
            {kinds.map((k) => (
                <Card key={k} title={KIND[k]} flush>
                    <div className="adm-table"><div className="adm-table__scroll"><table>
                        <thead><tr><th>Name</th>{['CANVAS', 'POSTER'].includes(k) && <th>Long side (in)</th>}<th>{k === 'FRAME' ? '₹ per inch' : 'Price ₹'}</th>{k === 'CANVAS' && <th>Stretching ₹</th>}<th>On sale</th></tr></thead>
                        <tbody>
                            {rows.filter((r) => r.kind === k).map((r) => (
                                <tr key={r.id}>
                                    <td data-label="Name"><input className="adm-input" value={r.label} disabled={!can.prices} onChange={(e) => set(r.id, 'label', e.target.value)} aria-label={`${r.code} name`} />
                                        {errors[`${r.id}.label`] && <p className="adm-field__error">{errors[`${r.id}.label`]}</p>}</td>
                                    {['CANVAS', 'POSTER'].includes(k) && <td data-label="Long side"><input className="adm-input" type="number" min="4" max="120" step="0.5" value={r.long_side_in || ''} disabled={!can.prices} onChange={(e) => set(r.id, 'long_side_in', e.target.value)} aria-label={`${r.code} long side`} />
                                        {errors[`${r.id}.long_side_in`] && <p className="adm-field__error">{errors[`${r.id}.long_side_in`]}</p>}</td>}
                                    <td data-label="Price"><input className="adm-input" type="number" min="0" step="1" value={r.price_inr} disabled={!can.prices} onChange={(e) => set(r.id, 'price_inr', e.target.value)} aria-label={`${r.code} price`} />
                                        {errors[`${r.id}.price_inr`] && <p className="adm-field__error">{errors[`${r.id}.price_inr`]}</p>}</td>
                                    {k === 'CANVAS' && <td data-label="Stretching"><input className="adm-input" type="number" min="0" step="1" value={r.extra_inr} disabled={!can.prices} onChange={(e) => set(r.id, 'extra_inr', e.target.value)} aria-label={`${r.code} stretching price`} /></td>}
                                    <td data-label="On sale"><Toggle checked={!!r.is_active} disabled={!can.prices} onChange={(v) => set(r.id, 'is_active', v)} label={r.is_active ? 'Yes' : 'No'} /></td>
                                </tr>
                            ))}
                        </tbody>
                    </table></div></div>
                </Card>
            ))}
            {can.prices ? (
                <div className="adm-savebar"><span className="adm-savebar__status">{dirty ? 'Unsaved changes' : 'All changes saved'}</span>
                    <div className="adm-savebar__actions"><Button onClick={() => setRows(prices)} disabled={!dirty}>Discard</Button><Button variant="brand" busy={busy} disabled={!dirty} onClick={save}>Save price list</Button></div></div>
            ) : <p className="adm-muted">Only a super admin can change prices.</p>}
        </div>
    );
}

/* ---------------------------------------------------------------- coupons */

const BLANK = { code: '', description: '', discount_type: 'PERCENT', discount_value: '', painting_id: '', artist_name: '', min_subtotal: '', max_uses: '', starts_at: '', expires_at: '', is_active: true };

function CouponDrawer({ coupon, listings, onClose, onSaved, toast }) {
    const [f, setF] = useState({ ...BLANK, ...Object.fromEntries(Object.entries(coupon || {}).map(([k, v]) => [k, v === null ? '' : v])) });
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const set = (k, v) => setF((x) => ({ ...x, [k]: v }));
    const artists = useMemo(() => Array.from(new Set(listings.map((l) => l.artist_name))).sort(), [listings]);
    const save = () => {
        setBusy(true); setErrors({});
        const body = { ...f, painting_id: f.painting_id ? Number(f.painting_id) : null };
        (coupon ? axios.put(`/admin/console/api/shop/coupons/${coupon.id}`, body) : axios.post('/admin/console/api/shop/coupons', body))
            .then((r) => { onSaved(r.data.coupons); toast(r.data.message); onClose(); })
            .catch((e) => { setErrors((e.response && e.response.data && e.response.data.errors) || {}); toast(errorText(e), 'error'); })
            .finally(() => setBusy(false));
    };
    const input = (k, props = {}) => <input id={`f-${k}`} className="adm-input" value={f[k]} onChange={(e) => set(k, e.target.value)} {...props} />;
    return (
        <Drawer open title={coupon ? `Coupon ${coupon.code}` : 'New coupon'} onClose={onClose}
                footer={<><Button onClick={onClose}>Cancel</Button><Button variant="brand" busy={busy} onClick={save}>Save coupon</Button></>}>
            <div className="adm-stack">
                <div className="adm-grid2">
                    <Field label="Code" name="code" error={errors.code} required hint="Collectors type this at checkout.">{input('code', { onChange: (e) => set('code', e.target.value.toUpperCase()), maxLength: 40 })}</Field>
                    <Field label="Discount" name="discount_value" error={errors.discount_value} required>
                        <div className="adm-inline">
                            <select className="adm-input" value={f.discount_type} onChange={(e) => set('discount_type', e.target.value)} aria-label="Discount type"><option value="PERCENT">% off</option><option value="FLAT">₹ off</option></select>
                            {input('discount_value', { type: 'number', min: 1, step: 1 })}
                        </div>
                    </Field>
                </div>
                <Field label="Description" name="description" error={errors.description} optional hint="Shown to the collector when the code is applied.">{input('description', { maxLength: 255 })}</Field>
                <div className="adm-grid2">
                    <Field label="Only for this artwork" name="painting_id" error={errors.painting_id} optional>
                        <select id="f-painting_id" className="adm-input" value={f.painting_id} onChange={(e) => set('painting_id', e.target.value)}>
                            <option value="">Any artwork</option>{listings.map((l) => <option key={l.id} value={l.id}>{l.title}</option>)}
                        </select>
                    </Field>
                    <Field label="Only for this artist" name="artist_name" optional>
                        <select id="f-artist_name" className="adm-input" value={f.artist_name} onChange={(e) => set('artist_name', e.target.value)}>
                            <option value="">Any artist</option>{artists.map((a) => <option key={a} value={a}>{a}</option>)}
                        </select>
                    </Field>
                    <Field label="Minimum order (₹)" name="min_subtotal" error={errors.min_subtotal} optional>{input('min_subtotal', { type: 'number', min: 0 })}</Field>
                    <Field label="Maximum uses" name="max_uses" error={errors.max_uses} optional hint="Leave empty for unlimited.">{input('max_uses', { type: 'number', min: 1 })}</Field>
                    <Field label="Starts" name="starts_at" error={errors.starts_at} optional>{input('starts_at', { type: 'date' })}</Field>
                    <Field label="Ends" name="expires_at" error={errors.expires_at} optional>{input('expires_at', { type: 'date' })}</Field>
                </div>
                <Toggle checked={!!f.is_active} onChange={(v) => set('is_active', v)} label="Active" description="Switch off to stop new orders from using it." />
            </div>
        </Drawer>
    );
}

function Coupons({ coupons, setCoupons, listings, can, toast }) {
    const [edit, setEdit] = useState(undefined);
    const [dialog, confirm] = useConfirm();
    const del = async (c) => {
        if (!(await confirm({ title: `Delete ${c.code}?`, body: 'Collectors will no longer be able to use it.', confirmLabel: 'Delete', danger: true }))) return;
        axios.delete(`/admin/console/api/shop/coupons/${c.id}`).then((r) => { setCoupons(r.data.coupons); toast(r.data.message); }).catch((e) => toast(errorText(e), 'error'));
    };
    return (
        <Card title="Artist coupon codes" subtitle="Percent or flat discounts, optionally limited to one artwork or artist." flush
              actions={can.prices && <Button variant="brand" icon="fa-plus" onClick={() => setEdit(null)}>New coupon</Button>}>
            {coupons.length === 0 ? <EmptyState icon="fa-tag" title="No coupons yet" /> : (
                <div className="adm-table"><div className="adm-table__scroll"><table>
                    <thead><tr><th>Code</th><th>Discount</th><th>Applies to</th><th>Used</th><th>Valid</th><th>Status</th><th></th></tr></thead>
                    <tbody>
                        {coupons.map((c) => (
                            <tr key={c.id}>
                                <td data-label="Code"><code>{c.code}</code>{c.description && <div className="adm-small adm-muted">{c.description}</div>}</td>
                                <td data-label="Discount">{c.discount_type === 'PERCENT' ? `${Number(c.discount_value)}%` : inr(c.discount_value)}{c.min_subtotal ? <div className="adm-small adm-muted">min {inr(c.min_subtotal)}</div> : null}</td>
                                <td data-label="Applies to">{c.painting_title || c.artist_name || 'Everything'}</td>
                                <td data-label="Used">{c.used_count}{c.max_uses ? ` / ${c.max_uses}` : ''}</td>
                                <td data-label="Valid">{c.starts_at || c.expires_at ? `${c.starts_at || '…'} → ${c.expires_at || '…'}` : 'Always'}</td>
                                <td data-label="Status"><Badge tone={c.is_active ? 'green' : 'grey'}>{c.is_active ? 'Active' : 'Off'}</Badge></td>
                                <td className="is-right">{can.prices && <><Button size="sm" onClick={() => setEdit(c)}>Edit</Button> <Button size="sm" variant="dangerline" onClick={() => del(c)}>Delete</Button></>}</td>
                            </tr>
                        ))}
                    </tbody>
                </table></div></div>
            )}
            {edit !== undefined && <CouponDrawer coupon={edit} listings={listings} toast={toast} onClose={() => setEdit(undefined)} onSaved={setCoupons} />}
            {dialog}
        </Card>
    );
}

/* ---------------------------------------------------------------- listings */

function ListingDrawer({ l, onClose, onSaved, toast }) {
    const [f, setF] = useState({ formats: l.formats, edition_size: l.edition_size, artist_whatsapp: l.artist_whatsapp || '', artist_instagram: l.artist_instagram || '', artist_email: l.artist_email || '' });
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const [upload, setUpload] = useState(null);
    const set = (k, v) => setF((x) => ({ ...x, [k]: v }));
    const save = () => {
        setBusy(true); setErrors({});
        axios.put(`/admin/console/api/shop/listings/${l.id}`, f)
            .then((r) => { onSaved(r.data.listings); toast(r.data.message); onClose(); })
            .catch((e) => { setErrors((e.response && e.response.data && e.response.data.errors) || {}); toast(errorText(e), 'error'); })
            .finally(() => setBusy(false));
    };
    const sendFile = (file) => {
        if (!file) return;
        const fd = new FormData();
        fd.append('file', file);
        setUpload(0);
        axios.post(`/admin/console/api/shop/listings/${l.id}/hires`, fd, { onUploadProgress: (p) => setUpload(Math.round((p.loaded / (p.total || 1)) * 100)) })
            .then((r) => { onSaved(r.data.listings); toast(r.data.message); setUpload(null); })
            .catch((e) => { setUpload(null); setErrors((e.response && e.response.data && e.response.data.errors) || {}); toast(errorText(e), 'error'); });
    };
    return (
        <Drawer open title={l.title} subtitle={`${l.artist_name} · ${l.height_inches} × ${l.width_inches} in`} onClose={onClose}
                footer={<><Button onClick={onClose}>Cancel</Button><Button variant="brand" busy={busy} onClick={save}>Save</Button></>}>
            <div className="adm-stack">
                <Field label="Sell as well as the original" name="formats" error={errors.formats} hint="Prices come from the price list.">
                    <ChoiceChips name="formats" options={FORMATS} value={f.formats} onChange={(v) => set('formats', v)} />
                </Field>
                <Field label="Canvas edition size" name="edition_size" error={errors.edition_size} hint={`${l.editions_sold} sold so far.`}>
                    <input id="f-edition_size" className="adm-input" type="number" min={Math.max(1, l.editions_sold)} max="1000" value={f.edition_size} onChange={(e) => set('edition_size', e.target.value)} />
                </Field>
                <Field label="High-resolution file (digital downloads)" name="file" error={errors.file}
                       hint={l.has_hires ? `Current file: ${l.hires_name}. Uploading replaces it.` : 'JPEG, PNG, TIFF or PDF up to 200 MB. Kept private: buyers get an expiring link.'}>
                    <input id="f-file" type="file" accept=".jpg,.jpeg,.png,.tif,.tiff,.pdf" onChange={(e) => sendFile(e.target.files[0])} disabled={upload !== null} />
                    {upload !== null && <div className="adm-progress" role="progressbar" aria-valuenow={upload} aria-valuemin="0" aria-valuemax="100"><span style={{ width: `${upload}%` }}></span></div>}
                </Field>
                <p className="adm-small adm-muted">“Meet the artist” links. Leave empty to use the studio’s contact details.</p>
                <div className="adm-grid2">
                    <Field label="WhatsApp number" name="artist_whatsapp" error={errors.artist_whatsapp} optional><input id="f-artist_whatsapp" className="adm-input" value={f.artist_whatsapp} onChange={(e) => set('artist_whatsapp', e.target.value)} placeholder="+91 98765 43210" /></Field>
                    <Field label="Instagram handle" name="artist_instagram" error={errors.artist_instagram} optional><input id="f-artist_instagram" className="adm-input" value={f.artist_instagram} onChange={(e) => set('artist_instagram', e.target.value)} placeholder="@artistname" /></Field>
                </div>
                <Field label="E-mail for questions" name="artist_email" error={errors.artist_email} optional hint="Also gets a copy of every chat message."><input id="f-artist_email" className="adm-input" type="email" value={f.artist_email} onChange={(e) => set('artist_email', e.target.value)} /></Field>
            </div>
        </Drawer>
    );
}

function Listings({ listings, setListings, can, toast }) {
    const [edit, setEdit] = useState(null);
    return (
        <Card title="Listings" subtitle="What each artwork sells besides the original, and who collectors can contact." flush>
            <div className="adm-table"><div className="adm-table__scroll"><table>
                <thead><tr><th>Artwork</th><th>Formats</th><th>Canvas edition</th><th>Download file</th><th></th></tr></thead>
                <tbody>
                    {listings.map((l) => (
                        <tr key={l.id}>
                            <td data-label="Artwork"><div className="adm-cell">{l.image_url && <img src={l.image_url} alt="" className="adm-thumb" />}<div className="adm-cell__text"><b>{l.title}</b><span className="adm-small adm-muted">{l.artist_name} · {l.stock_status.toLowerCase()}</span></div></div></td>
                            <td data-label="Formats">{l.formats.length ? l.formats.map((x) => <Badge key={x} tone="blue">{x.toLowerCase()}</Badge>) : <span className="adm-muted">Original only</span>}</td>
                            <td data-label="Edition">{l.editions_sold} / {l.edition_size}</td>
                            <td data-label="File">{l.has_hires ? <Badge tone="green">Uploaded</Badge> : <span className="adm-muted">—</span>}</td>
                            <td className="is-right">{can.listings && <Button size="sm" onClick={() => setEdit(l)}>Edit</Button>}</td>
                        </tr>
                    ))}
                </tbody>
            </table></div></div>
            {edit && <ListingDrawer l={edit} toast={toast} onClose={() => setEdit(null)} onSaved={(ls) => { setListings(ls); const again = ls.find((x) => x.id === edit.id); if (again) setEdit(again); }} />}
        </Card>
    );
}

/* ---------------------------------------------------------------- page */

export default function AdminShop() {
    const props = usePage().props;
    const [tab, setTab] = useState(props.tab || 'orders');
    const [orders, setOrders] = useState(props.orders);
    const [threads, setThreads] = useState(props.threads);
    const [prices, setPrices] = useState(props.prices);
    const [coupons, setCoupons] = useState(props.coupons);
    const [listings, setListings] = useState(props.listings);
    const [toastNode, toast] = useToast();
    const unread = threads.filter((t) => t.staff_unread > 0).length;
    const toDo = orders.filter((o) => ['PAID', 'PACKED', 'REFUND_REQUIRED'].includes(o.status) || Number(o.refund_due_inr) > 0).length;
    const revenue = orders.filter((o) => ['PAID', 'PACKED', 'SHIPPED', 'DELIVERED'].includes(o.status)).reduce((s, o) => s + Number(o.total_inr) - Number(o.refund_due_inr || 0), 0);
    const pick = (t) => { setTab(t); try { window.history.replaceState(null, '', `?tab=${t}`); } catch (e) { /* ignore */ } };
    const tabs = [
        { value: 'orders', label: 'Orders', count: toDo }, { value: 'messages', label: 'Artist messages', count: unread },
        { value: 'prices', label: 'Price list' }, { value: 'coupons', label: 'Coupons' }, { value: 'listings', label: 'Listings' },
    ];
    return (
        <AdminLayout title="Art shop" crumbs={[{ label: 'Sales' }, { label: 'Art shop' }]}>
            <PageHeader eyebrow="Sales" title="Art shop" subtitle="Cart orders, prints and downloads, coupons, and collectors' questions for the artists." />
            <div className="adm-stats">
                <Stat label="Orders to handle" value={toDo} icon="fa-shopping-bag" tone={toDo ? 'pink' : undefined} />
                <Stat label="Unread messages" value={unread} icon="fa-comments-o" tone={unread ? 'pink' : undefined} />
                <Stat label="Shop revenue" value={inr(revenue)} icon="fa-inr" />
                <Stat label="Listings selling prints" value={plural(listings.filter((l) => l.formats.length).length, 'artwork')} icon="fa-th-large" />
            </div>
            <div className="pzadm-tabs"><Segments value={tab} onChange={pick} label="Art shop sections" options={tabs} /></div>
            {tab === 'orders' && <Orders orders={orders} setOrders={setOrders} flow={props.orderFlow} can={props.can} toast={toast} />}
            {tab === 'messages' && <Messages threads={threads} setThreads={setThreads} initial={props.openThread} toast={toast} />}
            {tab === 'prices' && <Prices prices={prices} setPrices={setPrices} can={props.can} toast={toast} />}
            {tab === 'coupons' && <Coupons coupons={coupons} setCoupons={setCoupons} listings={listings} can={props.can} toast={toast} />}
            {tab === 'listings' && <Listings listings={listings} setListings={setListings} can={props.can} toast={toast} />}
            {toastNode}
        </AdminLayout>
    );
}
