import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import { Inertia } from '@inertiajs/inertia';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button, Badge, useConfirm } from '@/Components/Admin/kit';
import {
    useForm, useUnsavedGuard, uploadImage, FormSection, Field, TextField, SelectField, Toggle, ChoiceChips, SlugField, ListEditor, DropZone, RichText, SaveBar, Row2, Row3,
} from '@/Components/Admin/form';
import Curriculum from '@/Components/Admin/Curriculum';

const LISTS = [
    ['course_highlights', 'Highlights', 'Short selling points shown near the top of the page.'],
    ['course_learn', 'What students will learn', 'One skill or outcome per line.'],
    ['course_for', 'Who it is for', ''],
    ['course_not_for', 'Who it is not for', ''],
    ['course_problems_solved', 'Problems it solves', ''],
    ['course_deliverables', 'What students make', 'Projects or pieces they finish during the course.'],
];

const INCLUDES = [
    ['certificate', 'Certificate of completion'],
    ['study_material_access', 'Study material access'],
    ['medium', 'Show the medium'],
    ['age', 'Show the age group'],
    ['skills', 'Show the skill level'],
];

const blank = (kind, me) => ({
    kind, title: '', slug: '', sub_title: '', status: false, is_free_course: false, admin_id: me ? String(me) : '', medium_id: '', genre_ids: [], skill_id: '',
    age_group: '', course_based: 'instructor_based', course_start_date: '', duration: '', sessions: '', time_required: '', mini_projects: '', course_modules: '',
    introduction: '', introduction_details: '', summary: '', summary_details: '', schedule: '', schedule_details: '', course_outcome: '', course_prerequisites: '',
    includes: { certificate: kind !== 'workshop', study_material_access: true, medium: true, age: true, skills: true },
    course_highlights: [], course_learn: [], course_for: [], course_not_for: [], course_problems_solved: [], course_deliverables: [],
    price_inr: '', price_usd: '', videos: [],
});

const editable = (c) => {
    const v = { ...c };
    ['id', 'photos', 'schedule_pdf', 'enrolled', 'public_url', 'updated_at'].forEach((k) => delete v[k]);
    return v;
};

