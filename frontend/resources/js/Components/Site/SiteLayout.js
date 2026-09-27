import React from 'react';
import { Head, usePage } from '@inertiajs/inertia-react';
import SiteHeader from '@/Components/Site/SiteHeader';
import SiteFooter from '@/Components/Site/SiteFooter';

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
            <main>{children}</main>
            <SiteFooter />
        </div>
    );
}
