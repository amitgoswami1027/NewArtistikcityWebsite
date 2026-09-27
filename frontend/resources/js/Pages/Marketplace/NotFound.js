import React from 'react';
import SiteLayout from '@/Components/Site/SiteLayout';

export default function MarketplaceNotFound() {
    return (
        <SiteLayout title="Artwork not found">
            <div className="tw">
                <section className="ac-wide py-24 text-center max-w-2xl">
                    <p className="font-mono text-xs uppercase tracking-widest text-brand">Marketplace</p>
                    <h1 className="mt-3 text-4xl font-black tracking-tight text-ink">This artwork isn't on show</h1>
                    <p className="mt-3 text-lg text-gray-600">It may have been withdrawn by the artist, or the link is incomplete. Every piece in the marketplace is one of a kind, so here are others you might like.</p>
                    <div className="mt-8 flex flex-wrap justify-center gap-3">
                        <a href="/marketplace" className="inline-flex items-center h-12 px-6 rounded-full bg-ink text-white font-bold">Browse the marketplace</a>
                        <a href="/commission/step-1" className="inline-flex items-center h-12 px-6 rounded-full border border-gray-300 font-bold hover:border-ink">Commission a painting</a>
                    </div>
                </section>
            </div>
        </SiteLayout>
    );
}
