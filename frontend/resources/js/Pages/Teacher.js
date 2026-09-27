import { usePage } from "@inertiajs/inertia-react";
import React, {useState} from "react";
import Breadcrumb from '@/Components/Breadcrumb';
import Footer from '@/Components/Footer';
import Header from '@/Components/Header';
import Newsletter from "@/Components/Newsletter";
import Tabs from '@/Components/Tabs';
import { FacebookShareButton, TwitterShareButton, LinkedinShareButton, WhatsappShareButton, FacebookIcon, TwitterIcon, LinkedinIcon, WhatsappIcon } from "react-share";
import { COURSE_TAGS_COLOR } from '@/Components/Constants';

export default function Teacher(){
    const {teacher, teachingCources} = usePage().props;
    console.log(teacher);
    const profileUrl = `${window.location.protocol}//${window.location.hostname}/teacher/${teacher.slug}`;

    const profileData = (
        <div className="tabcontent2 profile">
            <h2>{teacher.profile_title}</h2>
            <p dangerouslySetInnerHTML={{__html: teacher.profile_description}}></p>
        </div>
    );

    const coursesData = (
        <div className="tabcontent2">
            <h2>Teaching Courses</h2>                 
            {teachingCources.length != 0 && (
                <div className="enrolledCourse">
                    {teachingCources.map((courses, i) => {
                        return (
                            <div className="column">
                                <div className="coursethumb">
                                    <img src={(`/storage/uploads/courses/${courses.id}/${courses.photo}`)} alt={courses.title} />
                                </div>
                                <div className="courseContent">
                                    <label className="categorybtn" style={{background: `${COURSE_TAGS_COLOR.medium}`}}>{courses.medium}</label>
                                    
                                    <h3>{courses.title}</h3>
                                    <h5>Instructor: {courses.teacher}</h5>
                                    <ul>
                                        <li><i><img src="/assets/images/user-icon.png" alt="" /></i> Age: {courses.age_group}</li>
                                        <li><i><img src="/assets/images/intermediate-icon.png" alt="" /></i> Skill: {courses.skill}</li>
                                        <li><i><img src="/assets/images/date-icon.png" alt="" /></i> {courses.duration} weeks / {courses.time_required} hrs a week</li>
                                    </ul>
                                </div>
                            </div> 
                        );
                    })}
                </div>
            )}

            {teachingCources.length == 0 && (
                <div className="enrolledCourse">
                    {teacher.name} have not enrolled for any courses.
                </div>
            )}
        </div>
    );

    const tabsData = [
        {
            id: 1,
            tabTitle: 'Profile',
            title: '',
            content: profileData
        },
        {
            id: 2,
            tabTitle: 'Courses',
            title: '',
            content: coursesData
        }
    ];

    const breadcrumbs = [
        { path: 'welcome', breadcrumb: 'Home' },
        { path: 'instructor', breadcrumb: 'Instructor'},
    ];
    const [crumbs, setCrumbs] = useState(breadcrumbs);
    const selected = crumb => { console.log(crumb); }
    return (
        <>
        <Header />
            <Breadcrumb crumbs={crumbs} selected={selected} />
            <div className="studentbanner" style={{backgroundImage: `url(/assets/images/profile-banner.jpg)`}}>
                <div className="container">
                    <div className="img">
                        <img src={(`/storage/uploads/teachers/${teacher.id}/${teacher.profile_photo}`)} alt={(`${teacher.name}`)} />
                    </div>
                    <div className="stinfo">
                        <h1>{teacher.name}</h1>
                        {/* <p>@kianna_p2021</p> */}
                        <p><img src="/assets/images/mappin-icon.png" alt="" /> {teacher.location}</p>
                        <div className="action">
                            {/* <a href="#" className="btn"><i className="msgicon"></i> Message</a> */}
                            {/* <a href="#" className="btn"><i className="shareicon"></i> Share Profile</a> */}
                            <FacebookShareButton title={teacher.profile_title} url={profileUrl} hashtags={["hashtag1", "hashtag2"]}>
                                <FacebookIcon size={32} round /> 
                            </FacebookShareButton>&nbsp;
                            <TwitterShareButton title={teacher.profile_title} url={profileUrl} hashtags={["hashtag1", "hashtag2"]}>
                                <TwitterIcon size={32} round /> 
                            </TwitterShareButton>&nbsp;
                            <LinkedinShareButton title={teacher.profile_title} url={profileUrl} hashtags={["hashtag1", "hashtag2"]}>
                                <LinkedinIcon size={32} round /> 
                            </LinkedinShareButton>&nbsp;
                            <WhatsappShareButton title={teacher.profile_title} url={profileUrl} hashtags={["hashtag1", "hashtag2"]}>
                                <WhatsappIcon size={32} round /> 
                            </WhatsappShareButton>
                        </div>
                    </div>
                </div>
            </div>

            <div className="studentInfo">
                <div className="container">
                    <Tabs tabsData={tabsData} />
                    {/* <div className="tabswrap">
                        <ul className="notabs">
                            <li className="tab-active"><a href="#tab-1">Profile</a></li>
                            <li><a href="#tab-2">Courses</a></li>
                        </ul>

                        <div className="stcontent">
                            <div id="tab-1" className="tabcontent2 profile">
                                <h2>{teacher.profile_title}</h2>
                                <p dangerouslySetInnerHTML={{__html: teacher.profile_description}}></p>
                            </div>
                            <div id="tab-2" className="tabcontent2">
                                <h2>Teaching Courses</h2>

                                
                                    {teachingCources.length != 0 && (
                                        <div className="enrolledCourse">
                                            {teachingCources.map((courses, i) => {
                                                return (
                                                    <div className="column">
                                                        <div className="coursethumb">
                                                            <img src={(`/storage/uploads/courses/${courses.id}/${courses.photo}`)} alt={courses.title} />
                                                        </div>
                                                        <div className="courseContent">
                                                            <label className="categorybtn" style={{background: `${COURSE_TAGS_COLOR.medium}`}}>{courses.medium}</label>
                                                            
                                                            <h3>{courses.title}</h3>
                                                            <h5>Instructor: {courses.teacher}</h5>
                                                            <ul>
                                                                <li><i><img src="/assets/images/user-icon.png" alt="" /></i> Age: {courses.age_group}</li>
                                                                <li><i><img src="/assets/images/intermediate-icon.png" alt="" /></i> Skill: {courses.skill}</li>
                                                                <li><i><img src="/assets/images/date-icon.png" alt="" /></i> {courses.duration} weeks / {courses.time_required} hrs a week</li>
                                                            </ul>
                                                        </div>
                                                    </div> 
                                                );
                                            })}
                                        </div>
                                    )}

                                    {teachingCources.length == 0 && (
                                        <div className="enrolledCourse">
                                            {teacher.name} have not enrolled for any courses.
                                        </div>
                                    )}

                            </div>
                        </div>
                    </div> */}
                </div>
            </div>
            <Newsletter />
            <Footer />
        </>
    );
}