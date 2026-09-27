import { usePage } from "@inertiajs/inertia-react";
import React, {useState} from "react";
import Breadcrumb from '@/Components/Breadcrumb';
import Footer from '@/Components/Footer';
import Header from '@/Components/Header';
import Newsletter from "@/Components/Newsletter";

export default function Instructor() {
    const { instructor } = usePage().props;
    console.log(instructor);
    const breadcrumbs = [
        { path: 'welcome', breadcrumb: 'Home' },
        { path: 'instructor', breadcrumb: 'Instructor'},
    ];
    const [crumbs, setCrumbs] = useState(breadcrumbs);
    const selected = crumb => { console.log(crumb); }
    return (
        <>
            <Header />
            <Breadcrumb  crumbs={crumbs} selected={selected} />
            <div class="inrbanrtxt" style={{backgroundImage: `url(/assets/images/cart-banner.jpg)`}}>
                <div class="container">
                    <h1>Meet The Instructors</h1>
                </div>
            </div>

            <div class="container">
                <div class="instructorstpage">
                    <div class="fdrow">
                        <div class="poster">
                            <img src={(`/storage/uploads/teachers/${instructor.id}/${instructor.profile_photo}`)} alt={(`${instructor.name}`)} />
                        </div>
                        <div class="text">
                            <h3>{instructor.name}</h3>
                            <h6>{instructor.profile_title}</h6>
                            <h6>{instructor.designation} /  <a href={route('welcome')}>www.artistikcity.com</a></h6>
                            <p dangerouslySetInnerHTML={{__html: instructor.profile_description}}></p>
                            <div class="soclShare">
                                <a href="#" class="fa fa-facebook" target="_blank"></a>
                                <a href="#" class="fa fa-instagram" target="_blank"></a>
                                <a href="#" class="fa fa-twitter" target="_blank"></a>
                                <a href="#" class="fa fa-linkedin" target="_blank"></a>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <Newsletter />
            <Footer />
        </>
    );
}