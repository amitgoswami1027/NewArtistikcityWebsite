import React, { useEffect, useState } from 'react';
import Authenticated from '@/Layouts/Authenticated';
import { Head, usePage } from '@inertiajs/inertia-react';
import StudentHeader from "@/Components/Students/StudentHeader";

export default function CourseHome(props) {
    const { FreeCoursesData, FreeCoursesMenu } = usePage().props;
    const [videoTitle, setVideoTitle] = useState(FreeCoursesMenu.videos[0].video_title);
    const [video, setVideo] = useState('<iframe width="1100" height="620" src="'+FreeCoursesMenu.videos[0].video_url+'" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>');

    const clickEvent = (e) => {
        e.preserveState;
        let id = e.target.id;
        let dataName = e.target.getAttribute('data-name');
        let dataVal = e.target.getAttribute('data-val');
        console.log(dataVal);
        let videoUrl = '<iframe width="1100" height="620" src="'+dataVal+'" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>';
        setVideo(videoUrl); 
        setVideoTitle(dataName);
    }

    return (
        <>
            <StudentHeader auth={props.auth} />
            <Authenticated
                auth={props.auth}
                errors={props.errors}
                header={<h2 className="font-semibold text-xl text-gray-800 leading-tight">Dashboard</h2>}
            >
                <Head title="Course Home" />
                {/* Main Section */}
                <div className="container-fluid">
                    <div className="row">
                        <div className="col-md-2 px-0 sidebar coursesidebar">
                            <div className="courseTitle">{FreeCoursesMenu.title} <button className="navbar-toggler d-md-none collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#sidebarMenu" aria-controls="sidebarMenu" aria-expanded="false" aria-label="Toggle Navigation"> <span className="navbar-toggler-icon"></span> </button></div>
                            <nav id="sidebarMenu" className="w-100 d-md-block collapse">
                                <div className=" position-sticky">
                                    <ul className="coursenav">
                                        <li> <a className="active" href="#">COURSE HOME</a> </li>
                                        {FreeCoursesMenu.syllabus != "" && (<li> <a href="#">SYLLABUS</a> </li>)}
                                        <li className="dropdown"> 
                                            {FreeCoursesMenu.type != "" && (<button className="accordion-button" type="button" data-bs-toggle="collapse" data-bs-target="#collapseOne" aria-expanded="true" aria-controls="collapseOne">{FreeCoursesMenu.type}</button>)}
                                            <div id="collapseOne" className="accordion-collapse collapse show">
                                                    {FreeCoursesMenu.videos.length != 0 && (
                                                        <ul>
                                                        {FreeCoursesMenu.videos.map((video, i) => {
                                                            return (
                                                                <li><a onClick={clickEvent} id={i} data-name={video.video_title} data-val={video.video_url}  style={{color: `#FFFFFF`}}>{i+1} {video.video_title}</a></li>
                                                            );
                                                        })};
                                                        </ul>
                                                    )}
                                            </div>
                                        </li>
                                    </ul>
                                </div>
                            </nav>
                        </div>
                        <main className="col-md-10 px-0 ms-sm-auto rightsidebar">
                            <div className="siteBreadcrumb">
                                <nav aria-label="breadcrumb">
                                    <ol className="breadcrumb">
                                        <li className="breadcrumb-item"><a href={route('user.dashboard')}>My classroom</a></li>
                                        <li className="breadcrumb-item"><a href={route('user.free.courses')}>Free Courses</a></li>
                                        <li className="breadcrumb-item active" aria-current="page">{FreeCoursesData.title}</li>
                                    </ol>
                                </nav>
                            </div>
                            <div className="aboutCourse p30">
                                <div className="row">
                                    <div className="col-md-10 px-0 pe-5">
                                        <h2>{FreeCoursesData.title}</h2>
                                        <p dangerouslySetInnerHTML={{__html: FreeCoursesData.description}}></p>
                                    </div>
                                    <div className="col-md-2 d-flex justify-content-end px-0">
                                        <ul className="crsList">
                                            <li><img src="assets/images/module-icon.svg" alt="" /> {FreeCoursesMenu.videos.length} Videos</li>
                                            {/* <li><img src="assets/images/project-icon.svg" alt="" /> 1 Project</li> */}
                                            {/* <li><img src="assets/images/week-icon.svg" alt="" /> 4 Weeks</li> */}
                                        </ul>
                                    </div>
                                </div>
                            </div>
                            <div className="todoSctn p30">
                                <div className="courseSection">
                                    <h6 className="sectionTitle">{videoTitle}</h6>
                                    <div className="row">
                                        <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                        <div dangerouslySetInnerHTML={{__html: video}}></div>
                                            {/* <div className="course_info me-auto">
                                                <h3>Module 2 : Principle Of Good Drawing</h3>
                                                <h5>Lesson 1 : Value & its Importance</h5>
                                            </div>
                                            <div className="course_actn">
                                                <a href="#" className="btn btn-primary">CONTINUE</a>
                                            </div> */}
                                        </div>
                                        {/* <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                            <div className="course_info me-auto">
                                                <h3><label>Project :</label> <span>One Still Life Composition in a professional manner<br /> from scratch to finish</span></h3>
                                            </div>
                                            <div className="course_actn">
                                                <a href="#" className="btn btn-primary">GO TO PROJECT</a>
                                            </div>
                                        </div> */}
                                    </div>
                                </div>
                                <div className="courseSection mt-5">
                                    <h6 className="sectionTitle">CERTIFICATE</h6>
                                    <div className="row">
                                        <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                            <div className="course_img wauto"><img src="assets/images/certificate-image.png" alt="" /></div>
                                            <div className="course_info me-auto">
                                                <h3>Download Certificate</h3>
                                                <p>Charcoal Drawing Foundation</p>
                                            </div>
                                            <div className="course_actn">
                                                <a href="#" className="btn btn-primary downloadbtn">DOWNLOAD</a>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div className="helpBotm">Need Help? Check on FAQ or contact <a href={route('user.help')}>here</a></div>
                        </main>
                    </div>
                </div>
                {/* Main Section */}
            </Authenticated>
        </>
    );
}
