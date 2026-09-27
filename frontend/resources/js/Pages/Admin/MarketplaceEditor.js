import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import AdminLayout, { PageHeader } from '@/Components/Admin/AdminLayout';
import ListingWizard from '@/Components/Marketplace/ListingWizard';

export default function MarketplaceEditor() {
    const { admin = {}, painting, artists = [] } = usePage().props;
    const caps = admin.capabilities || [];
    const publisher = caps.includes('marketplace.edit');
    const [done, setDone] = useState(null);

    if (done) {
        return (
            <AdminLayout title="Marketplace">
                <div className="max-w-xl mx-auto mt-10 rounded-2xl border border-gray-200 bg-white p-10 text-center">
                    <span className="inline-flex w-14 h-14 rounded-full bg-green-100 text-green-700 items-center justify-center text-2xl"><i className="fa fa-check" aria-hidden="true"></i></span>
                    <h1 className="mt-4 text-2xl font-black text-ink">{done.message}</h1>
                    <div className="mt-6 flex flex-wrap justify-center gap-3">
                        <a href="/admin/dashboard/marketplace" className="inline-flex items-center h-11 px-5 rounded-full bg-ink text-white font-bold">Back to listings</a>
                        <a href={`/admin/dashboard/marketplace/${done.id}/edit`} className="inline-flex items-center h-11 px-5 rounded-full border border-gray-300 font-bold hover:border-ink">Keep editing</a>
                        <a href="/admin/dashboard/marketplace/new" className="inline-flex items-center h-11 px-5 rounded-full border border-gray-300 font-bold hover:border-ink">List another</a>
                    </div>
                </div>
            </AdminLayout>
        );
    }

    return (
        <AdminLayout title={painting ? 'Edit listing' : 'New listing'}>
            <nav className="text-sm text-gray-500 mb-3"><a href="/admin/dashboard/marketplace" className="hover:text-ink">Marketplace</a> <span className="mx-1">/</span> {painting ? painting.title : 'New listing'}</nav>
            <PageHeader title={painting ? `Edit “${painting.title}”` : 'List an original'}
                        subtitle={publisher ? 'Three short steps. Publish straight to the marketplace or keep it as a draft.' : 'Three short steps. A gallery moderator reviews your listing before it goes live.'}
                        actions={painting && <a href={`/marketplace/${painting.slug}`} target="_blank" rel="noopener" className="inline-flex items-center h-11 px-5 rounded-full border border-gray-300 bg-white font-bold hover:border-ink"><i className="fa fa-eye mr-2" aria-hidden="true"></i>Preview page</a>} />
            <ListingWizard painting={painting} mode={publisher ? 'publisher' : 'instructor'} artists={artists} artistName={admin.name}
                           defaults={{ artistAdminId: publisher ? '' : admin.id }}
                           endpoints={{ upload: '/admin/console/api/marketplace/images', create: '/admin/console/api/marketplace/paintings', update: (id) => `/admin/console/api/marketplace/paintings/${id}` }}
                           onSaved={setDone} />
        </AdminLayout>
    );
}
