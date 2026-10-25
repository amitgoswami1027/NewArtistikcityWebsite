import React, { useEffect, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import { errorText } from '@/Components/Shop/shop';

/*
 * The marketplace cart as shared client state. The cart itself lives on the server (shop_carts / shop_cart_items,
 * tied to the signed-in collector or the guest session), so it survives refreshes, new tabs and signing in. Every
 * change returns the whole re-priced cart, which replaces the local copy.
 *
 * The state is one module-level store, so every component that calls useCart() (the header bag, the drawer, the
 * purchase pages, the checkout) sees the same cart without needing a provider above it.
 *
 *   const cart = useCart();
 *   cart.add({ paintingId, format, size, finish, frame, qty })    // resolves with the new cart, opens the drawer
 *   cart.setQty(lineId, 2); cart.remove(lineId);
 *   cart.applyCoupon('WELCOME10'); cart.removeCoupon();
 *   cart.open() / cart.close(); cart.count; cart.data (lines + totals)
 */

const store = { data: null, isOpen: false, busy: false, error: null, serverNow: Date.now() };
const listeners = new Set();
let loading = null;

function set(patch) {
    Object.assign(store, patch);
    listeners.forEach((l) => l());
}

function accept(d) {
    set({ data: d, serverNow: (d && d.serverNow) || store.serverNow, error: null });
    return d;
}

function refresh(country) {
    if (loading) return loading;
    loading = axios.get('/api/v1/cart' + (country ? `?country=${encodeURIComponent(country)}` : ''))
        .then((r) => accept(r.data))
        .catch((e) => { set({ error: errorText(e, 'We couldn’t load your cart.') }); return null; })
        .finally(() => { loading = null; });
    return loading;
}

function run(promise) {
    set({ busy: true, error: null });
    return promise.then((r) => accept(r.data))
        .catch((e) => { set({ error: errorText(e) }); throw e; })
        .finally(() => set({ busy: false }));
}

const actions = {
    open: () => { set({ isOpen: true }); refresh(); },
    close: () => set({ isOpen: false }),
    refresh,
    clearError: () => set({ error: null }),
    add: (item, { openDrawer = true } = {}) => run(axios.post('/api/v1/cart/items', item)).then((d) => { if (openDrawer) set({ isOpen: true }); return d; }),
    setQty: (id, qty) => run(axios.patch(`/api/v1/cart/items/${id}`, { qty })),
    remove: (id) => run(axios.delete(`/api/v1/cart/items/${id}`)),
    applyCoupon: (code, country) => run(axios.post('/api/v1/cart/coupon', { code, country })),
    removeCoupon: () => run(axios.delete('/api/v1/cart/coupon')),
};

/**
 * Seeds the store with a cart the server already sent with the page (checkout). Called while that page renders,
 * so it doesn't notify other components; they re-render with the page anyway.
 */
export function seedCart(data) {
    if (data && data.lines) Object.assign(store, { data, serverNow: data.serverNow || store.serverNow, error: null });
}

export function useCart() {
    const [, force] = useState(0);
    useEffect(() => {
        const l = () => force((n) => n + 1);
        listeners.add(l);
        return () => { listeners.delete(l); };
    }, []);
    const page = usePage();
    const shared = page && page.props && page.props.cart;
    return {
        ...actions,
        data: store.data,
        busy: store.busy,
        error: store.error,
        serverNow: store.serverNow,
        isOpen: store.isOpen,
        count: store.data ? store.data.count : (shared && shared.count) || 0,
    };
}

/** Kept for pages that pass the cart they were rendered with. */
export function CartProvider({ children, initial = null }) {
    const [seeded] = useState(() => { if (initial) seedCart(initial); return true; });
    return seeded ? <>{children}</> : null;
}
