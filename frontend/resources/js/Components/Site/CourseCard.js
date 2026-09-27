import React from 'react';
import { programImage, programPrice, isWorkshop } from '@/Components/Site/ProgramCard';

export function formatCount(n) {
    const v = Number(n || 0);
    if (v >= 1000) return (v / 1000).toFixed(v >= 10000 ? 0 : 1).replace(/\.0$/, '') + 'K';
    return String(v);
}

function badgeFor(course, index) {
    if (isWorkshop(course)) return { label: 'Workshop', cls: 'dm-badge--accent' };
    if (Number(course.students || 0) >= 10) return { label: 'Bestseller', cls: 'dm-badge--brand' };
    if (index !== undefined && index < 2) return { label: 'New', cls: '' };
    return null;
}

export default function CourseCard({ course, index }) {
    const href = route('course.details', { slug: course.slug });
    const price = programPrice(course);
    const badge = badgeFor(course, index);
    const teacher = course.teacher && course.teacher.name;

    return (
        <article className="dm-card">
            <a href={href} className="dm-card__media">
                <img src={programImage(course)} alt={course.title} loading="lazy" />
                {badge && <span className={`dm-badge ${badge.cls}`}>{badge.label}</span>}
            </a>
            <div className="dm-card__body">
                <h3 className="dm-card__title"><a href={href}>{course.title}</a></h3>
                {teacher && <div className="dm-card__by">A course by {teacher}</div>}
                <div className="dm-card__stats">
                    <span><i className="fa fa-user-o"></i>{formatCount(course.students)} students</span>
                    {course.positive !== null && course.positive !== undefined
                        ? <span className="is-good"><i className="fa fa-thumbs-o-up"></i>{course.positive}% ({formatCount(course.reviews)})</span>
                        : course.medium && course.medium.name && <span><i className="fa fa-tint"></i>{course.medium.name}</span>}
                </div>
                <div className="dm-card__price">
                    {price || ''}
                    {course.course_start_date && <small>· Starts {course.course_start_date}</small>}
                </div>
            </div>
        </article>
    );
}
