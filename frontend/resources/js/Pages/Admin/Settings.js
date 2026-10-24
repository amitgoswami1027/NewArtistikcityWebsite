import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';
import axios from 'axios';
import AdminLayout, { PageHeader, useToast, errorText } from '@/Components/Admin/AdminLayout';
import { Button } from '@/Components/Admin/kit';
import { FormSection, Field, DropZone, SaveBar, Row2, Row3, useUnsavedGuard } from '@/Components/Admin/form';

const flatten = (values) => {
    const out = {};
    Object.entries(values).forEach(([g, kv]) => Object.entries(kv).forEach(([k, v]) => { out[`${g}.${k}`] = v || ''; }));
    return out;
};
const nest = (flat) => {
    const out = {};
    Object.entries(flat).forEach(([key, v]) => { const [g, k] = key.split('.'); out[g] = out[g] || {}; out[g][k] = v; });
    return out;
};

/** Site settings & SEO: homepage highlights, search/sharing defaults, contact details and social links. */
export default function Settings() {
    const { values, defaults = {} } = usePage().props;
    const [saved, setSaved] = useState(flatten(values));
    const [v, setV] = useState(flatten(values));
    const [errors, setErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const [og, setOg] = useState([]);
    const [toastNode, toast] = useToast();
    const dirty = JSON.stringify(v) !== JSON.stringify(saved) || og.length > 0;
    useUnsavedGuard(dirty);

    const set = (k, x) => { setV((p) => ({ ...p, [k]: x })); setErrors((e) => ({ ...e, [k]: null })); };
    const input = (k, label, { hint, rows, placeholder, max = 255, type = 'text', optional = true } = {}) => (
        <Field label={label} name={k} hint={hint} error={errors[k]} max={max} counter={(v[k] || '').length} optional={optional}>
            {rows ? <textarea id={`f-${k}`} className="adm-input" rows={rows} value={v[k]} placeholder={placeholder} onChange={(e) => set(k, e.target.value)} aria-invalid={errors[k] ? 'true' : undefined} />
                : <input id={`f-${k}`} type={type} className="adm-input" value={v[k]} placeholder={placeholder} onChange={(e) => set(k, e.target.value)} aria-invalid={errors[k] ? 'true' : undefined} />}
        </Field>
    );
    const save = () => {
        setBusy(true);
        const fd = new FormData();
        fd.append('data', JSON.stringify(nest(v)));
        if (og[0]) fd.append('og_image', og[0]);
        axios.post('/admin/api/settings', fd).then((r) => {
            const f = flatten(r.data.values);
            setSaved(f); setV(f); setOg([]); setErrors({}); toast(r.data.message);
        }).catch((e) => {
            if (e.response && e.response.status === 422) {
                setErrors(e.response.data.errors || {});
                toast(e.response.data.message, 'error');
                const first = Object.keys(e.response.data.errors || {})[0];
                const el = first && document.getElementById(`f-${first}`);
                if (el) { el.focus(); el.scrollIntoView({ block: 'center' }); }
            } else toast(errorText(e), 'error');
        }).finally(() => setBusy(false));
    };
    const title = v['seo.site_title'] || defaults.site_title;
    const desc = v['seo.meta_description'] || defaults.meta_description;

    return (
        <AdminLayout title="Site settings & SEO">
            <PageHeader title="Site settings & SEO" subtitle="Details used across the public website. Changes go live as soon as you save." />
            <form className="adm-form" onSubmit={(e) => { e.preventDefault(); save(); }} noValidate>
                <FormSection id="seo" title="Search & sharing" description="The default title and description search engines and social networks show for the site. Blog posts can override them.">
                    <div className="adm-serp" aria-label="Search result preview">
                        <span className="adm-serp__url">artistikcity.com</span>
                        <span className="adm-serp__title">{title.length > 60 ? `${title.slice(0, 59)}…` : title}</span>
                        <span className="adm-serp__text">{desc.length > 160 ? `${desc.slice(0, 159)}…` : desc}</span>
                    </div>
                    {input('seo.site_title', 'Site title', { hint: 'Best under 60 characters.', optional: false })}
                    {input('seo.meta_description', 'Meta description', { rows: 3, max: 500, hint: 'Best between 120 and 160 characters.' })}
                    {input('seo.meta_keywords', 'Keywords', { hint: 'Comma separated. Most search engines ignore these, but some tools read them.' })}
                    <Field label="Share image" name="seo.og_image" hint="Shown when the site is shared on WhatsApp, Facebook or LinkedIn. 1200 × 630 px works best." optional>
                        <DropZone name="seo.og_image" value={og} onChange={setOg} error={errors['seo.og_image']}
                            existing={v['seo.og_image'] ? [{ id: 'og', url: v['seo.og_image'], name: 'Current share image' }] : []}
                            onRemoveExisting={() => set('seo.og_image', '')} />
                    </Field>
                </FormSection>

                <FormSection id="home" title="Homepage highlights" description="The three short points under the course and workshop sections of the homepage.">
                    <Row3>
                        {input('home.course_text_1', 'Course point 1', { max: 60 })}
                        {input('home.course_text_2', 'Course point 2', { max: 60 })}
                        {input('home.course_text_3', 'Course point 3', { max: 60 })}
                    </Row3>
                    <Row3>
                        {input('home.workshop_text_1', 'Workshop point 1', { max: 60 })}
                        {input('home.workshop_text_2', 'Workshop point 2', { max: 60 })}
                        {input('home.workshop_text_3', 'Workshop point 3', { max: 60 })}
                    </Row3>
                </FormSection>

                <FormSection id="contact" title="Contact details" description="Shown on the contact page. Leave a field empty to hide it.">
                    <Row2>
                        {input('contact.email', 'E-mail', { type: 'email', placeholder: 'hello@artistikcity.com' })}
                        {input('contact.phone', 'Phone / WhatsApp', { placeholder: '+91 98765 43210' })}
                    </Row2>
                    {input('contact.address', 'Office address', { rows: 3, max: 500, hint: 'One line per row; it also links to Google Maps.' })}
                </FormSection>

                <FormSection id="social" title="Social links" description="Icons in the site footer and on the contact page. Leave a link empty to hide that icon.">
                    <Row2>
                        {input('social.instagram', 'Instagram', { placeholder: 'https://www.instagram.com/…' })}
                        {input('social.facebook', 'Facebook', { placeholder: 'https://www.facebook.com/…' })}
                    </Row2>
                    <Row2>
                        {input('social.youtube', 'YouTube', { placeholder: 'https://www.youtube.com/@…' })}
                        {input('social.linkedin', 'LinkedIn', { placeholder: 'https://www.linkedin.com/…' })}
                    </Row2>
                </FormSection>
                <SaveBar dirty={dirty} busy={busy} onSave={save}>
                    {dirty && <Button variant="ghost" onClick={() => { setV(saved); setOg([]); setErrors({}); }}>Discard changes</Button>}
                </SaveBar>
            </form>
            {toastNode}
        </AdminLayout>
    );
}
