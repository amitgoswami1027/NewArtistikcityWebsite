import React from 'react';

/**
 * On the log-in / join page when the visitor came from "Buy now" or "Enroll": what they are buying, and that they
 * come straight back to finish (PurchaseIntent.java names it).
 */
export default function PurchaseIntentBanner({ intent, mode = 'login' }) {
    if (!intent) return null;
    return (
        <div className="ac-intent" role="status" data-testid="purchase-intent">
            {intent.image ? <img src={intent.image} alt="" className="ac-intent__img" /> : (
                <span className="ac-intent__icon" aria-hidden="true"><i className={`fa ${intent.kind === 'artwork' ? 'fa-picture-o' : intent.kind === 'commission' ? 'fa-paint-brush' : 'fa-graduation-cap'}`}></i></span>
            )}
            <div>
                <b>{mode === 'join' ? 'Join for free to finish buying' : 'Log in to finish buying'}</b>
                <span>{intent.title}</span>
                <small>You come straight back to checkout; nothing you chose is lost.</small>
            </div>
        </div>
    );
}
