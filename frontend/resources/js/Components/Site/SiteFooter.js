import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { API_URL } from '@/Components/Constants';
import { catalogUrl, AGE_GROUPS } from '@/Components/Site/SiteHeader';

export default function SiteFooter() {
    const [mediums, setMediums] = useState([]);
    useEffect(() => {
        axios.get(`${API_URL}/get_mediums`).then((r) => setMediums(Array.isArray(r.data) ? r.data : [])).catch(() => {});
    }, []);
    const year = new Date().getFullYear();

    return (
        <footer className="dm-footer">
            <div className="ac-wide">
                <div className="dm-footer__grid">
                    <div className="dm-footer__brand">
                        <a href="/"><img src="/assets/images/footerlogo.png" alt="ArtistikCity" /></a>
                        <p style={{ maxWidth: 320 }}>Online art courses taught live by a working artist. Learn to draw and paint at your own pace, in any medium, at any age.</p>
                        <div className="ac-social">
                            <a href="https://www.facebook.com/StudioVaishaliArts/" target="_blank" rel="noopener" aria-label="Facebook"><i className="fa fa-facebook"></i></a>
                            <a href="https://www.instagram.com/artistikcity/" target="_blank" rel="noopener" aria-label="Instagram"><i className="fa fa-instagram"></i></a>
                            <a href="https://www.youtube.com/@artistikcityYT" target="_blank" rel="noopener" aria-label="YouTube"><i className="fa fa-youtube-play"></i></a>
                            <a href="https://in.linkedin.com/in/vaishali-jetly-goswami-4b5874233" target="_blank" rel="noopener" aria-label="LinkedIn"><i className="fa fa-linkedin"></i></a>
                        </div>
                    </div>
                    <div>
                        <h6>Mediums</h6>
                        <ul>
                            {mediums.map((m) => <li key={m.id}><a href={catalogUrl({ medium: m.id })}>{m.name}</a></li>)}
                            <li><a href={catalogUrl({})}>All courses</a></li>
                        </ul>
                    </div>
                    <div>
                        <h6>Learn</h6>
                        <ul>
                            <li><a href={catalogUrl({ type: 'course' })}>Live courses</a></li>
                            <li><a href={catalogUrl({ type: 'workshop' })}>Workshops</a></li>
                            {AGE_GROUPS.map((a) => <li key={a}><a href={catalogUrl({ age: a })}>{a.split(' - ')[0]} courses</a></li>)}
                        </ul>
                    </div>
                    <div>
                        <h6>ArtistikCity</h6>
                        <ul>
                            <li><a href={route('commission.step1')}>Artist Commission Portal</a></li>
                            <li><a href={route('artistikcity.vision')}>Our vision</a></li>
                            <li><a href={route('how.it.works')}>How it works</a></li>
                            <li><a href={route('instructor')}>Our instructor</a></li>
                            <li><a href={route('student.feedback')}>Student gallery</a></li>
                            <li><a href="/student-shop">Student shop</a></li>
                            <li><a href={route('studio.stories')}>Studio Stories</a></li>
                            <li><a href="https://studiovaishaliarts.com/" target="_blank" rel="noopener">Studio Vaishali Arts</a></li>
                        </ul>
                    </div>
                    <div>
                        <h6>Support</h6>
                        <ul>
                            <li><a href={route('faq')}>FAQ</a></li>
                            <li><a href={route('customer.support')}>Help centre</a></li>
                            <li><a href={route('contact')}>Contact us</a></li>
                            <li><a href={route('join')}>Join for free</a></li>
                        </ul>
                    </div>
                </div>
                <div className="dm-footer__bottom">
                    <span>&copy; {year} ArtistikCity. All rights reserved.</span>
                    <nav>
                        <a href={route('terms.conditions')}>Terms of use</a>
                        <a href={route('privacy.policy')}>Privacy policy</a>
                        <a href={route('contact')}>Contact</a>
                    </nav>
                </div>
            </div>
        </footer>
    );
}
