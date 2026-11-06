/** The six learning stages of the Student Creative Lifecycle, shared by the roadmap page and the studio (studio stage 1-6). */
export const JOURNEY = [
    { n: '01', key: 'DISCOVER', title: 'Discover', text: 'See how ArtistikCity takes you from your first sketch to a certified course.', href: '/how-it-works', cta: 'How it works' },
    { n: '02', key: 'JOIN', title: 'Join', text: 'Create your free account in under a minute, with email, phone, Google or Facebook.', href: '/join', cta: 'Join for free' },
    { n: '03', key: 'CHOOSE_COURSE', title: 'Choose a course', text: 'Pick a live course or a weekend workshop in the medium you love.', href: '/courses?type=all', cta: 'Browse courses' },
    { n: '04', key: 'LEARN', title: 'Learn', text: 'Follow the lessons in your course, practise and track your progress.', href: '/user/my-courses', cta: 'Go to my courses' },
    { n: '05', key: 'SUBMIT', title: 'Submit', text: 'Upload your assignment milestone for a personal review from your instructor.', href: '/dashboard/submissions', cta: 'Submit work' },
    { n: '06', key: 'CERTIFY', title: 'Certify', text: 'Finish the course with approved work to earn your verifiable ArtistikCity certificate and PDF portfolio report.', href: '/dashboard/certificates', cta: 'Certificates & reports' },
];

/**
 * Step 7 of the public roadmap (/how-it-works): selling approved work in the marketplace. Kept out of JOURNEY because the
 * studio's progress (StudentStudioController.stage) ends at Certify; the text is filled in with the real terms by the page.
 */
export const SELL_STEP = { n: '07', key: 'SELL', title: 'Sell your art', href: '/marketplace', cta: 'Explore the Marketplace' };

/** Font Awesome icon per stage, for roadmaps. */
export const JOURNEY_ICONS = { DISCOVER: 'fa-compass', JOIN: 'fa-user-plus', CHOOSE_COURSE: 'fa-th-large', LEARN: 'fa-paint-brush', SUBMIT: 'fa-upload', CERTIFY: 'fa-certificate', SELL: 'fa-shopping-bag' };

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
