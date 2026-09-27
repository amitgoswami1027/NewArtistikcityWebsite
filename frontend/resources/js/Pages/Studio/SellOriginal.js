import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import StudioLayout from '@/Components/Studio/StudioLayout';
import ListingWizard from '@/Components/Marketplace/ListingWizard';

export default function SellOriginal() {
    const { submission, painting, artistName, blocked } = usePage().props;
    const [done, setDone] = useState(null);

    return (
        <StudioLayout title="Sell an original" active="/dashboard/portfolio" stage={6}
                      subtitle="List approved coursework in the ArtistikCity Marketplace as a 1-of-1 original. A moderator checks every listing before it goes live."
                      actions={<a href="/dashboard/portfolio" className="dm-btn dm-btn--line">Back to portfolio</a>}>
            {blocked ? (
                <div className="rounded-2xl border border-yellow-200 bg-yellow-50 p-8 text-yellow-900">
                    <p className="text-lg font-bold">{blocked}</p>
                    <a href="/dashboard/portfolio" className="mt-4 inline-flex h-11 items-center px-5 rounded-full bg-ink text-white font-bold">Back to portfolio</a>
                </div>
            ) : done ? (
                <div className="max-w-xl rounded-2xl border border-gray-200 bg-white p-10 text-center mx-auto">
                    <span className="inline-flex w-14 h-14 rounded-full bg-green-100 text-green-700 items-center justify-center text-2xl"><i className={`fa ${done.status === 'PENDING_REVIEW' ? 'fa-paper-plane' : 'fa-check'}`} aria-hidden="true"></i></span>
                    <h2 className="mt-4 text-2xl font-black text-ink">{done.status === 'PENDING_REVIEW' ? 'Sent for review' : 'Draft saved'}</h2>
                    <p className="mt-2 text-gray-600">{done.message}</p>
                    <div className="mt-6 flex flex-wrap justify-center gap-3">
                        <a href="/dashboard/portfolio" className="inline-flex h-11 items-center px-5 rounded-full bg-ink text-white font-bold">Back to portfolio</a>
                        <a href={`/dashboard/portfolio/sell/${submission.id}`} className="inline-flex h-11 items-center px-5 rounded-full border border-gray-300 font-bold hover:border-ink">Keep editing</a>
                    </div>
                </div>
            ) : painting && ['RESERVED', 'SOLD'].includes(painting.raw_status) ? (
                <div className="rounded-2xl border border-gray-200 bg-white p-8">
                    <p className="text-lg font-bold text-ink">{painting.raw_status === 'SOLD' ? 'Congratulations, this original has sold!' : 'A collector is checking out right now.'}</p>
                    <p className="mt-2 text-gray-600">{painting.raw_status === 'SOLD' ? 'The studio will pack and ship it. Sold listings can\'t be edited.' : 'It\'s held for up to 15 minutes. You can edit it again if the hold ends without a sale.'}</p>
                    <a href={`/marketplace/${painting.slug}`} className="mt-4 inline-flex h-11 items-center px-5 rounded-full bg-ink text-white font-bold">View in marketplace</a>
                </div>
            ) : (
                <ListingWizard painting={painting} mode="student" artistName={artistName}
                               defaults={{ title: submission.title, description: submission.description || '', imageUrl: submission.file_url, year: new Date(String(submission.created_at).replace(' ', 'T')).getFullYear() || undefined }}
                               extra={{ submissionId: submission.id }}
                               endpoints={{ upload: '/api/v1/marketplace/student/images', create: '/api/v1/marketplace/student/paintings', update: (id) => `/api/v1/marketplace/student/paintings/${id}` }}
                               onSaved={setDone} />
            )}
        </StudioLayout>
    );
}
