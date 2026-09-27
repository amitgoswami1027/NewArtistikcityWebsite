import React from 'react';
import Input from '@/Components/Input';
import { Head, Link, useForm, usePage } from '@inertiajs/inertia-react';
import Button from '@/Components/Button';
import ValidationMessages from '@/Components/ValidationMessages';

export default function GetFreeCourses() {
    const { data, setData, post, processing, errors, reset } = useForm({
        name: '',
        email: '',
        free_course_type: 'color'
    });
    const { flash } = usePage().props
    const onHandleChange = (event) => {
        setData(event.target.name, event.target.value);
    };
    const submit = (e) => {
        e.preventDefault();

        post(route('getfreecourses'));
    };
    return (
        <>
            <div className='free-courese-form-home'>
                <p>Free Course</p>
                <form onSubmit={submit}>
                    <ul>
                        <li>
                            <img src="/assets/images/icons-new/user-form.png" alt="" className='form-image'/>
                            <Input
                                type="text"
                                name="name"
                                value={data.name}
                                className="field name-ak"
                                autoComplete="name"
                                placeholder="Your Name"
                                handleChange={onHandleChange}
                                isFocused={true}
                                required
                            />
                        </li>
                        <li>
                            <img src="/assets/images/icons-new/email-form.png" alt="" className='form-image'/>
                            <Input
                                type="text"
                                name="email"
                                value={data.email}
                                className="field email-ak"
                                autoComplete="name"
                                placeholder="Email Address"
                                handleChange={onHandleChange}
                                isFocused={true}
                                required
                            />
                        </li>
                        {/* <li>
                            <Input
                                type="radio"
                                name="free_course_type"
                                value="color"
                                className="field"
                                checked={data.free_course_type === "color"}
                                handleChange={onHandleChange}
                            />Color
                            <Input
                                type="radio"
                                name="free_course_type"
                                value="black-white"
                                className="field"
                                checked={data.free_course_type === "black-white"}
                                handleChange={onHandleChange}
                            />Black & White
                        </li> */}
                        <li><Button className="btn" processing={processing}>Get a Free Course</Button></li>
                    </ul>
                </form>
                <ValidationMessages errors={errors} />
                <div>
                    {flash.message && (
                        <div className="alert">{flash.message}</div>
                    )}
                </div>
            </div>
        </>
    );
}
