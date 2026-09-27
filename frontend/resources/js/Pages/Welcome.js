import React, { useRef } from 'react';
import { usePage, useForm } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import CourseCard from '@/Components/Site/CourseCard';
import { isWorkshop } from '@/Components/Site/ProgramCard';
import { catalogUrl } from '@/Components/Site/SiteHeader';
import StudioStoriesStrip from '@/Components/Site/StudioStoriesStrip';

const BENEFITS = [
    { icon: 'fa-video-camera', title: 'Live, small-group classes', text: 'Watch every demo up close and ask questions in real time.' },
    { icon: 'fa-comments-o', title: 'Personal feedback', text: 'Submit your projects and get specific notes from your instructor.' },
    { icon: 'fa-clock-o', title: 'Learn at your own pace', text: 'Recordings and study material stay open after each session.' },
    { icon: 'fa-certificate', title: 'Certificate', text: 'Finish your course and download a shareable certificate.' },
    { icon: 'fa-users', title: 'Every age welcome', text: 'Batches for sub-juniors, juniors and adults.' },
    { icon: 'fa-paint-brush', title: 'Every major medium', text: 'Graphite, charcoal, pastel, watercolour, acrylic and more.' },
    { icon: 'fa-id-card-o', title: 'Free artist profile', text: 'Publish your finished work to a public portfolio.' },
    { icon: 'fa-gift', title: 'Free starter lessons', text: 'Join for free and start with guided mini-lessons.' },
];

function Row({ title, subtitle, link, courses }) {
    const ref = useRef(null);
    if (!courses.length) return null;
    const scroll = (dir) => { if (ref.current) ref.current.scrollBy({ left: dir * ref.current.clientWidth * 0.9, behavior: 'smooth' }); };
    return (
        <section className="dm-section" style={{ paddingBottom: 24 }}>
            <div className="ac-wide">
                <div className="dm-head">
                    <div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>
                    <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
                        {link && <a href={link}>See all</a>}
                        {courses.length > 4 && (
                            <div className="dm-arrows">
                                <button type="button" aria-label="Previous" onClick={() => scroll(-1)}><i className="fa fa-angle-left"></i></button>
                                <button type="button" aria-label="Next" onClick={() => scroll(1)}><i className="fa fa-angle-right"></i></button>
                            </div>
                        )}
                    </div>
                </div>
                <div className="dm-row" ref={ref}>
                    {courses.map((c, i) => <CourseCard key={c.id} course={c} index={i} />)}
                </div>
            </div>
        </section>
    );
}

