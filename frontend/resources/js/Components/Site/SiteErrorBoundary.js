import React from 'react';

/**
 * Keeps a crashed component from blanking a public page or the checkout. The visitor sees what happened, that
 * nothing was lost (cart, holds and orders live on the server), and a way on.
 */
export default class SiteErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = { error: null };
    }

    static getDerivedStateFromError(error) {
        return { error };
    }

    componentDidCatch(error, info) {
        // eslint-disable-next-line no-console
        console.error('Page crashed', error, info && info.componentStack);
    }

    render() {
        if (!this.state.error) return this.props.children;
        const checkout = this.props.checkout;
        return (
            <div className="ac-wide ac-crash" role="alert">
                <i className="fa fa-exclamation-triangle" aria-hidden="true"></i>
                <h2>Something went wrong on this page</h2>
                <p>{checkout
                    ? 'Your cart and any piece you are holding are saved, and no payment was taken by this error. Reload to carry on where you were.'
                    : 'Nothing you saved is lost - your cart, wish list and orders are kept on your account. Reload to try again.'}</p>
                <div className="ac-crash__actions">
                    <button type="button" className="dm-btn dm-btn--ink" onClick={() => window.location.reload()}>Reload the page</button>
                    <a className="dm-btn dm-btn--ghost" href={checkout ? '/purchase/checkout' : '/'}>{checkout ? 'Back to checkout' : 'Go to the home page'}</a>
                </div>
            </div>
        );
    }
}
