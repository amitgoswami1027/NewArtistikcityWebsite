import React, {useEffect, useState} from 'react';
import { Inertia } from '@inertiajs/inertia'
import Authenticated from '@/Layouts/Authenticated';
import { Head, usePage, useForm, InertiaLink } from '@inertiajs/inertia-react';
import StudentHeader from "@/Components/Students/StudentHeader";
import StudentNavigation from "@/Components/Students/StudentNavigation";
import CourseNavigation from '@/Components/Students/CourseNavigation';
import { Link } from '@inertiajs/inertia-react';
import { DefaultEditor } from 'react-simple-wysiwyg';

export default function LessonTask(props) {
    const { taskDetails, lessonDetails, lessonNav, messages } = usePage().props;
    const { auth, courseLinks } = usePage().props;
    const links = courseLinks || {};
    // console.log(lessonDetails);
    // console.log(taskDetails);
    // const { data, setData, post, progress, reset } = useForm({
    //     reply: '',
    // })
    const [values, setValues] = useState("");

    const [images, setImages] = useState([]);
    const [imageURLS, setImageURLs] = useState([]);

    useEffect(() => {
        if (images.length < 1) return;
        const newImageUrls = [];
        images.forEach((image) => newImageUrls.push(URL.createObjectURL(image)));
        setImageURLs(newImageUrls);
    }, [images]);

    function onImageChange(e) {
        setImages([...e.target.files]);
    }
    function handleSubmit(e) {
        e.preventDefault()
        Inertia.post(route('user.course.module.lesson.task.save'), {
            "reply": values,
            "images": images,
            "task_id": taskDetails.id,
            "from_id": auth.user.id
        })
    }
    return (
        <>
            <StudentHeader auth={props.auth} />
            <Authenticated
                auth={props.auth}
                errors={props.errors}
                header={<h2 className="font-semibold text-xl text-gray-800 leading-tight">Dashboard</h2>}
            >
                <Head title={`${taskDetails.task_title || 'Task'} · ${links.courseTitle || 'Course'}`} />
                <div className="cl-task">
                    <div className="cl-task__head">
                        <span className="ac-eyebrow">Practice task{links.lessonTitle ? ` · ${links.lessonTitle}` : ''}</span>
                        <h1>{taskDetails.task_title || 'Task'}</h1>
                        <div className="cl-task__links">
                            {links.lesson && <a href={links.lesson} className="dm-btn dm-btn--line"><i className="fa fa-arrow-left" aria-hidden="true"></i>&nbsp; Back to the lesson</a>}
                            {links.overview && <a href={links.overview} className="dm-linkbtn">Course overview</a>}
                        </div>
                    </div>
                    <div>
                        <div>
                            <div className="taskPage p30">
                                <div className="taskLists">
                                    <div className="row">
                                        <div className="user_left">
                                            <img src={(`/storage/uploads/teachers/${taskDetails.admin_id}/${taskDetails.taskTeacherPhoto}`)} alt={(`${taskDetails.taskTeacherAvatar}`)} />
                                            <h3><a target='_blank' href={taskDetails.taskTeacherProfileUrl}>{taskDetails.taskTeacherAvatar}</a></h3>
                                        </div>
                                        <div className="task_right">
                                            <h4>Task: {taskDetails.task_title}</h4>
                                            <p>{taskDetails.task_description}</p>
                                        </div>
                                    </div>

                                    {messages.length != 0 && (
                                        <>
                                        {messages.message.map((message, i) => { return (
                                            
                                        <div className="row">
                                            <div className="user_left">
                                                {message.from_data.profile_photo !== false && (
                                                    <img src={(`${message.from_data.profile_photo}`)} alt={(`${message.from_data.avatar}`)} />
                                                )}
                                                <h3><a target='_blank' href={message.from_data.profile_url}>{message.from_data.avatar}</a></h3>
                                            </div>
                                            <div class="task_right">
                                                {message.from_type == 'Student' && (
                                                    <label class="underreview">{message.review_status}</label>
                                                )}
                                                {/* <h4>Aliquip commodo consequat</h4> */}
                                                <p>{message.reply}</p>
                                                {message.reply_photos.length != 0 && (
                                                <div class="uploadedmedia">
                                                    <h5>Uploaded Media</h5>
                                                    <ul>
                                                        {message.reply_photos.map((reply_photo, i) => { return (
                                                            <li>
                                                                <img src={(`/storage/uploads/courses/task/${reply_photo.student_task_id}/${reply_photo.photo_name}`)} alt={(`${reply_photo.photo_name}`)} width={`121`} height={`81`} />
                                                            </li>
                                                        );})}
                                                    </ul>
                                                </div>
                                                )}
                                                {/* <div class="botmActn">
                                                    <button class="btn btn-primary noicon">Edit</button>
                                                </div> */}
                                            </div>
                                        </div>
                                        );})}
                                        </>
                                    )}


                                    <form onSubmit={handleSubmit}>
                                        <input type={`hidden`} name={`task_id`} value={taskDetails.id}/>
                                        <input type={`hidden`} name={`from_id`} value={auth.user.id}/>
                                        <div className="row">
                                            <div className="user_left">
                                                {messages.student_data.profile_photo !== false && (
                                                    <img src={(`${messages.student_data.profile_photo}`)} alt={(`${messages.student_data.name}`)} />
                                                )}
                                                <h3>{messages.student_data.avatar}</h3>
                                            </div>
                                            <div className="task_right">
                                                <div className="wyswigeditor">
                                                    <DefaultEditor value={values} id={`reply`} name={`reply`} onChange={(e) => {
                                                        setValues(e.target.value);
                                                    }} />
                                                </div>
                                                <div className="fileUpload">
                                                    <input type="file" id="input-file" className="file-upload" multiple accept="image/*" onChange={onImageChange} />
                                                    <label>
                                                        {imageURLS.length == 0 && (<>Upload <small>only jpg, png, mp4, mpeg with max size of 25 MB</small></>)}
                                                        {imageURLS.map((imageSrc) => (
                                                            <img src={imageSrc} alt="not fount" width={"250px"} />
                                                        ))}
                                                     </label>
                                                </div>
                                                <div className="botmActn text-end">
                                                    <button type="submit" className="btn btn-primary righticon">Submit</button>
                                                </div>
                                            </div>
                                        </div>
                                    </form>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                {/* Main Section */}

            </Authenticated>
        </>
    );
}