export default function Welcome() {
    const { mediums = [], open_courses, home_artworks = [], testimonials = [], instructor = {} } = usePage().props;
    const programs = (open_courses && open_courses.data) || [];
    const popular = [...programs].sort((a, b) => Number(b.students || 0) - Number(a.students || 0));
    const courses = programs.filter((c) => !isWorkshop(c));
    const workshops = programs.filter(isWorkshop);
    const heroArt = home_artworks.slice(0, 4).map((a) => `/storage/uploads/home-artworks/${a.id}/${a.photo_name}`);
    while (heroArt.length < 4) heroArt.push('/assets/images/home-banner.png');
    const free = useForm({ name: '', email: '', free_course_type: 'color' });
    const [q, setQ] = React.useState('');

    return (
        <SiteLayout title="Online art courses" showCategories>
            {/* ---------- hero ---------- */}
            <section className="dm-hero">
                <div className="dm-hero__bg" aria-hidden="true">
                    {heroArt.map((src, i) => <span key={i} style={{ backgroundImage: `url(${src})` }}></span>)}
                </div>
                <div className="ac-wide">
                    <div className="dm-hero__inner">
                        <h1>Learn to draw and paint <em>with a real artist.</em></h1>
                        <p>Live online art courses and workshops for every age and level. Pick a medium, join a small group and finish artwork you're proud of.</p>
                        <form className="dm-hero__search" onSubmit={(e) => { e.preventDefault(); window.location.href = catalogUrl({ q }); }}>
                            <input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="What do you want to learn?" aria-label="Search courses" />
                            <button type="submit" className="dm-btn dm-btn--brand dm-btn--lg">Search</button>
                        </form>
                        <div className="dm-hero__tags">
                            {mediums.slice(0, 5).map((m) => <a key={m.id} href={catalogUrl({ medium: m.id })}>{m.name}</a>)}
                        </div>
                    </div>
                </div>
            </section>

            <Row title="Popular right now" subtitle="The courses our students are enrolling in this term." link={catalogUrl({})} courses={popular} />

            {/* ---------- categories ---------- */}
            {mediums.length > 0 && (
                <section className="dm-section" style={{ paddingBottom: 24 }}>
                    <div className="ac-wide">
                        <div className="dm-head"><div><h2>Explore by medium</h2><p>Start with the material you love most.</p></div><a href={catalogUrl({})}>All courses</a></div>
                        <div className="dm-tiles">
                            {mediums.map((m) => (
                                <a key={m.id} className="dm-tile" href={catalogUrl({ medium: m.id })}>
                                    <span style={{ backgroundImage: `url(/storage/uploads/mediums/${m.id}/${m.photo})` }}></span>
                                    <strong>{m.name}</strong>
                                </a>
                            ))}
                        </div>
                    </div>
                </section>
            )}

            <Row title="Live courses" subtitle="Multi-week programs with projects, feedback and a certificate." link={catalogUrl({ type: 'course' })} courses={courses} />
            <Row title="Weekend workshops" subtitle="Short, focused sessions on a single technique." link={catalogUrl({ type: 'workshop' })} courses={workshops} />

            {/* ---------- benefits ---------- */}
            <section className="dm-section">
                <div className="ac-wide">
                    <div className="dm-head"><div><h2>Why learn with ArtistikCity</h2><p>Everything you need to keep creating, in one place.</p></div></div>
                    <div className="dm-benefits">
                        {BENEFITS.map((b) => (
                            <div className="dm-benefit" key={b.title}><i className={`fa ${b.icon}`}></i><h4>{b.title}</h4><p>{b.text}</p></div>
                        ))}
                    </div>
                </div>
            </section>

            {/* ---------- instructor ---------- */}
            {instructor && instructor.name && (
                <section className="dm-section dm-section--soft">
                    <div className="ac-wide ac-split">
                        <div className="ac-split__img" style={{ borderRadius: 12 }}>
                            <img src={`/storage/uploads/teachers/${instructor.id}/${instructor.profile_photo}`} alt={instructor.name} />
                        </div>
                        <div>
                            <span className="ac-eyebrow">Your instructor</span>
                            <h2 style={{ fontSize: 36 }}>{instructor.name}</h2>
                            {instructor.profile_title && <p style={{ color: 'var(--dm-grey)', fontSize: 17, margin: '6px 0 14px' }}>{instructor.profile_title}</p>}
                            <div className="ac-rich" dangerouslySetInnerHTML={{ __html: instructor.profile_description }}></div>
                            <a href={route('instructor')} className="dm-btn dm-btn--ink" style={{ marginTop: 18 }}>View profile</a>
                        </div>
                    </div>
                </section>
            )}

            {/* ---------- testimonials ---------- */}
            {testimonials.length > 0 && (
                <section className="dm-section">
                    <div className="ac-wide">
                        <div className="dm-head"><div><h2>What our students say</h2></div><a href={route('student.feedback')}>See student work</a></div>
                        <div className="dm-reviews" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))' }}>
                            {testimonials.map((t) => (
                                <div className="dm-review" key={t.id}>
                                    <div className="dm-review__who">
                                        <img src={`/storage/uploads/testimonials/${t.id}/${t.photo}`} alt={t.name} />
                                        <div><strong>{t.name}</strong><span>{t.title}</span></div>
                                        <i className="fa fa-thumbs-o-up dm-review__thumb"></i>
                                    </div>
                                    <p>{t.description}</p>
                                </div>
                            ))}
                        </div>
                    </div>
                </section>
            )}

            {/* ---------- student gallery ---------- */}
            {home_artworks.length > 0 && (
                <section className="dm-section" style={{ paddingTop: 0 }}>
                    <div className="ac-wide">
                        <div className="dm-head"><div><h2>Made by our students</h2><p>Real artwork from real ArtistikCity classes.</p></div><a href={route('student.feedback')}>Open the gallery</a></div>
                        <div className="dm-gallery">
                            {home_artworks.slice(0, 8).map((a) => {
                                const src = `/storage/uploads/home-artworks/${a.id}/${a.photo_name}`;
                                return <a key={a.id} href={src} data-fancybox="home-gallery" style={{ backgroundImage: `url(${src})` }} aria-label="Student artwork"></a>;
                            })}
                        </div>
                    </div>
                </section>
            )}

            <StudioStoriesStrip />

            {/* ---------- join banner ---------- */}
            <section className="dm-section" style={{ paddingTop: 0 }}>
                <div className="ac-wide">
                    <div className="dm-join">
                        <div className="dm-join__txt">
                            <h2>Get a free lesson today</h2>
                            <p>Tell us where to send it. We'll create your free account and email your first guided lesson.</p>
                            <form onSubmit={(e) => { e.preventDefault(); free.post(route('getfreecourses')); }} style={{ display: 'grid', gap: 10, maxWidth: 420 }}>
                                <input className="ac-input" required placeholder="Your name" value={free.data.name} onChange={(e) => free.setData('name', e.target.value)} />
                                <input className="ac-input" type="email" required placeholder="Email address" value={free.data.email} onChange={(e) => free.setData('email', e.target.value)} />
                                {(free.errors.email || free.errors.name) && <div className="ac-field__error" style={{ color: '#ffb4b4' }}>{free.errors.email || free.errors.name}</div>}
                                <button className="dm-btn dm-btn--brand dm-btn--lg" disabled={free.processing}>Send my free lesson</button>
                            </form>
                        </div>
                        <div className="dm-join__img" style={{ backgroundImage: `url(${heroArt[1]})` }}></div>
                    </div>
                </div>
            </section>
        </SiteLayout>
    );
}
