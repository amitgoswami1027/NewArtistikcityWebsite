import React, { useState, useRef } from 'react';
import Authenticated from '@/Layouts/Authenticated';
import { Head, usePage, useForm } from '@inertiajs/inertia-react';
import StudentHeader from "@/Components/Students/StudentHeader";
import StudentNavigation from "@/Components/Students/StudentNavigation";
import MyAccountNavigation from '@/Components/Students/MyAccountNavigation';
import { DefaultEditor } from 'react-simple-wysiwyg';

export default function MyAccount(props) {
    const profilePhoto = useRef(null);

    const { data, setData, post, progress, reset } = useForm({
        id: props.auth.user.id,
        name: props.auth.user.name,
        email: props.auth.user.email,
        phone: props.auth.user.phone,
        profile_title: props.auth.user.profile_title,
        profile_description: props.auth.user.profile_description,
        location: props.auth.user.location,
        profile_photo: props.auth.user.profile_photo,
        // notifications: props.auth.user.notifications,
        // language_preference: props.auth.user.language_preference,
        tmp_profile_photo: '',
    })
    const [html, setHtml] = React.useState(data.profile_description);
    function onChange(e) {
        setHtml(e.target.value);
    }
    function handleSubmit(e) {
        e.preventDefault()
        post(route('user.account.save'), {
            preserveScroll: true,
            onSuccess: () => reset(),
        })
    }

    const onButtonClick = () => {
        profilePhoto.current.click();
    };

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
                        <StudentNavigation />
                        <main className="col-md-10 px-0 ms-sm-auto rightsidebar">
                            <div className="accountPage p30">
                                <MyAccountNavigation auth={props.auth} />
                                <div className="accountDtl">
                                    <form onSubmit={handleSubmit}>
                                        <input type="hidden" id='id' name='id' value={data.id} onChange={e => setData('id', e.target.value)} />
                                        <div className="accountEdit">
                                            <h3>Basic Information</h3>
                                            <div className="accountForm">
                                                <div className="file-preview-thumbnails">
                                                    <div className="thumb-preview">
                                                        {data.profile_photo !== null && (<img className="avatar" src={(`/storage/uploads/students/${data.id}/${data.profile_photo}`)} alt={(`${data.name}`)} />)}
                                                        {data.profile_photo == null && (<img className="avatar" src="/assets/user/images/profile-thumb.png" alt="" />)}
                                                        <input type="file" id="tmp_profile_photo" name='tmp_profile_photo' ref={profilePhoto} style={{display: "none"}} onChange={e => setData('tmp_profile_photo', e.target.files[0])} />
                                                        <div className="btn-file" onClick={onButtonClick}><img src="/assets/user/images/camera-icon.svg" alt="" /></div>
                                                    </div>
                                                </div>
                                                <div className="form-group"><label>Name</label><input type="text" className="form-control" id='name' value={data.name} onChange={e => setData('name', e.target.value)} placeholder="Name" /></div>
                                                <div className="form-group"><label>Email</label><input type="email" className="form-control" id='email' value={data.email} onChange={e => setData('email', e.target.value)} placeholder="Email" /></div>
                                                {/* <div className="form-group">
                                                    <label>Notification</label>
                                                    <select name='notifications' id='notifications' className='form-control'>
                                                        <option value="1">Yes</option>
                                                        <option value="0">No</option>
                                                    </select>
                                                </div>
                                                <div className="form-group">
                                                    <label>Language Preference</label>
                                                    <select name='language_preference' id='language_preference' className='form-control'>
                                                            <option value="English">English</option>
                                                    </select>
                                                </div> */}
                                                <div className="form-group">
                                                    <label>Phone</label>
                                                    <div className="d-flex">
                                                    <div className="phone">
                                                        <span className="india_flag"><img src="/assets/user/images/india-flag.png" alt="" /> <i className="arrow-down"></i></span>
                                                        <input type="text" className="form-control" id='phone' value={data.phone} onChange={e => setData('phone', e.target.value)} placeholder="Phone" />
                                                    </div>
                                                    <div className="verfied_phone"><span className="btnb"><img src="/assets/user/images/verefied-icon.svg" alt="" /> VERIFIED</span></div>
                                                    </div>
                                                </div>
                                                <div className="form-group"><label>Profile Title</label><input type="text" className="form-control" id='profile_title' value={data.profile_title} onChange={e => setData('profile_title', e.target.value)} placeholder="Profile Title" /></div>
                                                <div className="form-group"><label>Profile Description</label>
                                                    <DefaultEditor value={html} id='profile_description' onChange={onChange} />
                                                    {/*<textarea className="form-control" id='profile_description' value={data.profile_description} onChange={e => setData('profile_description', e.target.value)} placeholder="Profile Description"></textarea>*/}
                                                </div>
                                                <div className="form-group"><label>Location</label><input type="text" className="form-control" id='location' value={data.location} onChange={e => setData('location', e.target.value)} placeholder="Location" /></div>
                                                <div className="note">
                                                    {/* <p>Mobile phone number is verified. Visit <a href="#">notifications</a> to disable SMS notifications for this number.</p> */}
                                                </div>
                                            </div>
                                        </div>

                                        <div className="accountActn">
                                            <button className="btn btn-secondary">Cancel</button>
                                            <button type='submit' className="btn btn-primary">Save</button>
                                        </div>
                                    </form>
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
