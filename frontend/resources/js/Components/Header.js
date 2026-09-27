import React from 'react';
import SiteHeader from '@/Components/Site/SiteHeader';

/**
 * Legacy entry point kept so older pages that still import "@/Components/Header"
 * automatically get the new ArtistikCity navigation.
 */
export default function Header() {
    return (
        <div className="ac">
            <SiteHeader announcement={false} />
        </div>
    );
}
