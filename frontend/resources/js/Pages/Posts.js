import React, {useState} from "react";
import { Link, Head, usePage } from '@inertiajs/inertia-react';
import Breadcrumb from '@/Components/Breadcrumb';
import Footer from '@/Components/Footer';
import Header from '@/Components/Header';
import BlogSidebar from '@/Components/Blog/BlogSidebar';
import Pagination from '@/Components/Blog/Pagination';

export default function Posts() {
    const { posts, category_list, featured_post } = usePage().props;

    console.log(posts);

    let sidebarData = {
        categoryList: category_list,
        featuredPost: featured_post
    }
    const breadcrumbs = [
        { path: 'welcome', breadcrumb: 'Home' },
        { path: 'blog.posts', breadcrumb: 'Posts'},
    ];
    const [crumbs, setCrumbs] = useState(breadcrumbs);
    const selected = crumb => { console.log(crumb); }
    
    return (
        <>
            <Header />
            <Breadcrumb crumbs={crumbs} selected={selected} />

            <div className="inrbanrtxt" style={{ backgroundImage: `url(/assets/images/cart-banner.jpg)` }}>
                <div className="container">
                    <h1>ArtistikCity Blog</h1>
                </div>
            </div>

            <div className="blogpage">
                <div className="container">
                    <div className="blogleftbar">
                        <div className="blogarticles">
                            {posts.data.map(({ id, title, subtitle, slug, image, created_at, posted_by, categories}) => {
                                return (<article>
                                    <div className="postthumb">
                                        <a href={route('blog.post.details', {slug})}>
                                            <img src={(`/storage/uploads/blog/${image}`)} alt={(`${title}`)} />
                                        </a>
                                    </div>
                                    <div className="postText">
                                        <h3><a href={route('blog.post.details', {slug})}>{title}</a></h3>
                                        <p>{subtitle}</p>
                                        <div className="postmeta">
                                            <span className="date">{created_at}</span>
                                            <span className="divider">/</span>
                                            <span className="postcatgry">{categories[0].name}</span>
                                        </div>
                                        <div className="author">
                                            <a href="#">
                                                <img src={(`/storage/uploads/teachers/${posted_by[0].id}/${posted_by[0].profile_photo}`)} alt={(`${posted_by[0].name}`)} />
                                                 {posted_by[0].name}
                                            </a>
                                        </div>
                                    </div>
                                </article>);
                            })}
                        </div>
                        <Pagination links={posts.links} />
                        {/* <div className="pagination">
                            <ul>
                                <li className="prev"><a href="#"><i className="fa fa-angle-left"></i></a></li>
                                <li className="current"><a href="#">1</a></li>
                                <li><a href="#">2</a></li>
                                <li><a href="#">3</a></li>
                                <li className="next"><a href="#"><i className="fa fa-angle-right"></i></a></li>
                            </ul>
                        </div> */}
                    </div>

                    <BlogSidebar sidebarData={sidebarData} />
                </div>
            </div>
            <Footer />
        </>
    );
}
