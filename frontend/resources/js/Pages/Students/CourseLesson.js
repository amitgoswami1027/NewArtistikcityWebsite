import React from 'react';
import Authenticated from '@/Layouts/Authenticated';
import { Head, usePage } from '@inertiajs/inertia-react';
import StudentHeader from "@/Components/Students/StudentHeader";
import StudentNavigation from "@/Components/Students/StudentNavigation";
import CourseNavigation from '@/Components/Students/CourseNavigation';
import { Link } from '@inertiajs/inertia-react';

export default function CourseHome(props) {
    const { lessonDetails, lessonNav } = usePage().props;
    console.log(lessonDetails);
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
                            <div className="courseTitle">{lessonNav.course_title}
                                <button className="navbar-toggler d-md-none collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#sidebarMenu" aria-controls="sidebarMenu" aria-expanded="false" aria-label="Toggle Navigation"> <span className="navbar-toggler-icon"></span> </button>
                            </div>
                            <nav id="sidebarMenu" className="w-100 d-md-block collapse">
                                <div className=" position-sticky">
                                    <ul className="coursenav">
                                    {lessonNav.course_module_lesson.length != 0 && (
                                        <li className="dropdown">
                                            <button className="accordion-button" type="button" data-bs-toggle="collapse" data-bs-target="#collapseOne" aria-expanded="true" aria-controls="collapseOne">{lessonNav.course_module_title}</button>
                                            <div id="collapseOne" className="accordion-collapse collapse show">
                                                <ul>
                                                    {lessonNav.course_module_lesson.map((lesson, i) => {
                                                        return (
                                                            <li>
                                                                <Link href={route('user.course.module.lesson', lesson.module_id)}  data={{page: i+1}}>{i+1}. {lesson.lesson_title}</Link>
                                                            </li>
                                                        );
                                                    })}
                                                </ul>
                                            </div>
                                        </li>
                                        )}
                                    </ul>
                                </div>
                            </nav>
                        </div>
                        <main className="col-md-10 px-0 ms-sm-auto rightsidebar">
                            <div className="siteBreadcrumb">
                                <nav aria-label="breadcrumb">
                                    <ol className="breadcrumb">
                                        <li className="breadcrumb-item"><a href={route('user.dashboard')}>My Classroom</a></li>
                                        <li className="breadcrumb-item"><a href={route('user.courses')}>Course</a></li>
                                        <li className="breadcrumb-item"><a href={route('user.course.home', lessonNav.course_id)}>{lessonNav.course_title}</a></li>
                                        <li className="breadcrumb-item"><a href={route('user.course.syllabus', lessonNav.course_id)}>Syllabus</a></li>
                                        <li className="breadcrumb-item" aria-current="page">Modules</li>
                                        <li className="breadcrumb-item active" aria-current="page">{lessonNav.course_module_title}</li>
                                    </ol>
                                </nav>
                            </div>
                            {/* <div className="aboutCourse p30">
                                <div className="row align-items-center">
                                    <div className="col-md-10 px-0 pe-5"></div>
                                    <div className="col-md-2 px-0 d-flex align-items-center justify-content-end">
                                        <div className="prev_next">
                                            {lessonDetails.prev_page_url != null && ( <Link href={`${lessonDetails.prev_page_url != null && lessonDetails.prev_page_url}`}><button className="prevbtn"><img src="/assets/user/images/prev-button.svg" alt="" /></button></Link>)}
                                            {lessonDetails.prev_page_url == null && ( <button className="prevbtn"><img src="/assets/user/images/prev-button.svg" alt="" /></button>)}
                                            {lessonDetails.next_page_url != null && ( <Link href={`${lessonDetails.next_page_url != null && lessonDetails.next_page_url}`}><button className="nextbtn"><img src="/assets/user/images/next-button.svg" alt="" /></button></Link>)}
                                            {lessonDetails.next_page_url == null && ( <button className="nextbtn"><img src="/assets/user/images/next-button.svg" alt="" /></button>)}
                                        </div>
                                    </div>
                                </div>
                            </div> */}
                            {lessonDetails.data.map((lessonDetail, i) => {
                                return (
                                    <>
                                        <div className="aboutCourse p30">
                                            <div className="row align-items-center">
                                                <div className="col-md-10 px-0 pe-5">
                                                    <h2>Lesson {i+1}: {lessonDetail.lesson_title}</h2>
                                                </div>
                                                <div className="col-md-2 px-0 d-flex align-items-center justify-content-end">
                                                    <div className="prev_next">
                                                        {lessonDetails.prev_page_url != null && ( <Link href={`${lessonDetails.prev_page_url != null && lessonDetails.prev_page_url}`}><button className="prevbtn"><img src="/assets/user/images/prev-button.svg" alt="" /></button></Link>)}
                                                        {lessonDetails.prev_page_url == null && ( <button className="prevbtn"><img src="/assets/user/images/prev-button.svg" alt="" /></button>)}
                                                        {lessonDetails.next_page_url != null && ( <Link href={`${lessonDetails.next_page_url != null && lessonDetails.next_page_url}`}><button className="nextbtn"><img src="/assets/user/images/next-button.svg" alt="" /></button></Link>)}
                                                        {lessonDetails.next_page_url == null && ( <button className="nextbtn"><img src="/assets/user/images/next-button.svg" alt="" /></button>)}
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                        <div className="course_detail allModule p30">

                                            <div className="detailContent" dangerouslySetInnerHTML={{__html: lessonDetail.lesson_description}}></div>
                                            {lessonDetail.tasks.map((taskList, j) => {
                                                return (
                                                    <>
                                                        <div className="courseSection mt-2">
                                                            <div className="row">
                                                                <div className="col-md-12 d-flex align-items-center justify-content-between courseInfo">
                                                                    <div className="course_info me-auto">
                                                                        <h5>TASK {j+1}</h5>
                                                                        <h3>{taskList.task_title}</h3>
                                                                        <p dangerouslySetInnerHTML={{__html: taskList.task_description}}></p>
                                                                    </div>
                                                                    <div className="moduleActn">
                                                                        <a className="btn btn-primary" href={route('user.course.module.lesson.task', taskList.id)}>Submit</a>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </>
                                                )
                                            })}


                                            <div className="row mt-4 align-items-center">
                                                <div className="col-md-6 px-0">
                                                    {(lessonDetail.lesson_pdf != null) && ( 
                                                        <>
                                                            <a href={(`/storage/uploads/courses/lesson/${lessonDetail.id}/${lessonDetail.lesson_pdf}`)} target="_blank" className="btn btn-primary">Download Pdf</a>
                                                        </>
                                                    )}
                                                </div>
                                                <div className="col-md-6 px-0 d-flex align-items-center justify-content-end">
                                                    <div className="prev_next">
                                                        {(lessonDetails.prev_page_url != null) && ( <Link href={`${lessonDetails.prev_page_url != null && lessonDetails.prev_page_url}`}><button className="prevbtn"><img src="/assets/user/images/prev-button.svg" alt="" /></button></Link>)}
                                                        {(lessonDetails.prev_page_url == null) && ( <button className="prevbtn"><img src="/assets/user/images/prev-button.svg" alt="" /></button>)}
                                                        {(lessonDetails.next_page_url != null) && ( <Link href={`${lessonDetails.next_page_url != null && lessonDetails.next_page_url}`}><button className="nextbtn"><img src="/assets/user/images/next-button.svg" alt="" /></button></Link>)}
                                                        {(lessonDetails.next_page_url == null) && ( <button className="nextbtn"><img src="/assets/user/images/next-button.svg" alt="" /></button>)}
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </>
                                );
                            })}
                        </main>
                    </div>
                </div>
                {/* Main Section */}

            </Authenticated>
        </>
    );
}
