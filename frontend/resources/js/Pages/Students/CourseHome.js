import React from 'react';
import Authenticated from '@/Layouts/Authenticated';
import { Head, usePage, Link } from '@inertiajs/inertia-react';
import StudentHeader from "@/Components/Students/StudentHeader";
import StudentNavigation from "@/Components/Students/StudentNavigation";
import CourseNavigation from '@/Components/Students/CourseNavigation';
import { FacebookShareButton, TwitterShareButton, LinkedinShareButton, FacebookIcon, TwitterIcon, LinkedinIcon } from "react-share";

export default function CourseHome(props) {
    const {CourseDetails, CourseNav} = usePage().props;
    
    const coursesData = CourseDetails['coursesData'];
    const courseModules = CourseDetails['courseModules'];
    const courseProject = CourseDetails['courseProject'];
    
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
                        <CourseNavigation courseNav={CourseNav} />
                        {/* <div className="col-md-2 px-0 sidebar coursesidebar">
                            <div className="courseTitle">{coursesData.title} <button className="navbar-toggler d-md-none collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#sidebarMenu" aria-controls="sidebarMenu" aria-expanded="false" aria-label="Toggle Navigation"> <span className="navbar-toggler-icon"></span> </button></div>
                            <nav id="sidebarMenu" className="w-100 d-md-block collapse">
                                <div className=" position-sticky">
                                    <ul className="coursenav">
                                        <li> <a className="active" href={route('user.courses')}>COURSE HOME</a> </li>
                                        <li> <a href={route('user.course.syllabus', coursesData.course_id)}>SYLLABUS</a> </li>
                                        {courseModules.length != 0 && (
                                        <li className="dropdown"> 
                                            <button className="accordion-button" type="button" data-bs-toggle="collapse" data-bs-target="#collapseOne" aria-expanded="true" aria-controls="collapseOne">MODULES</button> 
                                            <div id="collapseOne" className="accordion-collapse collapse show">
                                                <ul>
                                                    {courseModules.map((module, i) => {
                                                        return (
                                                            <li><a href={route('user.course.modules', module.id)}>{i+1}. {module.module_title}</a></li>
                                                        );
                                                    })}
                                                </ul>
                                            </div>
                                        </li>
                                        )}
                                    </ul>
                                </div>
                            </nav>
                        </div> */}
                        <main className="col-md-10 px-0 ms-sm-auto rightsidebar">

                            <div className="siteBreadcrumb">
                                <nav aria-label="breadcrumb">
                                    <ol className="breadcrumb">
                                        <li className="breadcrumb-item"><a href={route('user.dashboard')}>My Classroom</a></li>
                                        <li className="breadcrumb-item"><a href={route('user.courses')}>Course</a></li>
                                        <li className="breadcrumb-item active" aria-current="page">{coursesData.length != 0 && (coursesData.title)}</li>
                                    </ol>
                                </nav>
                            </div>

                            <div className="aboutCourse p30">
                            {coursesData.length != 0 && (
                                <div className="row">
                                    <div className="col-md-10 px-0 pe-5">
                                        <h2>{coursesData.title}</h2>
                                        <p>{coursesData.sub_title}</p>
                                    </div>
                                    <div className="col-md-2 d-flex justify-content-end px-0">
                                        <ul className="crsList">
                                            <li>
                                                <img src="/assets/user/images/module-icon.svg" alt="" /> 
                                                
                                            </li>
                                            <li>
                                                <img src="/assets/user/images/project-icon.svg" alt="" /> 
                                                
                                            </li>
                                            <li>
                                                <img src="/assets/user/images/week-icon.svg" alt="" /> 
                                                
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                            )}
                            </div>

                            <div className="todoSctn p30">
                                {courseModules.length != 0 && (
                                <div className="courseSection">
                                    <h6 className="sectionTitle">To DO</h6>
                                    <div className="row">
                                        {courseModules.map((module, i) => {
                                            return (
                                                <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                                    <div className="course_info me-auto">
                                                        <h3>Module {i+1}: {module.module_title}</h3>
                                                        {module.lessons.map((lesson, j) => {
                                                            return (
                                                                <h5>Lesson {j+1} : {lesson.lesson_title}</h5>
                                                            );
                                                        })}
                                                    </div>
                                                    <div className="course_actn">
                                                        <a href={route('user.course.modules', module.id)} className="btn btn-primary">CONTINUE</a>
                                                    </div>
                                                </div>
                                            );
                                        })}

                                        {courseProject.map((project, j) => {
                                            return (
                                                <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                                    <div className="course_info me-auto">
                                                        <h3><label>Project :</label> <span>{project.project_title}</span></h3>
                                                    </div>
                                                    <div className="course_actn">
                                                        <a href="#" className="btn btn-primary">GO TO PROJECT</a>
                                                    </div>
                                                </div>
                                            )
                                        })}
                                    </div>
                                </div>
                                )}
                                {coursesData.length != 0 && (
                                <div className="courseSection mt-5">
                                    <h6 className="sectionTitle">CERTIFICATE</h6>
                                    <div className="row">
                                        <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                            <div className="course_img wauto"><img src="/assets/user/images/certificate-image.png" alt="" /></div>
                                            <div className="course_info me-auto">
                                                <h3>Download Certificate</h3>
                                                <p>{coursesData.length != 0 && (coursesData.title)}</p>
                                                {coursesData.issue_certificate == 1 && (
                                                    <p>
                                                        <FacebookShareButton title={coursesData.certificate_text} url={coursesData.certificate_facebook_share_url} hashtags={["hashtag1", "hashtag2"]}>
                                                            <FacebookIcon size={32} round /> 
                                                        </FacebookShareButton>&nbsp;
                                                        <TwitterShareButton title={coursesData.certificate_text} url={coursesData.certificate_twitter_share_url} hashtags={["hashtag1", "hashtag2"]}>
                                                            <TwitterIcon size={32} round /> 
                                                        </TwitterShareButton>&nbsp;
                                                        <LinkedinShareButton title={coursesData.certificate_text} url={coursesData.certificate_linkedin_share_url} hashtags={["hashtag1", "hashtag2"]}>
                                                            <LinkedinIcon size={32} round /> 
                                                        </LinkedinShareButton>&nbsp;
                                                    </p>
                                                )}
                                            </div>
                                            {coursesData.issue_certificate == 1 && (
                                                <div className="course_actn">
                                                    <a
                                                        href={route('user.certificate.download', {'id': coursesData.order_id})}
                                                        method="get"
                                                        className="btn btn-primary downloadbtn"
                                                        // data={{ id: coursesData.order_id }}
                                                    >
                                                        DOWNLOAD
                                                    </a>
                                                    {/* <a href={route('user.certificate', coursesData.course_id)} target="_blank" className="btn btn-primary downloadbtn">DOWNLOAD</a> */}
                                                </div>
                                            )}
                                            {coursesData.issue_certificate == 0 && (
                                                <div className="course_actn">
                                                    Certificate not issued yet!
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </div>
                                )}
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
