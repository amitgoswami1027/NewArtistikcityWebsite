import React from 'react';
import { Head, usePage } from '@inertiajs/inertia-react';
import SiteHeader from '@/Components/Site/SiteHeader';
import SiteFooter from '@/Components/Site/SiteFooter';
import SiteErrorBoundary from '@/Components/Site/SiteErrorBoundary';
import SignInSheet from '@/Components/Site/SignInSheet';
import UndoToast from '@/Components/Site/UndoToast';
import SlowVisit from '@/Components/Site/SlowVisit';
import CookieBanner from '@/Components/Site/CookieBanner';

export default function SiteLayout({ title, children, showCategories = false, activeMedium = null }) {
    const { flash } = usePage().props;
    return (
        <div className="ac">
            {title && <Head title={title} />}
            <SiteHeader showCategories={showCategories} activeMedium={activeMedium} />
            {flash && flash.message && (
                <div className="ac-wide" style={{ marginTop: 16 }}>
                    <div className="ac-alert ac-alert--ok">{flash.message}</div>
                </div>
            )}
            <main id="main"><SiteErrorBoundary>{children}</SiteErrorBoundary></main>
            <SiteFooter />
            <SignInSheet />
            <UndoToast />
            <SlowVisit />
            <CookieBanner />
        </div>
    );
}
