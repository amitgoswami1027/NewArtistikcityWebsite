import React from "react";
import {Head, Link, useForm, usePage} from '@inertiajs/inertia-react';
import Guest from "@/Layouts/Guest";
import Header from "@/Components/Header";
import Footer from "@/Components/Footer";
import Breadcrumb from "@/Components/Breadcrumb";

export default function Checkout(){
    const { auth } = usePage().props;
    console.log(auth);
    const breadcrumbs = [
        { path: 'welcome', breadcrumb: 'Home' },
        { path: '', breadcrumb: 'Checkout'},
    ];
    const [crumbs, setCrumbs] = useState(breadcrumbs);
    const selected = crumb => { console.log(crumb); }
    return (
        <>
            <Guest>
                <Head title="Checkout" />
                <Header />
                <Breadcrumb crumbs={crumbs} selected={selected} />
                <div className="checkoutHead">
                    <div className="container">
                        <h1>Checkout {auth.user.name}</h1>
                    </div>
                </div>

                <div className="container">
                    <div className="checkoutwrap">
                        <div className="checkoutsteps">
                            <div className="stepbox">
                                <div className="stephead">
                                    <div className="stepname"><img src="/assets/images/step-1.png" alt="" /> Login or Register to Continue</div>
                                </div>

                                <div className="stepcontent loginSc">
                                    <div className="formSc">
                                        <ul>
                                            <li>
                                                <input type="email" className="field" placeholder="Email Address" />
                                            </li>
                                            <li>
                                                <input type="password" className="field" placeholder="Password" />
                                            </li>
                                            <li className="full"><a href="#">Forgot Password ?</a></li>
                                            <li>
                                                <input type="submit" className="btn" value="Login" />
                                            </li>
                                            <li className="full">
                                                <p className="or">or</p>
                                            </li>
                                            <li><i><img src="assets/images/fb-icon.png" alt="" /></i>
                                                <input type="submit" className="lgnBtn fbBtn" value="Continue with facebook" />
                                            </li>
                                            <li><i><img src="assets/images/google-icon.png" alt="" /></i>
                                                <input type="submit" className="lgnBtn" value="Continue with Google" />
                                            </li>
                                            <li><i><img src="assets/images/apple-icon.png" alt="" /></i>
                                                <input type="submit" className="lgnBtn" value="Continue with Apple" />
                                            </li>
                                            <li>
                                                <h6>Don't have an Account? <a href="register.html">Register</a></h6>
                                            </li>
                                        </ul>
                                    </div>
                                </div>
                            </div>

                            <div className="stepbox">
                                <div className="stephead">
                                    <div className="stepname"><img src="assets/images/step-2.png" alt="" /> Billing Details</div>
                                </div>
                            </div>

                            <div className="stepbox">
                                <div className="stephead">
                                    <div className="stepname"><img src="assets/images/step-3.png" alt="" /> Payment Option </div>
                                </div>
                            </div>
                        </div>

                        <div className="checkoutsidebar">
                            <div className="widget">
                                <h3>You are Purchasing</h3>
                                <div className="pd30">
                                    <img src="assets/images/checkout-img1.jpg" alt=""/>
                                    <h4>Charcoal drawing secrets revealed</h4>
                                    <p>4 weeks / 10 hrs a week</p>
                                    <strong className="price">₹490.00</strong>
                                    <h6>(Including all taxes)</h6>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <Footer />
            </Guest>
        </>
    );
}
