import React from 'react';

export function programImage(course) {
    const photos = course.photos || [];
    return photos.length ? `/storage/uploads/courses/${course.id}/${photos[0].photo_name}` : '/assets/images/course1.jpg';
}

export function programPrice(course) {
    const prices = course.prices || [];
    if (!prices.length || prices[0].price_inr === undefined || prices[0].price_inr === null) return null;
    const n = Number(prices[0].price_inr);
    if (isNaN(n)) return null;
    return n === 0 ? 'Free' : '₹' + n.toLocaleString('en-IN');
}

export function isWorkshop(course) {
    return String(course.course_type_id) === '2';
}

export default function ProgramCard({ course }) {
    const { title, slug, sub_title, introduction, age_group, skill, medium, duration, time_required, course_start_date, teacher } = course;
    const workshop = isWorkshop(course);
    const price = programPrice(course);
    const href = route('course.details', { slug });

    return (
        <article className="ac-card">
            <a href={href} className="ac-card__media">
                <img src={programImage(course)} alt={title} loading="lazy" />
                <span className={`ac-chip ${workshop ? 'ac-chip--accent' : 'ac-chip--dark'}`}>{workshop ? 'Workshop' : 'Live course'}</span>
            </a>
            <div className="ac-card__body">
                {medium && medium.name && <div className="ac-card__school">{medium.name}</div>}
                <h3 className="ac-card__title"><a href={href}>{title}</a></h3>
                {(sub_title || introduction) && <p className="ac-card__text">{sub_title || introduction}</p>}
                <div className="ac-card__chips">
                    {skill && skill.name && <span className="ac-chip ac-chip--brand">{skill.name}</span>}
                    {age_group && <span className="ac-chip">{age_group}</span>}
                </div>
                <div className="ac-card__meta">
                    {course_start_date && <span><i className="fa fa-calendar-o"></i>Starts {course_start_date}</span>}
                    {duration && <span><i className="fa fa-clock-o"></i>{duration} {workshop ? 'days' : 'weeks'}{time_required ? ` · ${time_required} hrs/session` : ''}</span>}
                    {teacher && teacher.name && <span><i className="fa fa-user-o"></i>{teacher.name}</span>}
                </div>
                <div className="ac-card__foot">
                    <span className="ac-price">{price || ''}</span>
                    <a href={href} className="ac-link">View program <i className="fa fa-arrow-right"></i></a>
                </div>
            </div>
        </article>
    );
}
