import React from 'react';
import Authenticated from '@/Layouts/Authenticated';
import { Head, usePage } from '@inertiajs/inertia-react';
import StudentHeader from "@/Components/Students/StudentHeader";
import StudentNavigation from "@/Components/Students/StudentNavigation";
import { COURSE_TAGS_COLOR } from '@/Components/Constants';

export default function Dashboard(props) {
    const { enrolledCourses } = usePage().props;

    const courses = enrolledCourses.filter((courses) => courses.course_type_id === 1)
    const workshops = enrolledCourses.filter((courses) => courses.course_type_id === 2)

    return (
        <>
            <StudentHeader auth={props.auth} />
            <Authenticated
                auth={props.auth}
                errors={props.errors}
                header={<h2 className="font-semibold text-xl text-gray-800 leading-tight">Dashboard</h2>}
            >
                <Head title="Dashboard" />
                {/* <div className="py-12">
                <div className="max-w-7xl mx-auto sm:px-6 lg:px-8">
                    <div className="bg-white overflow-hidden shadow-sm sm:rounded-lg">
                        <div className="p-6 bg-white border-b border-gray-200">You're logged in!</div>
                    </div>
                </div>
            </div> */}
                {/* Main Section */}

                <div className="container-fluid">
                    <div className="row">
                        <StudentNavigation />
                        <main className="col-md-10 px-0 ms-sm-auto rightsidebar">
                            <div className="homepage p30">
                                {/*<div className="courseSection">
                                    <h6 className="sectionTitle">Latest Activity</h6>
                                    <div className="row">
                                        <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                            <div className="course_img"><img src="/assets/user/images/wonder_woman.png" alt="" /></div>
                                            <div className="course_info me-auto">
                                                <label className="category pink">CHARCOAL</label>
                                                <h3>Charcoal Drawing Foundation</h3>
                                                <h5>3 Module, 1 Project</h5>
                                                <h6>4 Week Course</h6>
                                            </div>
                                            <div className="course_actn">
                                                <a href="#" className="btn btn-primary">CONTINUE</a>
                                            </div>
                                        </div>
                                    </div>
                                </div>*/}

                                <div className="courseSection mt-5">
                                    <h6 className="sectionTitle">ENROLLED COURSES</h6>
                                    <div className="row">
                                        {courses.map((enrolledCourse, i) => {
                                            return (
                                                <div className="col-md-12 d-flex courseInfo">
                                                    <div className="course_img">
                                                        <img src={(`/storage/uploads/courses/${enrolledCourse.course_id}/${enrolledCourse.photo}`)} alt="" />
                                                    </div>
                                                    <div className="course_info me-auto">
                                                        <label className="categorybtn" style={{background: `${COURSE_TAGS_COLOR.medium}`}}>{enrolledCourse.medium}</label>
                                                        <label className="categorybtn" style={{background: `${COURSE_TAGS_COLOR.age}`}}>{enrolledCourse.age_group}</label>
                                                        <label className="categorybtn" style={{background: `${COURSE_TAGS_COLOR.skill}`}}>{enrolledCourse.skill}</label>
                                                        <h3>{enrolledCourse.title}</h3>
                                                        <p>{enrolledCourse.sub_title}</p>
                                                        <h6>{enrolledCourse.duration} Week Course</h6>
                                                    </div>
                                                    <div className="course_actn">
                                                        <a href={route('user.course.home', enrolledCourse.order_id)} className="btn btn-primary">COURSE HOME</a>
                                                    </div>
                                                </div>
                                            );
                                        })}

                                        {courses.length == 0 && (
                                            <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                                You have not enrolled for any courses.
                                            </div>
                                        )}
                                    </div>
                                </div>

                                <div className="courseSection mt-5">
                                    <h6 className="sectionTitle">ENROLLED WORKSHOP</h6>
                                    <div className="row">
                                        {workshops.map((enrolledWorkshop, i) => {
                                                return (
                                                    <div className="col-md-12 d-flex courseInfo">
                                                        <div className="course_img">
                                                            <img src={(`/storage/uploads/courses/${enrolledWorkshop.course_id}/${enrolledWorkshop.photo}`)} alt="" />
                                                        </div>
                                                        <div className="course_info me-auto">
                                                            <label className="categorybtn" style={{background: `${COURSE_TAGS_COLOR.medium}`}}>{enrolledWorkshop.medium}</label>
                                                            <label className="categorybtn" style={{background: `${COURSE_TAGS_COLOR.age}`}}>{enrolledWorkshop.age_group}</label>
                                                            <label className="categorybtn" style={{background: `${COURSE_TAGS_COLOR.skill}`}}>{enrolledWorkshop.skill}</label>
                                                            <h3>{enrolledWorkshop.title}</h3>
                                                            <p>{enrolledWorkshop.sub_title}</p>
                                                            <h6>{enrolledWorkshop.duration} DAYS WORKSHOP</h6>
                                                        </div>
                                                        <div className="course_actn">
                                                            <a href={route('user.course.home', enrolledWorkshop.order_id)} className="btn btn-primary">WORKSHOP HOME</a>
                                                        </div>
                                                    </div>
                                                );
                                        })}
                                        { workshops.length == 0 && (
                                            <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                                You have not enrolled for any workshop.
                                            </div>
                                        )}
                                    </div>
                                </div>

                            </div>
                        </main>
                    </div>
                </div>
                {/* Main Section */}
            </Authenticated>
        </>
    );
}
