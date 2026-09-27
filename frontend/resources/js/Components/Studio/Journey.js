/** The six stages of the Student Creative Lifecycle, shared by the roadmap page and the studio. */
export const JOURNEY = [
    { n: '01', key: 'DISCOVER', title: 'Discover', text: 'See how ArtistikCity takes you from first sketch to a sold artwork.', href: '/how-it-works', cta: 'How it works' },
    { n: '02', key: 'JOIN', title: 'Join', text: 'Create your free account in under a minute, with email, Google or Facebook.', href: '/join', cta: 'Join for free' },
    { n: '03', key: 'CHOOSE_TRACK', title: 'Choose a track', text: 'Pick a live course or a weekend workshop in the medium you love.', href: '/courses?type=all', cta: 'Browse courses' },
    { n: '04', key: 'LEARN', title: 'Learn', text: 'Follow lessons in your classroom, practise and track your progress.', href: '/dashboard', cta: 'Open my studio' },
    { n: '05', key: 'SUBMIT', title: 'Submit', text: 'Upload your assignment milestone for a personal review from your instructor.', href: '/dashboard/submissions', cta: 'Submit work' },
    { n: '06', key: 'EXHIBIT_SELL', title: 'Exhibit & sell', text: 'Approved pieces join your portfolio, and you choose which ones to sell.', href: '/dashboard/portfolio', cta: 'My portfolio' },
    { n: '07', key: 'CERTIFY', title: 'Certify & showcase', text: 'Download your ArtistikCity certificate and a printable PDF portfolio with curriculum and instructor comments.', href: '/dashboard/certificates', cta: 'Certificates & reports' },
];

export const statusStyle = (status) => ({
    'Pending Review': { chip: 'bg-yellow-100 text-yellow-800 border-yellow-200', icon: 'fa-clock-o', label: 'Pending review' },
    Approved: { chip: 'bg-green-100 text-green-800 border-green-200', icon: 'fa-check-circle', label: 'Approved' },
    Rejected: { chip: 'bg-red-100 text-red-800 border-red-200', icon: 'fa-times-circle', label: 'Changes requested' },
}[status] || { chip: 'bg-gray-100 text-gray-700 border-gray-200', icon: 'fa-circle-o', label: status });

export const inr = (n) => '₹' + Number(n || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 });
export const shortDate = (d) => {
    if (!d) return '';
    const dt = new Date(String(d).replace(' ', 'T'));
    return isNaN(dt) ? String(d).slice(0, 10) : dt.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
};
