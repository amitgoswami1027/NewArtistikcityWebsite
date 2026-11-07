import React, { useState } from 'react';
import { Head, usePage } from '@inertiajs/inertia-react';
import Authenticated from '@/Layouts/Authenticated';
import OfferCard from '@/Components/Shop/OfferCard';

/** Account > Your offers: every offer the collector made, the artists' answers, and Pay / Accept / Withdraw. */
export default function Offers() {
    const props = usePage().props;
    const [offers, setOffers] = useState(props.offers || []);
    const replace = (o) => setOffers((list) => list.map((x) => (x.id === o.id ? o : x)));
    const live = offers.filter((o) => ['OPEN', 'COUNTERED', 'ACCEPTED'].includes(o.status));
    const past = offers.filter((o) => !['OPEN', 'COUNTERED', 'ACCEPTED'].includes(o.status));
    return (
        <Authenticated auth={props.auth} errors={props.errors}>
            <Head title="Your offers" />
            {offers.length === 0 ? (
                <div className="wl-empty" data-testid="offers-empty">
                    <i className="fa fa-handshake-o" aria-hidden="true"></i>
                    <h2>No offers yet</h2>
                    <p>On an original you love, choose “Make an offer” to suggest your price. The artist can accept, counter or decline.</p>
                    <a href="/marketplace" className="dm-btn dm-btn--ink">Browse originals</a>
                </div>
            ) : (
                <div className="of-list">
                    {live.length > 0 && (
                        <section aria-labelledby="of-live">
                            <h2 id="of-live" className="of-h">In progress</h2>
                            {live.map((o) => <OfferCard key={o.id} offer={o} role="BUYER" endpoint="/api/v1/offers" onChange={replace} serverNow={props.serverNow} />)}
                        </section>
                    )}
                    {past.length > 0 && (
                        <section aria-labelledby="of-past">
                            <h2 id="of-past" className="of-h">Earlier offers</h2>
                            {past.map((o) => <OfferCard key={o.id} offer={o} role="BUYER" endpoint="/api/v1/offers" onChange={replace} serverNow={props.serverNow} />)}
                        </section>
                    )}
                </div>
            )}
        </Authenticated>
    );
}
