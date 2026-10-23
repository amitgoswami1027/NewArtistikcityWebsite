import React from 'react';
import { Head } from '@inertiajs/inertia-react';
import SiteHeader from '@/Components/Site/SiteHeader';
import SiteFooter from '@/Components/Site/SiteFooter';

/** Progress bar used in the course bar, the overview header and the lesson sidebar. */
export function ProgressBar({ percent = 0, label }) {
    const pct = Math.max(0, Math.min(100, Number(percent) || 0));
    return (
        <span className="cl-progress" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label={label || 'Course progress'}>
            <span style={{ width: `${pct}%` }}></span>
        </span>
    );
}

/** Address of a lesson in the learning view. */
export const lessonUrl = (moduleId, lessonId) => `/user/course/modules/${moduleId}${lessonId ? `?lesson=${lessonId}` : ''}`;

/**
 * Distraction-free frame for a student's course: the site header plus a slim course bar
 * (back link, course title, progress). No studio tabs, so the page is only about this course.
 */
export default function CourseShell({ title, course, progress, back, actions, footer = true, className = '', children }) {
    return (
        <div className={`ac cl ${className}`}>
            {title !== false && <Head title={title || course.title} />}
            <SiteHeader />
            <div className="cl-bar">
                <div className="ac-wide cl-bar__row">
                    <a href={back.href} className="cl-bar__back"><i className="fa fa-arrow-left" aria-hidden="true"></i><span>{back.label}</span></a>
                    <span className="cl-bar__title" title={course.title}>{course.title}</span>
                    {progress && (
                        <span className="cl-bar__progress">
                            <ProgressBar percent={progress.percent} />
                            <span>{progress.percent}%</span>
                        </span>
                    )}
                    {actions}
                </div>
            </div>
            {children}
            {footer && <SiteFooter />}
        </div>
    );
}
