import React from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';

export default function VerifyCertificate() {
    const { certificate, number } = usePage().props;
    return (
        <SiteLayout title="Verify a certificate">
            <div className="ac-wide">
                <ul className="dm-crumbs"><li><a href="/">Home</a></li><li>Certificate verification</li></ul>
            </div>
            <div className="tw">
                <div className="ac-wide py-10">
                    <div className="max-w-2xl mx-auto">
                        {certificate ? (
                            <div className="rounded-2xl border-2 border-green-600 bg-white overflow-hidden">
                                <div className="bg-green-600 text-white px-6 py-4 flex items-center gap-3">
                                    <i className="fa fa-check-circle text-2xl" aria-hidden="true"></i>
                                    <p className="text-lg font-black">Valid ArtistikCity certificate</p>
                                </div>
                                <dl className="p-6 grid grid-cols-3 gap-y-3 text-base">
                                    <dt className="text-gray-500">Awarded to</dt><dd className="col-span-2 text-xl font-black text-ink">{certificate.student_name}</dd>
                                    <dt className="text-gray-500">{String(certificate.course_type_id) === '2' ? 'Workshop' : 'Course'}</dt><dd className="col-span-2 font-bold text-ink">{certificate.course_title}</dd>
                                    {certificate.medium_name && <><dt className="text-gray-500">Medium</dt><dd className="col-span-2">{certificate.medium_name}{certificate.skill_name ? ` · ${certificate.skill_name}` : ''}</dd></>}
                                    {certificate.instructor_name && <><dt className="text-gray-500">Instructor</dt><dd className="col-span-2">{certificate.instructor_name}</dd></>}
                                    <dt className="text-gray-500">Issued</dt><dd className="col-span-2">{certificate.issued_on}</dd>
                                    <dt className="text-gray-500">Certificate no.</dt><dd className="col-span-2 font-mono">{certificate.certificate_no}</dd>
                                </dl>
                                <p className="px-6 pb-6 text-sm text-gray-500">Awarded after completing every lesson and having an assignment approved by an ArtistikCity instructor.</p>
                            </div>
                        ) : (
                            <div className="rounded-2xl border-2 border-red-300 bg-white p-8 text-center">
                                <i className="fa fa-times-circle text-4xl text-red-500" aria-hidden="true"></i>
                                <p className="mt-3 text-2xl font-black text-ink">Certificate not found</p>
                                <p className="mt-2 text-gray-600">We couldn't find a certificate with number <span className="font-mono">{number}</span>. Check the number printed on the certificate and try again.</p>
                            </div>
                        )}
                        <form className="mt-8 flex gap-2" onSubmit={(e) => { e.preventDefault(); const v = e.target.elements.no.value.trim(); if (v) window.location.href = `/certificates/verify/${encodeURIComponent(v.toUpperCase())}`; }}>
                            <input name="no" defaultValue={certificate ? '' : number} placeholder="Certificate number, e.g. AC-2026-ABCD2345" className="flex-1 h-12 rounded-full border-gray-300 px-5 font-mono focus:border-brand focus:ring-brand" aria-label="Certificate number" />
                            <button className="h-12 px-6 rounded-full bg-ink text-white font-bold">Verify</button>
                        </form>
                    </div>
                </div>
            </div>
        </SiteLayout>
    );
}
