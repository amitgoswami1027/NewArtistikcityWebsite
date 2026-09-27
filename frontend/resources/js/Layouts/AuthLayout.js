import React from 'react';
import { Head, usePage } from '@inertiajs/inertia-react';

const ART = [
    '/assets/images/portfolio-1.jpg', '/assets/images/portfolio-2.jpg', '/assets/images/portfolio-3.jpg',
    '/assets/images/portfolio-4.jpg', '/assets/images/portfolio-5.jpg', '/assets/images/portfolio-6.jpg',
    '/assets/images/portfolio-7.jpg', '/assets/images/portfolio-8.jpg', '/assets/images/feedback-image-1.jpg',
    '/assets/images/feedback-image-2.jpg', '/assets/images/feedback-image-3.jpg', '/assets/images/course1.jpg',
    '/assets/images/course2.jpg', '/assets/images/course3.jpg', '/assets/images/prog-1.jpg',
    '/assets/images/prog-2.jpg', '/assets/images/prog-3.jpg', '/assets/images/image2.jpg',
];

/**
 * Centred authentication card over a soft mosaic of student artwork,
 * with a slim top bar (logo + close) — used by log in, join and password pages.
 */
export default function AuthLayout({ title, children }) {
    const { flash } = usePage().props;
    return (
        <div className="ac">
            <Head title={title} />
            <div className="dm-auth">
                <div className="dm-auth__art" aria-hidden="true">
                    {ART.map((src) => <span key={src} style={{ backgroundImage: `url(${src})` }}></span>)}
                </div>
                <div className="dm-auth__top">
                    <a href={route('welcome')} className="ac-logo" aria-label="ArtistikCity home"><img src="/assets/images/logo.png" alt="ArtistikCity" /></a>
                    <a href={route('welcome')} className="dm-close" aria-label="Close and go back"><i className="fa fa-times"></i></a>
                </div>
                <main className="dm-auth__main">
                    <div className="dm-auth__card">
                        {flash && flash.message && <div className="ac-alert ac-alert--error" role="alert">{flash.message}</div>}
                        {children}
                    </div>
                </main>
            </div>
        </div>
    );
}
