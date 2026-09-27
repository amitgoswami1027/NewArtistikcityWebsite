import React, { useState } from "react";
import { Link, Head, usePage } from '@inertiajs/inertia-react';
import Breadcrumb from '@/Components/Breadcrumb';
import Footer from '@/Components/Footer';
import Header from '@/Components/Header';

export default function StudentFeedback() {
    const breadcrumbs = [
        { path: 'welcome', breadcrumb: 'Home' },
        { path: 'student.feedback', breadcrumb: 'Student Feedback' },
    ];
    const [crumbs, setCrumbs] = useState(breadcrumbs);
    const selected = crumb => { console.log(crumb); }
    return (
        <>
            <Header />
            <Breadcrumb crumbs={crumbs} selected={selected} />

            <div className="inrbanrtxt" style={{ backgroundImage: `url(/assets/images/cart-banner.jpg)` }}>
                <div className="container">
                <h1>Don't Take Our Word.<br /> Hear From Our Students.</h1>
                </div>
            </div>

            <div className="container">
                <div className="feedbacktpage">
                    <div className="fdrow">
                        <div className="poster"><img src="assets/images/feedback-image-1.jpg" alt="" /></div>
                        <div className="text">
                            <div className="rating">
                                <i className="fa fa-star"></i><i className="fa fa-star"></i><i className="fa fa-star"></i><i className="fa fa-star"></i><i className="fa fa-star"></i>
                            </div>
                            <h4>"I liked the demonstration of techniques. It was a Fantastic Course!!!"</h4>
                            <p><a href="#">Charcoal drawing secrets revealed</a></p>
                            <h5>Rashmi</h5>
                            <h6>Hyderabad</h6>
                        </div>
                    </div>

                    <div className="fdrow">
                        <div className="poster"><img src="assets/images/feedback-image-2.jpg" alt="" /></div>
                        <div className="text">
                            <div className="rating">
                                <i className="fa fa-star"></i><i className="fa fa-star"></i><i className="fa fa-star"></i><i className="fa fa-star"></i><i className="fa fa-star"></i>
                            </div>
                            <h4>"Me and my son attended vaishali's painting workshop and I must say it is a must attend painting party ever Vaishali was very supportive in helping out in each and every step."</h4>
                            <p><a href="#">Charcoal drawing secrets revealed</a></p>
                            <h5>Shruti</h5>
                            <h6>Hyderabad</h6>
                        </div>
                    </div>

                    <div className="fdrow">
                        <div className="poster"><img src="assets/images/feedback-image-3.jpg" alt="" /></div>
                        <div className="text">
                            <div className="rating">
                                <i className="fa fa-star"></i><i className="fa fa-star"></i><i className="fa fa-star"></i><i className="fa fa-star"></i><i className="fa fa-star"></i>
                            </div>
                            <h4>"I strongly recommend Studio Arts to all the Art Enthusiasts. I am a beginner to painting and attended few workshops of Vaishali which I thoroughly enjoyed. All the details of making an oil painting."</h4>
                            <p><a href="#">Charcoal drawing secrets revealed</a></p>
                            <h5>Chitra Nalini</h5>
                            <h6>Hyderabad</h6>
                        </div>
                    </div>
                </div>
            </div>

            <Footer />
        </>
    );
}