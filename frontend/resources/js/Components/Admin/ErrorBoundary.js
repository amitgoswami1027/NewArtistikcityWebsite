import React from 'react';

/** Keeps a rendering error in one admin screen from blanking the whole console. */
export default class ErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = { error: null };
    }

    static getDerivedStateFromError(error) {
        return { error };
    }

    componentDidCatch(error, info) {
        // eslint-disable-next-line no-console
        console.error('Admin screen crashed', error, info && info.componentStack);
    }

    render() {
        if (!this.state.error) return this.props.children;
        return (
            <div className="adm-crash" role="alert">
                <i className="fa fa-exclamation-triangle" aria-hidden="true"></i>
                <h2>{this.props.title || 'This screen ran into a problem'}</h2>
                <p>Your data is safe. Reload the page to try again; if it keeps happening, tell the developer what you clicked last.</p>
                <div className="adm-crash__actions">
                    <button type="button" className="adm-btn adm-btn--dark" onClick={() => window.location.reload()}>Reload page</button>
                    <a className="adm-btn adm-btn--line" href="/admin/dashboard">Go to the overview</a>
                </div>
                <details><summary>Technical details</summary><pre>{String(this.state.error && (this.state.error.stack || this.state.error))}</pre></details>
            </div>
        );
    }
}
