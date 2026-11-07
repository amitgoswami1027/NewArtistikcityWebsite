import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import { CartLine, Totals } from '@/Components/Shop/CartDrawer';
import { useCart, seedCart } from '@/Components/Shop/CartStore';
import { inr, errorText } from '@/Components/Shop/shop';

/*
 * /cart: everything in the cart on one page. Quantities for prints, Save for later (to the wish list), Remove, a coupon,
 * the totals, and "Checkout" for the whole cart. Originals in the cart are held for their owner for 15 minutes (or until
 * the end of an accepted offer's payment window); a line that can't be bought any more is marked and blocks checkout
 * until it is removed.
 */
export default function Cart() {
    const { cart: initial, signedIn } = usePage().props;
    const [seeded] = useState(() => { seedCart(initial); return true; });
    const cart = useCart();
    const [code, setCode] = useState('');
    const [notice, setNotice] = useState(null);
    const data = (seeded && cart.data) || initial;
    const lines = data ? data.lines : [];
    const t = data && data.totals;
    const reserve = (l) => cart.add({ paintingId: l.paintingId, format: 'ORIGINAL', frame: l.frame, qty: 1 }, { openDrawer: false })
        .then(() => setNotice('It’s held for you again for 15 minutes.')).catch((e) => setNotice(errorText(e)));
    const save = (l) => (signedIn
        ? cart.saveForLater(l.id).then(() => setNotice(`“${l.title}” is saved in your wish list.`)).catch(() => {})
        : window.location.assign(`/login?return_to=${encodeURIComponent('/cart')}`));
    const applyCoupon = (e) => {
        e.preventDefault();
        if (!code.trim()) return;
        cart.applyCoupon(code.trim()).then(() => { setCode(''); setNotice('Code applied.'); }).catch(() => {});
    };
    const count = lines.reduce((n, l) => n + l.qty, 0);

    return (
        <SiteLayout title="Your cart">
            <div className="pz pz-cartpage">
                <div className="ac-wide">
                    <ul className="dm-crumbs">
                        <li><a href="/">Home</a></li>
                        <li><a href="/marketplace">Marketplace</a></li>
                        <li>Your cart</li>
                    </ul>
                    <header className="pz-head">
                        <h1>Your cart{count > 0 ? <span className="pz-head__count"> · {count} {count === 1 ? 'item' : 'items'}</span> : ''}</h1>
                    </header>

                    {lines.length === 0 ? (
                        <div className="pz-empty pz-empty--page" data-testid="cart-empty">
                            <div className="pz-empty__art" aria-hidden="true"><i className="fa fa-shopping-bag"></i></div>
                            <h2>Your cart is empty</h2>
                            <p>Originals, numbered canvases, posters and downloads you add will wait for you here.</p>
                            <a href="/marketplace" className="pz-btn pz-btn--ink">Browse the marketplace</a>
                        </div>
                    ) : (
                        <div className="pz-cartgrid">
                            <section aria-label="Items in your cart">
                                {(cart.error || notice) && <p className={`pz-note ${cart.error ? 'is-error' : ''}`} role="status">{cart.error || notice}</p>}
                                <ul className="pz-lines pz-lines--page" aria-busy={cart.busy ? 'true' : 'false'} data-testid="cart-lines">
                                    {lines.map((l) => (
                                        <CartLine key={l.id} line={l} serverNow={cart.serverNow || (data && data.serverNow)} busy={cart.busy}
                                                  onQty={(line, q) => cart.setQty(line.id, q).catch(() => {})}
                                                  onRemove={(line) => cart.remove(line.id).catch(() => {})} onReserve={reserve} onSave={save} />
                                    ))}
                                </ul>
                                <p className="pz-fine">An original is held for you for 15 minutes once it's in your cart; after that anyone can buy it. Prices are checked again at checkout.</p>
                            </section>
                            <aside className="pz-cartsum" aria-label="Order summary">
                                <h2>Summary</h2>
                                <form className="pz-coupon" onSubmit={applyCoupon}>
                                    <label htmlFor="cart-coupon" className="sr-only">Coupon code</label>
                                    <input id="cart-coupon" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="Coupon code" autoComplete="off" />
                                    <button type="submit" className="pz-btn pz-btn--line" disabled={cart.busy || !code.trim()}>Apply</button>
                                </form>
                                {t && t.coupon && (
                                    <p className="pz-note">Code <b>{t.coupon.code}</b>{t.coupon.error ? `: ${t.coupon.error}` : ' applied.'}{' '}
                                        <button type="button" className="pz-link pz-link--muted" onClick={() => cart.removeCoupon().catch(() => {})}>Remove</button></p>
                                )}
                                <Totals totals={t} />
                                {t && t.hasPrints && <p className="pz-fine">Shipping shown for delivery in India; worldwide rates apply at checkout.</p>}
                                <a href="/purchase/checkout?all=1" className={`pz-btn pz-btn--brand pz-btn--lg pz-btn--block ${t && t.blocked ? 'is-disabled' : ''}`} data-testid="cart-checkout"
                                   aria-disabled={t && t.blocked ? 'true' : undefined}
                                   onClick={(e) => { if (t && t.blocked) { e.preventDefault(); setNotice('Remove or fix the items marked in red first.'); } }}>
                                    <i className="fa fa-lock" aria-hidden="true"></i> Checkout · {inr(t ? t.total : 0)}
                                </a>
                                <a href="/marketplace" className="pz-btn pz-btn--ghost pz-btn--block">Keep browsing</a>
                            </aside>
                        </div>
                    )}
                </div>
            </div>
        </SiteLayout>
    );
}