export default function CourseEditor() {
    const { kind, course, curriculum, mediums = [], genres = [], skills = [], instructors = [], me } = usePage().props;
    const [saved, setSaved] = useState(course);
    const isNew = !saved;
    const noun = kind === 'workshop' ? 'workshop' : 'course';
    const base = kind === 'workshop' ? '/admin/workshop' : '/admin/courses';
    const [tab, setTab] = useState(() => (typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('tab') === 'curriculum' && course ? 'curriculum' : 'details'));
    const [cur, setCur] = useState(curriculum);
    const [photos, setPhotos] = useState([]);
    const [schedule, setSchedule] = useState([]);
    const [toastNode, toast] = useToast();
    const [dialog, confirm] = useConfirm();
    const form = useForm(course ? editable(course) : blank(kind, me), {
        title: ['required', 'max:255'],
        slug: ['slug', 'max:90'],
        sub_title: ['max:500'],
        course_start_date: (course ? !course.is_free_course : true) ? ['date'] : [],
        price_inr: ['number', 'min:0'],
        price_usd: ['number', 'min:0'],
        duration: ['number'], sessions: ['number'], time_required: ['number'], mini_projects: ['number'],
    });
    const v = form.values;
    useUnsavedGuard(form.dirty || photos.length > 0 || schedule.length > 0);

    const save = () => {
        const extra = {};
        if (!v.is_free_course) {
            if (!v.course_start_date) extra.course_start_date = 'Choose when the first session starts.';
            if (v.price_inr === '' || v.price_inr === null) extra.price_inr = 'Enter the price in rupees.';
            if (v.price_usd === '' || v.price_usd === null) extra.price_usd = 'Enter the price in US dollars.';
        }
        const ok = form.validate();
        if (!ok || Object.keys(extra).length) {
            form.setErrors((prev) => ({ ...prev, ...extra }));
            toast('Please fill in the highlighted fields.', 'error');
            if (ok) {
                const el = document.querySelector(`[data-field="${Object.keys(extra)[0]}"] input`);
                if (el) { el.focus(); el.scrollIntoView({ block: 'center' }); }
            }
            return;
        }
        const fd = new FormData();
        fd.append('data', JSON.stringify(v));
        photos.forEach((f) => fd.append('photos', f));
        if (schedule[0]) fd.append('schedule_pdf', schedule[0]);
        form.submit(() => axios.post(isNew ? '/admin/api/courses' : `/admin/api/courses/${saved.id}`, fd), { skipValidation: true })
            .then((r) => {
                if (isNew) {
                    toast(r.data.message);
                    Inertia.visit(`${r.data.editUrl}?tab=curriculum`);
                    return;
                }
                setSaved(r.data.course);
                form.reset(editable(r.data.course));
                setPhotos([]); setSchedule([]);
                toast(r.data.message);
            })
            .catch((e) => toast(e.response && e.response.status === 422 ? e.response.data.message : errorText(e), 'error'));
    };

    const removePhoto = async (p) => {
        if (!(await confirm({ title: 'Remove this photo?', body: 'It disappears from the course page straight away.', confirmLabel: 'Remove', danger: true }))) return;
        axios.delete(`/admin/api/courses/${saved.id}/photos/${p.id}`).then((r) => { setSaved({ ...saved, photos: saved.photos.filter((x) => x.id !== p.id) }); toast(r.data.message); })
            .catch((e) => toast(errorText(e), 'error'));
    };
    const removeSchedule = async () => {
        if (!(await confirm({ title: 'Remove the schedule PDF?', confirmLabel: 'Remove', danger: true }))) return;
        axios.delete(`/admin/api/courses/${saved.id}/schedule`).then((r) => { setSaved({ ...saved, schedule_pdf: null }); toast(r.data.message); })
            .catch((e) => toast(errorText(e), 'error'));
    };

    const setVideo = (i, k, val) => form.set('videos', v.videos.map((x, j) => (j === i ? { ...x, [k]: val } : x)));
    const title = isNew ? `New ${noun}` : (saved.title || `Untitled ${noun}`);

    return (
        <AdminLayout title={title} crumbs={[{ label: 'Learning' }, { href: base, label: kind === 'workshop' ? 'Workshops' : 'Courses' }, { label: isNew ? `New ${noun}` : saved.title }]}>
            <PageHeader eyebrow={kind === 'workshop' ? 'Workshop' : 'Course'} title={title}
                subtitle={isNew ? 'Fill in the basics and save; you can add modules and lessons right after.' : <>{saved.status ? <Badge tone="green" dot>Live on the site</Badge> : <Badge dot>Hidden</Badge>} <span className="adm-muted">· {saved.enrolled} enrolled</span></>}
                actions={!isNew && <Button as="a" href={saved.public_url} target="_blank" rel="noopener noreferrer" icon="fa-external-link">View page</Button>} />

            <div className="adm-tabs" role="tablist">
                <button type="button" role="tab" aria-selected={tab === 'details'} className={tab === 'details' ? 'is-on' : ''} onClick={() => setTab('details')}>Details</button>
                <button type="button" role="tab" aria-selected={tab === 'curriculum'} className={tab === 'curriculum' ? 'is-on' : ''} disabled={isNew}
                    onClick={() => setTab('curriculum')} title={isNew ? 'Save first to add modules and lessons' : undefined}>
                    Curriculum{!isNew && cur && <Badge>{cur.modules.length}</Badge>}
                </button>
            </div>

            {tab === 'curriculum' && !isNew && <Curriculum courseId={saved.id} initial={cur} toast={toast} confirm={confirm} noun={noun} onChange={setCur} />}

            {tab === 'details' && (
                <form className="adm-form" onSubmit={(e) => { e.preventDefault(); save(); }} noValidate>
                    <FormSection id="basics" title="Basics" description={`The name and summary students see in listings and at the top of the ${noun} page.`}>
                        <TextField form={form} name="title" label="Title" required max={255} placeholder={kind === 'workshop' ? 'e.g. Weekend Watercolour Florals' : 'e.g. Watercolour Landscapes for Beginners'} />
                        <SlugField form={form} source="title" prefix="/course/" onCheck={(s) => axios.get('/admin/api/slug', { params: { table: 'courses', slug: s, except: saved ? saved.id : 0 } }).then((r) => r.data)} />
                        <TextField form={form} name="sub_title" label="Subtitle" max={500} rows={2} hint="One or two sentences under the title." optional />
                        <Row2>
                            <Field label="Type" name="kind">
                                <select className="adm-input" id="f-kind" value={v.kind} onChange={(e) => form.set('kind', e.target.value)}>
                                    <option value="course">Course (several weeks)</option>
                                    <option value="workshop">Workshop (short session)</option>
                                </select>
                            </Field>
                            {instructors.length > 0
                                ? <SelectField form={form} name="admin_id" label="Instructor" options={instructors.map((i) => ({ value: String(i.value), label: i.label }))} placeholder="Choose an instructor" />
                                : <Field label="Instructor" name="admin_id" hint="Your courses are always listed under your name."><input className="adm-input" value="You" disabled /></Field>}
                        </Row2>
                        <Toggle name="status" checked={v.status} onChange={(x) => form.set('status', x)} label="Visible on the site" description={v.status ? 'Students can find and buy it.' : 'Hidden until you switch this on.'} />
                    </FormSection>

                    <FormSection id="category" title="Category" description="Used for filters on the courses page and shown in the course facts.">
                        <Row2>
                            <SelectField form={form} name="medium_id" label="Medium" options={mediums.map((m) => ({ value: String(m.value), label: m.label }))} />
                            <SelectField form={form} name="skill_id" label="Skill level" options={skills.map((m) => ({ value: String(m.value), label: m.label }))} optional />
                        </Row2>
                        <Field label="Genres" name="genre_ids" hint="Pick all that apply." optional>
                            <ChoiceChips name="genre_ids" options={genres.map((g) => ({ value: String(g.value), label: g.label }))} value={v.genre_ids} onChange={(x) => form.set('genre_ids', x)} />
                        </Field>
                        <TextField form={form} name="age_group" label="Age group" placeholder="e.g. 12+ years" max={255} optional />
                    </FormSection>

                    <FormSection id="schedule" title="Schedule & format" description="When it runs and how much time it takes. Numbers only; units are added on the site.">
                        <Toggle name="is_free_course" checked={v.is_free_course} onChange={(x) => form.set('is_free_course', x)} label="Free course" description="No payment and no start date needed." />
                        <Row3>
                            <TextField form={form} name="course_start_date" label="First session" type="date" required={!v.is_free_course} />
                            <TextField form={form} name="duration" label="Duration" suffix="weeks" inputProps={{ inputMode: 'numeric' }} optional />
                            <TextField form={form} name="sessions" label="Live sessions" inputProps={{ inputMode: 'numeric' }} optional />
                        </Row3>
                        <Row3>
                            <TextField form={form} name="time_required" label="Time needed" suffix="hrs/week" inputProps={{ inputMode: 'numeric' }} optional />
                            <TextField form={form} name="mini_projects" label="Mini projects" inputProps={{ inputMode: 'numeric' }} optional />
                            <TextField form={form} name="course_modules" label="Modules (shown)" inputProps={{ inputMode: 'numeric' }} hint="Leave empty to count automatically." optional />
                        </Row3>
                        <Field label="Format" name="course_based">
                            <select className="adm-input" id="f-course_based" value={v.course_based} onChange={(e) => form.set('course_based', e.target.value)}>
                                <option value="instructor_based">Live with an instructor</option>
                                <option value="video_course">Recorded video course</option>
                            </select>
                        </Field>
                        <TextField form={form} name="schedule" label="Schedule summary" rows={2} placeholder="e.g. Live sessions every Saturday and Sunday, 11 am IST" optional />
                        <Field label="Schedule details" name="schedule_details" optional>
                            <RichText name="schedule_details" value={v.schedule_details} onChange={(x) => form.set('schedule_details', x)} minHeight={140} onUpload={uploadImage} />
                        </Field>
                        <Field label="Schedule PDF" name="schedule_pdf" hint="Students can download it from the course page and their classroom." optional>
                            <DropZone name="schedule_pdf" kind="pdf" value={schedule} onChange={setSchedule} error={form.error('schedule_pdf')}
                                existing={saved && saved.schedule_pdf ? [{ id: 'pdf', ...saved.schedule_pdf }] : []} onRemoveExisting={removeSchedule} />
                        </Field>
                    </FormSection>

                    <FormSection id="pricing" title="Pricing" description="Charged at checkout: rupees in India (Razorpay), US dollars elsewhere (PayPal).">
                        {v.is_free_course ? <p className="adm-muted">This is a free course, so no price is charged.</p> : (
                            <Row2>
                                <TextField form={form} name="price_inr" label="Price in India" prefix="₹" required inputProps={{ inputMode: 'decimal' }} placeholder="4999" />
                                <TextField form={form} name="price_usd" label="Price elsewhere" prefix="US$" required inputProps={{ inputMode: 'decimal' }} placeholder="69" />
                            </Row2>
                        )}
                        <Field label="Included with the course" name="includes">
                            <div className="adm-stack">
                                {INCLUDES.map(([k, l]) => <Toggle key={k} name={`inc-${k}`} checked={!!v.includes[k]} onChange={(x) => form.set('includes', { ...v.includes, [k]: x })} label={l} />)}
                            </div>
                        </Field>
                    </FormSection>

                    <FormSection id="content" title="Page content" description="The longer sections of the course page. Use headings and lists to keep them easy to scan.">
                        <TextField form={form} name="introduction" label="Introduction (short)" rows={2} optional />
                        <Field label="Introduction" name="introduction_details" optional>
                            <RichText name="introduction_details" value={v.introduction_details} onChange={(x) => form.set('introduction_details', x)} onUpload={uploadImage} />
                        </Field>
                        <TextField form={form} name="summary" label="Summary (short)" rows={2} optional />
                        <Field label="Summary" name="summary_details" optional>
                            <RichText name="summary_details" value={v.summary_details} onChange={(x) => form.set('summary_details', x)} minHeight={180} onUpload={uploadImage} />
                        </Field>
                        <Field label="Outcome" name="course_outcome" optional>
                            <RichText name="course_outcome" value={v.course_outcome} onChange={(x) => form.set('course_outcome', x)} minHeight={140} />
                        </Field>
                        <Field label="Prerequisites" name="course_prerequisites" optional>
                            <RichText name="course_prerequisites" value={v.course_prerequisites} onChange={(x) => form.set('course_prerequisites', x)} minHeight={140} />
                        </Field>
                    </FormSection>

                    <FormSection id="lists" title="Highlights & lists" description="Bullet lists on the course page. Press Enter to add an item; use the arrows to reorder.">
                        {LISTS.map(([k, l, h]) => (
                            <Field key={k} label={l} name={k} hint={h} optional>
                                <ListEditor name={k} value={v[k]} onChange={(x) => form.set(k, x)} placeholder="Add an item and press Enter" />
                            </Field>
                        ))}
                    </FormSection>

                    <FormSection id="media" title="Photos & videos" description="The first photo is the cover in listings. Videos are YouTube or other links shown on the page and in lessons.">
                        <Field label="Photos" name="photos" error={null} optional>
                            <DropZone name="photos" multiple value={photos} onChange={setPhotos} error={form.error('photos')}
                                existing={saved ? saved.photos : []} onRemoveExisting={removePhoto} label="Drop photos here or click to choose" />
                        </Field>
                        <Field label="Videos" name="videos" error={form.error('videos')} optional>
                            <div className="adm-stack">
                                {v.videos.map((x, i) => (
                                    <div key={i} className="adm-list__row">
                                        <input className="adm-input" placeholder="Video title" aria-label={`Video ${i + 1} title`} value={x.title} onChange={(e) => setVideo(i, 'title', e.target.value)} />
                                        <input className="adm-input" placeholder="https://www.youtube.com/watch?v=…" aria-label={`Video ${i + 1} link`} value={x.url} onChange={(e) => setVideo(i, 'url', e.target.value)} />
                                        <button type="button" onClick={() => form.set('videos', v.videos.filter((_, j) => j !== i))} aria-label={`Remove video ${i + 1}`}><i className="fa fa-trash-o" aria-hidden="true"></i></button>
                                    </div>
                                ))}
                                <div><Button size="sm" icon="fa-plus" onClick={() => form.set('videos', [...v.videos, { title: '', url: '' }])}>Add a video</Button></div>
                            </div>
                        </Field>
                    </FormSection>

                    <SaveBar dirty={form.dirty || photos.length > 0 || schedule.length > 0} busy={form.busy} onSave={save} saveLabel={isNew ? `Create ${noun}` : 'Save changes'}
                        status={isNew ? 'Not saved yet' : undefined}>
                        <Button as="a" href={base} variant="ghost">{isNew ? 'Cancel' : 'Back to list'}</Button>
                    </SaveBar>
                </form>
            )}
            {dialog}{toastNode}
        </AdminLayout>
    );
}
