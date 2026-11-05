import React, { useEffect, useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import SiteLayout from '@/Components/Site/SiteLayout';
import CourseCard, { formatCount } from '@/Components/Site/CourseCard';
import { programImage, programPrice, isWorkshop } from '@/Components/Site/ProgramCard';
import { catalogUrl } from '@/Components/Site/SiteHeader';

const asList = (v) => (Array.isArray(v) ? v.filter((x) => x !== null && String(x).trim() !== '') : []);
const has = (v) => v !== undefined && v !== null && String(v).trim() !== '';
const stripHtml = (h) => String(h || '').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();

function youtubeEmbed(url) {
    if (!url) return null;
    const m = String(url).match(/(?:youtu\.be\/|youtube\.com\/(?:watch\?v=|embed\/|shorts\/))([\w-]{6,})/);
    return m ? `https://www.youtube-nocookie.com/embed/${m[1]}?autoplay=1&rel=0` : null;
}

const TABS = [
    { id: 'information', label: 'Information' },
    { id: 'content', label: 'Content' },
    { id: 'projects', label: 'Projects' },
    { id: 'reviews', label: 'Reviews' },
    { id: 'faq', label: 'FAQ' },
];

const PERKS = [
    { icon: 'fa-video-camera', title: 'Live classes', text: 'Learn alongside a small group with real-time demos.' },
    { icon: 'fa-comments-o', title: 'Instructor feedback', text: 'Get personal notes on the projects you submit.' },
    { icon: 'fa-refresh', title: 'Replay anytime', text: 'Study material stays in your classroom to revisit.' },
    { icon: 'fa-download', title: 'Downloadable resources', text: 'Worksheets and references for each lesson.' },
    { icon: 'fa-certificate', title: 'Certificate', text: 'Earn a certificate when you complete the course.' },
    { icon: 'fa-mobile', title: 'Any device', text: 'Join from your laptop, tablet or phone.' },
    { icon: 'fa-users', title: 'Creative community', text: 'Share progress and learn from classmates.' },
    { icon: 'fa-life-ring', title: 'Friendly support', text: 'Our team is here if you get stuck.' },
];

export default function CourseDetails() {
    const { course, curriculum = [], projects = [], reviews = [], gallery = [], videos = [], related = [], stats = {}, auth } = usePage().props;
    const data = course.data;
    const workshop = isWorkshop(data);
    const kind = workshop ? 'workshop' : 'course';
    const price = programPrice(data);
    const token = typeof document !== 'undefined' ? document.head.querySelector('meta[name="csrf-token"]') : null;
    const includes = data.course_includes || {};
    const learn = [...asList(data.course_learn), ...asList(data.course_highlights)];
    const forWho = asList(data.course_for);
    const notFor = asList(data.course_not_for);
    const solves = asList(data.course_problems_solved);
    const deliverables = asList(data.course_deliverables);
    const teacher = data.teacher || {};
    const cover = programImage(data);
    const video = videos[0];
    const embed = video ? youtubeEmbed(video.video_url) : null;
    const allLessons = curriculum.flatMap((u, ui) => (u.lessons || []).map((l, li) => ({ ...l, unit: ui + 1, n: li + 1 })));
    const firstPrice = data.prices && data.prices[0] ? data.prices[0].price_inr : 0;

    const [active, setActive] = useState('information');
    const [playing, setPlaying] = useState(false);
    const [aboutOpen, setAboutOpen] = useState(false);
    const [allLessonsOpen, setAllLessonsOpen] = useState(false);
    const [reviewCount, setReviewCount] = useState(6);
    const [pageUrl, setPageUrl] = useState('');
    const [copied, setCopied] = useState(false);
    const [saved, setSaved] = useState(false);

    useEffect(() => {
        setPageUrl(window.location.href);
        try { setSaved(JSON.parse(localStorage.getItem('ac-saved') || '[]').includes(data.id)); } catch (e) { /* storage unavailable */ }
        const onScroll = () => {
            let current = 'information';
            TABS.forEach((t) => { const el = document.getElementById(t.id); if (el && el.getBoundingClientRect().top < 160) current = t.id; });
            setActive(current);
        };
        window.addEventListener('scroll', onScroll, { passive: true });
        return () => window.removeEventListener('scroll', onScroll);
    }, []);

    const share = () => {
        if (navigator.share) { navigator.share({ title: data.title, url: pageUrl }).catch(() => {}); return; }
        if (navigator.clipboard) navigator.clipboard.writeText(pageUrl).then(() => { setCopied(true); setTimeout(() => setCopied(false), 2000); });
    };
    const toggleSave = () => {
        try {
            const list = JSON.parse(localStorage.getItem('ac-saved') || '[]');
            const next = list.includes(data.id) ? list.filter((x) => x !== data.id) : [...list, data.id];
            localStorage.setItem('ac-saved', JSON.stringify(next));
            setSaved(next.includes(data.id));
        } catch (e) { setSaved(!saved); }
    };

    const BuyForm = ({ label = 'Enroll now', big = true }) => (
        <form method="post" action="/add-to-cart">
            <input type="hidden" name="course_id" value={data.id} />
            <input type="hidden" name="_token" value={token ? token.content : ''} />
            <input type="hidden" name="price_type" value="inr" />
            <input type="hidden" name="price" value={firstPrice} />
            <button type="submit" className={`dm-btn dm-btn--brand dm-btn--block ${big ? 'dm-btn--lg' : ''}`}>{label}</button>
        </form>
    );

    const lessonsShown = allLessonsOpen ? allLessons : allLessons.slice(0, 6);

    return (
        <SiteLayout title={data.title}>
            <div className="ac-wide">
                <ul className="dm-crumbs">
                    <li><a href={route('welcome')}>Home</a></li>
                    <li><a href={catalogUrl({})}>Courses</a></li>
                    {data.medium && data.medium.name && <li><a href={catalogUrl({ medium: data.medium_id })}>{data.medium.name}</a></li>}
                    <li>{data.title}</li>
                </ul>

                <div className="dm-course">
                    {/* ================= main column ================= */}
                    <div>
                        {/* cover / trailer */}
                        <div className="dm-cover">
                            {playing && embed ? (
                                <iframe src={embed} title={video.video_title || data.title} allow="autoplay; encrypted-media; picture-in-picture" allowFullScreen></iframe>
                            ) : (
                                <>
                                    <img src={cover} alt={data.title} />
                                    {video && (embed ? (
                                        <button type="button" className="dm-cover__play" onClick={() => setPlaying(true)} aria-label="Play trailer"><span><i className="fa fa-play"></i></span></button>
                                    ) : (
                                        <a className="dm-cover__play" href={video.video_url} target="_blank" rel="noopener" aria-label="Watch trailer"><span><i className="fa fa-play"></i></span></a>
                                    ))}
                                    {video && <div className="dm-cover__label">Watch the trailer</div>}
                                </>
                            )}
                        </div>

                        {/* title block */}
                        <div className="dm-titleblock">
                            <div className="dm-badges">
                                {Number(stats.students || 0) >= 10 && <span className="dm-badge dm-badge--brand">Bestseller</span>}
                                <span className="dm-badge">{workshop ? 'Workshop' : 'Live course'}</span>
                                {data.skill && data.skill.name && <span className="dm-badge">{data.skill.name}</span>}
                            </div>
                            <h1>{data.title}</h1>
                            {has(data.sub_title) && <p className="dm-sub">{data.sub_title}</p>}
                            {teacher.name && (
                                <div className="dm-by">
                                    <img src={`/storage/uploads/teachers/${teacher.id}/${teacher.profile_photo}`} alt="" />
                                    <div>
                                        <strong>A {kind} by <a href={route('teacher.profile', teacher.slug)} style={{ textDecoration: 'underline' }}>{teacher.name}</a></strong>
                                        {has(teacher.profile_title) && <span>{teacher.profile_title}</span>}
                                    </div>
                                </div>
                            )}
                            <div className="dm-metarow">
                                {stats.positive !== null && stats.positive !== undefined && (
                                    <span className="is-good"><i className="fa fa-thumbs-o-up"></i> {stats.positive}% positive reviews ({formatCount(stats.reviews)})</span>
                                )}
                                <span><i className="fa fa-user-o"></i> {formatCount(stats.students)} students</span>
                                {data.medium && data.medium.name && <span><i className="fa fa-tint"></i> {data.medium.name}</span>}
                                <button type="button" onClick={share}><i className="fa fa-share-alt"></i> {copied ? 'Link copied' : 'Share'}</button>
                                <button type="button" onClick={toggleSave}><i className={`fa ${saved ? 'fa-bookmark' : 'fa-bookmark-o'}`}></i> {saved ? 'Saved' : 'Save for later'}</button>
                            </div>
                        </div>

                        <nav className="dm-tabs" aria-label="Course sections">
                            {TABS.map((t) => <a key={t.id} href={`#${t.id}`} className={active === t.id ? 'is-active' : ''}>{t.label}</a>)}
                        </nav>

                        {/* ---------- INFORMATION ---------- */}
                        <div id="information">
                            <section className="dm-block">
                                <h2>About this {kind}</h2>
                                {has(data.introduction) && <p style={{ fontSize: 18, fontWeight: 600 }}>{data.introduction}</p>}
                                <div className={`dm-readmore ${aboutOpen ? 'is-open' : ''}`}>
                                    <div className="ac-rich" dangerouslySetInnerHTML={{ __html: data.introduction_details || '' }}></div>
                                    {has(data.summary) && <><h3>{workshop ? 'Workshop' : 'Course'} summary</h3><p>{data.summary}</p></>}
                                    <div className="ac-rich" dangerouslySetInnerHTML={{ __html: data.summary_details || '' }}></div>
                                    {has(data.course_outcome) && <><h3>By the end you will have</h3><p>{data.course_outcome}</p></>}
                                </div>
                                <button type="button" className="dm-textbtn" onClick={() => setAboutOpen(!aboutOpen)}>{aboutOpen ? 'Read less' : 'Read more'}</button>
                            </section>

                            <section className="dm-block">
                                <h2>What you'll learn in this {kind}</h2>
                                <div className="dm-callout">
                                    <span><i className="fa fa-play-circle-o"></i>{stats.lessons || data.course_modules || 0} lessons</span>
                                    {Number(stats.downloads || 0) > 0 && <span><i className="fa fa-download"></i>{stats.downloads} downloads</span>}
                                    {has(data.sessions) && <span><i className="fa fa-video-camera"></i>{data.sessions} live sessions</span>}
                                </div>
                                {allLessons.length > 0 ? (
                                    <>
                                        <div className="dm-lessons">
                                            {lessonsShown.map((l, i) => (
                                                <div className="dm-lesson" key={l.id}>
                                                    <div className="dm-lesson__img" style={{ backgroundImage: `url(${cover})` }}>
                                                        <i className="fa fa-lock"></i>
                                                        <span>U{l.unit} · L{l.n}</span>
                                                    </div>
                                                    <p>{l.lesson_title}<small>{stripHtml(l.lesson_description).slice(0, 70)}</small></p>
                                                </div>
                                            ))}
                                        </div>
                                        {allLessons.length > 6 && (
                                            <button type="button" className="dm-textbtn" onClick={() => setAllLessonsOpen(!allLessonsOpen)}>{allLessonsOpen ? 'Show fewer lessons' : `View all ${allLessons.length} lessons`}</button>
                                        )}
                                    </>
                                ) : learn.length > 0 ? (
                                    <ul className="ac-checklist dm-twocol" style={{ gap: '0 24px' }}>{learn.map((x, i) => <li key={i}>{x}</li>)}</ul>
                                ) : null}
                                {learn.length > 0 && allLessons.length > 0 && (
                                    <>
                                        <h3>Skills you'll practise</h3>
                                        <ul className="ac-checklist dm-twocol" style={{ gap: '0 24px' }}>{learn.map((x, i) => <li key={i}>{x}</li>)}</ul>
                                    </>
                                )}
                                {solves.length > 0 && (
                                    <>
                                        <h3>Common problems this {kind} fixes</h3>
                                        <div className="ac-card__chips">{solves.map((x, i) => <span key={i} className="ac-chip ac-chip--brand">{x}</span>)}</div>
                                    </>
                                )}
                            </section>

                            <section className="dm-block">
                                <h2>What's included</h2>
                                <div className="ac-includes">
                                    {Number(stats.units || 0) > 0 && <div><i className="fa fa-th-list"></i>{stats.units} units</div>}
                                    {has(data.mini_projects) && <div><i className="fa fa-paint-brush"></i>{data.mini_projects} hands-on projects</div>}
                                    {has(data.sessions) && <div><i className="fa fa-video-camera"></i>{data.sessions} live sessions</div>}
                                    {includes.study_material_access === 'yes' && <div><i className="fa fa-book"></i>Study material access</div>}
                                    {includes.certificate === 'yes' && <div><i className="fa fa-certificate"></i>Certificate of completion</div>}
                                    {deliverables.map((x, i) => <div key={i}><i className="fa fa-check"></i>{x}</div>)}
                                </div>
                                {has(data.schedule) && (
                                    <>
                                        <h3>Schedule</h3>
                                        <p>{data.schedule}</p>
                                        <div className="ac-rich" dangerouslySetInnerHTML={{ __html: data.schedule_details || '' }}></div>
                                    </>
                                )}
                            </section>
                        </div>

                        {/* ---------- PROJECTS ---------- */}
                        <div id="projects">
                            <section className="dm-block">
                                <h2>{workshop ? 'Workshop' : 'Course'} project</h2>
                                {projects.length > 0 ? projects.map((p, i) => (
                                    <div className="dm-project" key={p.id} style={{ marginBottom: i < projects.length - 1 ? 24 : 0 }}>
                                        <div>
                                            <h3 style={{ marginTop: 0 }}>{p.project_title}</h3>
                                            <p style={{ color: '#444' }}>{p.project_description}</p>
                                        </div>
                                        <img src={cover} alt={p.project_title} />
                                    </div>
                                )) : (
                                    <p style={{ color: '#444' }}>{has(data.course_outcome) ? data.course_outcome : `You'll finish the ${kind} with completed artwork ready to share.`}</p>
                                )}
                            </section>

                            {gallery.length > 0 && (
                                <section className="dm-block">
                                    <h2>Projects by ArtistikCity students</h2>
                                    <div className="dm-gallery">
                                        {gallery.slice(0, 7).map((g) => {
                                            const src = `/storage/uploads/artworks/${g.id}/${g.photo_name}`;
                                            return <a key={g.id} href={src} data-fancybox="course-gallery" style={{ backgroundImage: `url(${src})` }}><span>{g.name}</span></a>;
                                        })}
                                        <a href="/marketplace?source=student" className="dm-more">Student originals</a>
                                    </div>
                                </section>
                            )}

                            {(forWho.length > 0 || notFor.length > 0) && (
                                <section className="dm-block">
                                    <h2>Who is this {kind} for?</h2>
                                    <div className="dm-twocol">
                                        {forWho.length > 0 && <div><h3 style={{ marginTop: 0 }}>Perfect for</h3><ul className="ac-checklist">{forWho.map((x, i) => <li key={i}>{x}</li>)}</ul></div>}
                                        {notFor.length > 0 && <div><h3 style={{ marginTop: 0 }}>Probably not for</h3><ul>{notFor.map((x, i) => <li key={i} style={{ marginBottom: 10 }}><i className="fa fa-minus-circle" style={{ color: 'var(--dm-grey)', marginRight: 8 }}></i>{x}</li>)}</ul></div>}
                                    </div>
                                    {has(data.age_group) && <p style={{ marginTop: 16 }}><strong>Age group:</strong> {data.age_group}</p>}
                                </section>
                            )}

                            <section className="dm-block">
                                <h2>Requirements and materials</h2>
                                <p style={{ color: '#444' }}>{has(data.course_prerequisites) ? data.course_prerequisites : 'No previous experience is needed. Every lesson starts with a live demonstration.'}</p>
                                <p style={{ color: '#444' }}>You'll receive a full materials list after you enroll. We recommend affordable, student-grade {data.medium && data.medium.name ? data.medium.name.toLowerCase() + ' ' : ''}supplies so you can start without a big spend.</p>
                            </section>
                        </div>

                        {/* ---------- REVIEWS ---------- */}
                        <section className="dm-block" id="reviews">
                            <h2>Reviews</h2>
                            <div className="dm-reviewsum">
                                <div><strong>{formatCount(stats.students)}</strong><span>Students</span></div>
                                <div><strong>{formatCount(stats.reviews)}</strong><span>Reviews</span></div>
                                {stats.positive !== null && stats.positive !== undefined && <div className="is-good"><strong>{stats.positive}%</strong><span>Positive reviews</span></div>}
                            </div>
                            {reviews.length > 0 ? (
                                <>
                                    <div className="dm-reviews">
                                        {reviews.slice(0, reviewCount).map((r) => (
                                            <div className="dm-review" key={r.id}>
                                                <div className="dm-review__who">
                                                    {r.profile_photo ? <img src={`/storage/uploads/students/${r.user_id}/${r.profile_photo}`} alt="" /> : <span className="dm-initial">{(r.name || 'S').charAt(0)}</span>}
                                                    <div><strong>{r.name || 'ArtistikCity student'}</strong><span>{String(r.created_at || '').split(' ')[0]}</span></div>
                                                    <i className={`fa fa-thumbs-o-${Number(r.rating || 5) >= 3 ? 'up' : 'down'} dm-review__thumb ${Number(r.rating || 5) >= 3 ? '' : 'is-bad'}`}></i>
                                                </div>
                                                <p>{r.review}</p>
                                            </div>
                                        ))}
                                    </div>
                                    {reviews.length > reviewCount && <button type="button" className="dm-textbtn" onClick={() => setReviewCount(reviewCount + 6)}>More reviews</button>}
                                </>
                            ) : (
                                <p style={{ color: 'var(--dm-grey)' }}>This {kind} doesn't have reviews yet. Enroll and be the first to share your experience.</p>
                            )}
                        </section>

                        {/* ---------- INSTRUCTOR ---------- */}
                        {teacher.name && (
                            <section className="dm-block">
                                <h2>Your instructor</h2>
                                <div className="dm-teacher">
                                    <img src={`/storage/uploads/teachers/${teacher.id}/${teacher.profile_photo}`} alt={teacher.name} />
                                    <div>
                                        <h3>{teacher.name}</h3>
                                        {has(teacher.profile_title) && <div className="dm-role">{teacher.profile_title}</div>}
                                        {has(teacher.location) && <div className="dm-role"><i className="fa fa-map-marker"></i> {teacher.location}</div>}
                                        {has(teacher.profile_description) && <div className="ac-rich" dangerouslySetInnerHTML={{ __html: teacher.profile_description }}></div>}
                                        <div className="dm-teacher__links">
                                            <a href="https://studiovaishaliarts.com/" target="_blank" rel="noopener" aria-label="Website"><i className="fa fa-globe"></i></a>
                                            <a href="https://www.instagram.com/artistikcity/" target="_blank" rel="noopener" aria-label="Instagram"><i className="fa fa-instagram"></i></a>
                                            <a href="https://www.youtube.com/@artistikcityYT" target="_blank" rel="noopener" aria-label="YouTube"><i className="fa fa-youtube-play"></i></a>
                                            <a href="https://www.facebook.com/StudioVaishaliArts/" target="_blank" rel="noopener" aria-label="Facebook"><i className="fa fa-facebook"></i></a>
                                        </div>
                                        <a href={route('teacher.profile', teacher.slug)} className="dm-btn dm-btn--line">View full profile</a>
                                    </div>
                                </div>
                            </section>
                        )}

                        {/* ---------- CONTENT / CURRICULUM ---------- */}
                        <section className="dm-block dm-units" id="content">
                            <h2>Content</h2>
                            {curriculum.length > 0 ? curriculum.map((u, ui) => (
                                <details key={u.id} open={ui === 0}>
                                    <summary>
                                        <span className="dm-unit-code">U{ui + 1}</span>
                                        <span>{u.module_title}<small>{(u.lessons || []).length} lessons{has(u.module_duration) ? ` · ${u.module_duration}` : ''}</small></span>
                                    </summary>
                                    <ol>
                                        {(u.lessons || []).map((l) => (
                                            <li key={l.id}>
                                                <i className={`fa ${Number(l.has_download) ? 'fa-file-pdf-o' : 'fa-play-circle-o'}`}></i>
                                                <span>{l.lesson_title}{stripHtml(l.lesson_description) && <small>{stripHtml(l.lesson_description)}</small>}</span>
                                            </li>
                                        ))}
                                    </ol>
                                </details>
                            )) : (
                                <div className="ac-rich" dangerouslySetInnerHTML={{ __html: data.summary_details || data.schedule_details || '<p>The detailed syllabus will be shared when the batch opens.</p>' }}></div>
                            )}
                            {projects.length > 0 && (
                                <details>
                                    <summary><span className="dm-unit-code">FP</span><span>Final project<small>{projects[0].project_title}</small></span></summary>
                                    <ol><li><i className="fa fa-paint-brush"></i><span>{projects[0].project_description}</span></li></ol>
                                </details>
                            )}
                        </section>

                        {/* ---------- PERKS ---------- */}
                        <section className="dm-block">
                            <h2>What to expect from an ArtistikCity {kind}</h2>
                            <div className="dm-perks">
                                {PERKS.map((p) => <div className="dm-perk" key={p.title}><i className={`fa ${p.icon}`}></i><h4>{p.title}</h4><p>{p.text}</p></div>)}
                            </div>
                        </section>

                        {/* ---------- FAQ ---------- */}
                        <section className="dm-block dm-faq" id="faq">
                            <h2>Frequently asked questions</h2>
                            <details><summary>Do I need any previous experience?</summary><p>{has(data.course_prerequisites) ? data.course_prerequisites : 'No. The lessons start from the basics and build up step by step.'}</p></details>
                            <details><summary>When does the {kind} start and how long is it?</summary><p>{has(data.course_start_date) ? `The next batch starts on ${data.course_start_date}. ` : ''}{has(data.duration) ? `It runs for ${data.duration} ${workshop ? 'days' : 'weeks'}` : ''}{has(data.time_required) ? `, with about ${data.time_required} hours per session.` : '.'}</p></details>
                            <details><summary>What if I miss a live session?</summary><p>Study material stays available in your classroom, so you can catch up and still submit your projects for feedback.</p></details>
                            <details><summary>What materials will I need?</summary><p>You'll get a materials list right after enrolling, with budget-friendly options.</p></details>
                            <details><summary>Will I get a certificate?</summary><p>{includes.certificate === 'yes' ? 'Yes. Complete the lessons and projects and your certificate appears in your classroom, ready to download and share.' : 'Certificates are issued for full courses. Check the course details panel for this program.'}</p></details>
                            <details><summary>Who can I contact with questions?</summary><p>Write to us any time through the <a href={route('contact')} style={{ textDecoration: 'underline' }}>contact page</a> and we'll get back to you quickly.</p></details>
                        </section>
                    </div>

                    {/* ================= sidebar ================= */}
                    <aside className="dm-aside">
                        <div className="dm-buy">
                            <div className="dm-buy__price"><strong>{price || ''}</strong></div>
                            <div className="dm-buy__note">One-time payment · {workshop ? 'Full workshop access' : 'Full course access'}</div>
                            <BuyForm />
                            {!(auth && auth.user) && <a href={route('join') + '?return_to=' + encodeURIComponent(typeof window !== 'undefined' ? window.location.pathname : '/')} className="dm-btn dm-btn--line dm-btn--block dm-btn--lg">Join for free</a>}
                            <div className="dm-buy__guarantee"><i className="fa fa-shield"></i><span>Secure checkout. Questions before you enroll? <a href={route('contact')} style={{ textDecoration: 'underline' }}>Ask us</a>.</span></div>
                        </div>

                        <div className="dm-details">
                            <h4>{workshop ? 'Workshop' : 'Course'} details</h4>
                            <ul>
                                {data.medium && data.medium.name && <li><i className="fa fa-tint"></i><div><span>Medium</span><strong>{data.medium.name}</strong></div></li>}
                                {stats.positive !== null && stats.positive !== undefined && <li><i className="fa fa-thumbs-o-up"></i><div><span>Positive reviews</span><strong>{stats.positive}% ({formatCount(stats.reviews)})</strong></div></li>}
                                <li><i className="fa fa-user-o"></i><div><span>Students</span><strong>{formatCount(stats.students)}</strong></div></li>
                                <li><i className="fa fa-play-circle-o"></i><div><span>Lessons</span><strong>{stats.lessons || data.course_modules || '—'}{has(data.sessions) ? ` · ${data.sessions} live sessions` : ''}</strong></div></li>
                                {has(data.duration) && <li><i className="fa fa-clock-o"></i><div><span>Duration</span><strong>{data.duration} {workshop ? 'days' : 'weeks'}{has(data.time_required) ? ` · ${data.time_required} hrs/session` : ''}</strong></div></li>}
                                {has(data.course_start_date) && <li><i className="fa fa-calendar-o"></i><div><span>Starts</span><strong>{data.course_start_date}</strong></div></li>}
                                <li><i className="fa fa-laptop"></i><div><span>Format</span><strong>Online, live {workshop ? 'workshop' : 'classes'}</strong></div></li>
                                {data.skill && data.skill.name && <li><i className="fa fa-signal"></i><div><span>Level</span><strong>{data.skill.name}</strong></div></li>}
                                {has(data.age_group) && <li><i className="fa fa-child"></i><div><span>Age group</span><strong>{data.age_group}</strong></div></li>}
                                <li><i className="fa fa-language"></i><div><span>Language</span><strong>English, Hindi</strong></div></li>
                                {includes.certificate === 'yes' && <li><i className="fa fa-certificate"></i><div><span>Certificate</span><strong>Included</strong></div></li>}
                                {teacher.name && <li><i className="fa fa-graduation-cap"></i><div><span>Instructor</span><strong>{teacher.name}</strong></div></li>}
                            </ul>
                        </div>
                    </aside>
                </div>
            </div>

            {/* ---------- related ---------- */}
            {related.length > 0 && (
                <section className="dm-section dm-section--soft" style={{ marginTop: 40 }}>
                    <div className="ac-wide">
                        <div className="dm-head"><div><h2>You might also like</h2></div><a href={catalogUrl({})}>Browse all courses</a></div>
                        <div className="dm-grid">{related.map((c, i) => <CourseCard key={c.id} course={c} index={i} />)}</div>
                    </div>
                </section>
            )}

            <div className="dm-mobilebuy">
                <strong>{price || ''}</strong>
                <div style={{ flex: 1, maxWidth: 220 }}><BuyForm big={false} /></div>
            </div>
        </SiteLayout>
    );
}
