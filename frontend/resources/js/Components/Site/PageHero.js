import React from 'react';

export default function PageHero({ title, subtitle, crumbs = [], children }) {
    return (
        <section className="ac-pagehero">
            <div className="ac-container">
                {crumbs.length > 0 && (
                    <ul className="ac-crumbs">
                        {crumbs.map((c, i) => (
                            <li key={i}>{c.href ? <a href={c.href}>{c.label}</a> : <span>{c.label}</span>}</li>
                        ))}
                    </ul>
                )}
                <h1>{title}</h1>
                {subtitle && <p>{subtitle}</p>}
                {children}
            </div>
        </section>
    );
}
