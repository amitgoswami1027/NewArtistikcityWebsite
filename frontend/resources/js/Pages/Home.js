import React from "react";

export default function Home() {
    return (
        <div className="homebanner">
            <div className="container">
                <div className="col50">
                    <h1>Art Learning for <strong>Anyone, Anywhere</strong></h1>
                    <p>Artistikcity is an Art Concept Builder and Provider which help an individual in exploration & growth of their inner artistic.</p>
                    <div className="actn">
                        <a href="#" className="btn">Create your free Account <i className="fa fa-angle-right"></i></a>
                        <a href="#" className="btn blue">Browse Courses <i className="fa fa-angle-right"></i></a>
                    </div>
                </div>
            </div>
            <img className="banrimg" src="assets/images/home-banner.png" alt="" />
        </div>
    );
}
