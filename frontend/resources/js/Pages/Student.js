import { usePage } from "@inertiajs/inertia-react";
import React, {useState} from "react";
import Breadcrumb from '@/Components/Breadcrumb';
import Footer from '@/Components/Footer';
import Header from '@/Components/Header';
import Newsletter from "@/Components/Newsletter";
import Tabs from '@/Components/Tabs';
import { FacebookShareButton, TwitterShareButton, LinkedinShareButton, WhatsappShareButton, FacebookIcon, TwitterIcon, LinkedinIcon, WhatsappIcon } from "react-share";
import { COURSE_TAGS_COLOR } from '@/Components/Constants';

export default function Student() {
    const { student, enrolledCourses, portfolio } = usePage().props;
    // const openWorkshopList = open_workshops.data;
    console.log(enrolledCourses);

    const profileUrl = `${window.location.protocol}//${window.location.hostname}/student/${student.slug}`;

    const profileData = (
        <div className="tabcontent2 profile">
            <h2>{student.profile_title}</h2>
            <p dangerouslySetInnerHTML={{__html: student.profile_description}}></p>
        </div>
    );

    const coursesData = (
        <div className="tabcontent2">
            <h2>Courses Enrolled</h2>
            {enrolledCourses.length != 0 && (
                <div className="enrolledCourse">
                    {enrolledCourses.map((courses, i) => {
                        return (
                            <div className="column">
                                <div className="coursethumb">
                                    <img src={(`/storage/uploads/courses/${courses.course_id}/${courses.photo}`)} alt={courses.title} />
                                </div>
                                <div className="courseContent">
                                    <label className="categorybtn" style={{background: `${COURSE_TAGS_COLOR.medium}`}}>{courses.medium}</label>
                                    
                                    <h3>{courses.title}</h3>
                                    <h5>Instructor: {courses.teacher}</h5>
                                    <ul>
                                        {/* <li><i><img src="/assets/images/live-icon.png" alt="" /></i> Course Type: Live</li> */}
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

            {enrolledCourses.length == 0 && (
                <div className="enrolledCourse">
                    {student.name} have not enrolled for any courses.
                </div>
            )}
        </div>
    );

    const portfolioData = (
        <div className="tabcontent2">
            <h2>My Portfolio</h2>
            <div className="glryImgs">
                {enrolledCourses.length != 0 && (
                    <ul>
                        {portfolio.map((artwork, i) => {
                            return (<li><a href={(`storage/uploads/artworks/${artwork.id}/${artwork.photo_name}`)}  data-fancybox="group1"><span style={{ backgroundImage: `url(storage/uploads/artworks/${artwork.id}/${artwork.photo_name})` }}></span></a></li>);
                        })}
                    </ul>
                )}
            </div>
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
        },
        {
            id: 3,
            tabTitle: 'Portfolio',
            title: '',
            content: portfolioData
        }
    ];

    const breadcrumbs = [
        { path: 'welcome', breadcrumb: 'Home' },
        { path: 'student', breadcrumb: 'Student'},
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
                        <img src={(`/storage/uploads/students/${student.id}/${student.profile_photo}`)} alt={(`${student.name}`)} />
                    </div>
                    <div className="stinfo">
                        <h1>{student.name}</h1>
                        <p><img src="/assets/images/mappin-icon.png" alt="" />{student.location}</p>
                        <div className="action">
                            {/* <a href="#" className="btn"><i className="msgicon"></i> Message</a> */}
                            {/* <a href="#" className="btn"><i className="shareicon"></i> Share Profile</a> */}
                            <FacebookShareButton title={student.profile_title} url={profileUrl} hashtags={["hashtag1", "hashtag2"]}>
                                <FacebookIcon size={32} round /> 
                            </FacebookShareButton>&nbsp;
                            <TwitterShareButton title={student.profile_title} url={profileUrl} hashtags={["hashtag1", "hashtag2"]}>
                                <TwitterIcon size={32} round /> 
                            </TwitterShareButton>&nbsp;
                            <LinkedinShareButton title={student.profile_title} url={profileUrl} hashtags={["hashtag1", "hashtag2"]}>
                                <LinkedinIcon size={32} round /> 
                            </LinkedinShareButton>&nbsp;
                            <WhatsappShareButton title={student.profile_title} url={profileUrl} hashtags={["hashtag1", "hashtag2"]}>
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
                            <li><a href="#tab-3">Portfolio</a></li>
                        </ul>

                        <div className="stcontent">
                            <div id="tab-1" className="tabcontent2 profile">
                                <h2>{student.profile_title}</h2>
                                <p dangerouslySetInnerHTML={{__html: student.profile_description}}></p>
                            </div>
                            <div id="tab-2" className="tabcontent2">
                                <h2>Courses Enrolled</h2>
                                    {enrolledCourses.length != 0 && (
                                        <div className="enrolledCourse">
                                            {enrolledCourses.map((courses, i) => {
                                                return (
                                                    <div className="column">
                                                        <div className="coursethumb">
                                                            <img src={(`/storage/uploads/courses/${courses.course_id}/${courses.photo}`)} alt={courses.title} />
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

                                    {enrolledCourses.length == 0 && (
                                        <div className="enrolledCourse">
                                            {student.name} have not enrolled for any courses.
                                        </div>
                                    )}
                                

                            </div>
                            <div id="tab-3" className="tabcontent2">
                                <h2>My Portfolio</h2>
                                <div className="glryImgs">
                                    {enrolledCourses.length != 0 && (
                                        <ul>
                                            {portfolio.map((artwork, i) => {
                                             return (<li><a href={(`storage/uploads/artworks/${artwork.id}/${artwork.photo_name}`)}  data-fancybox="group1"><span style={{ backgroundImage: `url(storage/uploads/artworks/${artwork.id}/${artwork.photo_name})` }}></span></a></li>);
                                            })}
                                        </ul>
                                    )}
                                </div>
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